package com.viomanager.ai.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.viomanager.ai.client.GlmClient;
import com.viomanager.ai.client.GlmSseParser;
import com.viomanager.ai.client.GlmStreamEvent;
import com.viomanager.ai.config.AiProperties;
import com.viomanager.ai.dto.ChatDTO;
import com.viomanager.ai.support.AiSystemPrompt;
import com.viomanager.common.enums.AiErrorCode;
import com.viomanager.common.exception.BizException;
import java.io.UncheckedIOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI01 聊天编排：SSE 事件序 meta → tool×N → delta×N → done｜error（接口清单 §5）。
 * 编排在独立线程池跑（SseEmitter 异步语义，不占容器请求线程），池大小对齐拍板的全局并发 5。
 * 客户端断开 → onError/onTimeout 回调关上游流，生成立即停止（PRD 明确语义）。
 * HTTP 层错误（9100 未启用）在 controller 同步抛出，不进流。
 */
@Slf4j
@Service
public class AiChatService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** keep-alive 注释行间隔（清单 §5：每 15s 防代理断连） */
    private static final long KEEP_ALIVE_SECONDS = 15;

    /** 生成总超时：超时后 emitter 自动 complete，已生成部分按中断处理 */
    private static final long CHAT_TIMEOUT_MILLIS = 180_000L;

    /** 全服务共享一个 keep-alive 调度线程（daemon 不挡停机） */
    private static final ScheduledExecutorService KEEP_ALIVE =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ai-sse-keepalive");
                t.setDaemon(true);
                return t;
            });

    private final ObjectProvider<GlmClient> clientProvider;

    private final AiProperties properties;

    private final ExecutorService chatExecutor;

    public AiChatService(ObjectProvider<GlmClient> clientProvider, AiProperties properties) {
        this.clientProvider = clientProvider;
        this.properties = properties;
        //池大小 = 全局并发上限（拍板 5）：排队在池队列，超出并发的请求等前面的完成
        this.chatExecutor = Executors.newFixedThreadPool(properties.getMaxConcurrent(), r -> {
            Thread t = new Thread(r, "ai-chat-worker");
            t.setDaemon(true);
            return t;
        });
    }

    /** AI01 入口：装配校验在 controller 层；此处起异步编排，立即回 emitter */
    public SseEmitter chat(ChatDTO dto) {
        GlmClient client = clientProvider.getIfAvailable();
        if (client == null) {
            throw new BizException(AiErrorCode.AI_NOT_ENABLED);
        }
        SseEmitter emitter = new SseEmitter(CHAT_TIMEOUT_MILLIS);
        chatExecutor.execute(() -> runChat(client, dto, emitter));
        return emitter;
    }

    private void runChat(GlmClient client, ChatDTO dto, SseEmitter emitter) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicReference<GlmClient.StreamHandle> handleRef = new AtomicReference<>();
        Runnable stopUpstream = () -> {
            cancelled.set(true);
            GlmClient.StreamHandle handle = handleRef.get();
            if (handle != null) {
                handle.cancel();
            }
        };
        emitter.onError(e -> stopUpstream.run());
        emitter.onTimeout(stopUpstream::run);
        emitter.onCompletion(() -> cancelled.set(true));
        ScheduledFuture<?> keepAlive = KEEP_ALIVE.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (Exception e) {
                stopUpstream.run();
            }
        }, KEEP_ALIVE_SECONDS, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS);
        try {
            long messageId = IdWorker.getId();
            ObjectNode meta = MAPPER.createObjectNode();
            meta.put("messageId", String.valueOf(messageId));
            meta.put("conversationId", dto.getConversationId() == null
                    ? null : String.valueOf(dto.getConversationId()));
            sendEvent(emitter, "meta", meta);

            GlmSseParser parser = new GlmSseParser();
            GlmClient.StreamHandle handle = client.prepareStream(
                    buildRequestBody(client, dto.getContent()),
                    line -> consumeLine(emitter, parser, line));
            handleRef.set(handle);
            handle.start();
            emitter.complete();
        } catch (Exception e) {
            if (cancelled.get()) {
                //客户端已断开/超时：静默收尾（done 没人收，不发）
                emitter.complete();
                return;
            }
            log.warn("AI chat 流式生成失败：{}", e.getMessage());
            ObjectNode error = MAPPER.createObjectNode();
            error.put("code", e instanceof BizException biz ? biz.getCode() : AiErrorCode.AI_STREAM_ERROR.getCode());
            error.put("message", e instanceof BizException biz ? biz.getMessage()
                    : AiErrorCode.AI_STREAM_ERROR.getMessage());
            try {
                sendEvent(emitter, "error", error);
            } catch (Exception ignored) {
                //连 error 事件都发不出去 = 客户端已断开
            }
            emitter.complete();
        } finally {
            keepAlive.cancel(false);
        }
    }

    /** 上游每一行的处理：文本增量发 delta，[DONE] 发 done（finishReason 映射见 §5） */
    private void consumeLine(SseEmitter emitter, GlmSseParser parser, String line) {
        GlmStreamEvent event = parser.accept(line);
        if (event == null) {
            return;
        }
        if (event.text() != null) {
            ObjectNode delta = MAPPER.createObjectNode();
            delta.put("text", event.text());
            sendEvent(emitter, "delta", delta);
        }
        if (event.end()) {
            String finish = "length".equals(parser.getFinishReason()) ? "interrupted" : "stop";
            ObjectNode done = MAPPER.createObjectNode();
            done.put("finishReason", finish);
            ObjectNode usage = done.putObject("usage");
            usage.put("prompt", parser.getPromptTokens());
            usage.put("completion", parser.getCompletionTokens());
            sendEvent(emitter, "done", done);
        }
    }

    /** OpenAI 兼容请求体：系统提示词服务端固定；max_tokens 拍板 2048；带 include_usage 拿统计 */
    private String buildRequestBody(GlmClient client, String content) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", client.getModel());
        com.fasterxml.jackson.databind.node.ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", AiSystemPrompt.TEXT);
        messages.addObject().put("role", "user").put("content", content);
        root.put("stream", true);
        root.put("max_tokens", properties.getMaxCompletionTokens());
        root.putObject("stream_options").put("include_usage", true);
        return root.toString();
    }

    private void sendEvent(SseEmitter emitter, String name, ObjectNode data) {
        try {
            emitter.send(SseEmitter.event().name(name)
                    .data(data.toString(), MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            //SseEmitter 对已断开连接抛 IOException/IllegalStateException——上抛给编排层统一按断开处理
            throw new UncheckedIOException(new java.io.IOException(e.getMessage()));
        }
    }
}
