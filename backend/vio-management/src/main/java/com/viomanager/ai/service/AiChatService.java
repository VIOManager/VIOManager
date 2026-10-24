package com.viomanager.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.viomanager.ai.client.GlmClient;
import com.viomanager.ai.client.GlmSseParser;
import com.viomanager.ai.client.GlmStreamEvent;
import com.viomanager.ai.config.AiProperties;
import com.viomanager.ai.dto.ChatDTO;
import com.viomanager.ai.entity.AiConversation;
import com.viomanager.ai.entity.AiMessage;
import com.viomanager.ai.mapper.AiConversationMapper;
import com.viomanager.ai.mapper.AiMessageMapper;
import com.viomanager.ai.support.AiSystemPrompt;
import com.viomanager.common.enums.AiErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import java.io.UncheckedIOException;
import java.util.List;
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
 * 两种模式：conversationId=null 纯流式不落库；带会话则历史拼接上下文 + 流结束统一落库（AiChatRecorder）。
 * 编排在独立线程池跑（SseEmitter 异步语义），池大小对齐拍板的全局并发 5；
 * 客户端断开 → onError/onTimeout 关上游流立即停生成，已生成部分落库 incomplete=1。
 */
@Slf4j
@Service
public class AiChatService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** keep-alive 注释行间隔（清单 §5：每 15s 防代理断连） */
    private static final long KEEP_ALIVE_SECONDS = 15;

    /** 生成总超时：超时后 emitter 自动 complete，已生成部分按中断处理 */
    private static final long CHAT_TIMEOUT_MILLIS = 180_000L;

    /** 并发满的 Retry-After 建议秒数（比按人限流短：槽位几秒内就有人让出） */
    private static final long CONGESTION_RETRY_AFTER_SECONDS = 2L;

    /** 送入模型的历史条数上限（user+assistant 各算一条）：防上下文无限膨胀，取最近 N 条 */
    private static final int HISTORY_LIMIT = 20;

    /**
     * 预检结论：reject 为 null = 通过；retryAfterSeconds 非 null 时 controller 设 Retry-After 头。
     */
    public record ChatPrecheck(Result<Void> reject, Long retryAfterSeconds) {
        static ChatPrecheck pass() {
            return new ChatPrecheck(null, null);
        }

        static ChatPrecheck reject(Result<Void> reject, Long retryAfterSeconds) {
            return new ChatPrecheck(reject, retryAfterSeconds);
        }
    }

    /** 全服务共享一个 keep-alive 调度线程（daemon 不挡停机） */
    private static final ScheduledExecutorService KEEP_ALIVE =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ai-sse-keepalive");
                t.setDaemon(true);
                return t;
            });

    private final ObjectProvider<GlmClient> clientProvider;

    private final AiProperties properties;

    private final AiConversationMapper conversationMapper;

    private final AiMessageMapper messageMapper;

    private final AiChatRecorder recorder;

    private final com.viomanager.ai.limit.AiRateLimiter rateLimiter;

    private final ExecutorService chatExecutor;

    public AiChatService(ObjectProvider<GlmClient> clientProvider, AiProperties properties,
                         AiConversationMapper conversationMapper, AiMessageMapper messageMapper,
                         AiChatRecorder recorder, com.viomanager.ai.limit.AiRateLimiter rateLimiter) {
        this.clientProvider = clientProvider;
        this.properties = properties;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.recorder = recorder;
        this.rateLimiter = rateLimiter;
        //池大小 = 全局并发上限（拍板 5）：排队在池队列，超出并发的请求等前面的完成
        this.chatExecutor = Executors.newFixedThreadPool(properties.getMaxConcurrent(), r -> {
            Thread t = new Thread(r, "ai-chat-worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 同步预检（controller 在回 SSE 前调用）：未装配 9100 / 他人会话 9101 /
     * 按人限流 9102（Retry-After 头秒数见 retryAfterSeconds）/ 全局并发满 9102（短 Retry-After）。
     * 返回 null = 通过；非 null = HTTP 层错误，由 controller 手写标准 Result JSON。
     * 顺序刻意：装配与归属校验不消耗限流配额，都过了才扣次数与并发槽。
     */
    public ChatPrecheck preflight(ChatDTO dto) {
        if (clientProvider.getIfAvailable() == null) {
            return ChatPrecheck.reject(Result.fail(AiErrorCode.AI_NOT_ENABLED), null);
        }
        Long userId = com.viomanager.common.auth.UserHolder.getUser().getUserId();
        if (dto.getConversationId() != null) {
            boolean owned = conversationMapper.selectCount(new QueryWrapper<AiConversation>()
                    .eq("id", dto.getConversationId()).eq("user_id", userId)) > 0;
            if (!owned) {
                return ChatPrecheck.reject(Result.fail(AiErrorCode.AI_CONVERSATION_NOT_FOUND), null);
            }
        }
        if (!rateLimiter.tryAcquire(userId)) {
            return ChatPrecheck.reject(Result.fail(AiErrorCode.AI_RATE_LIMITED),
                    rateLimiter.retryAfterSeconds(userId));
        }
        if (!rateLimiter.tryAcquireGlobalSlot()) {
            //并发槽已拿但本轮不再生成：立即还槽，只扣一次每分钟配额（用户重试时再按新请求计）
            rateLimiter.releaseGlobalSlot();
            return ChatPrecheck.reject(Result.fail(AiErrorCode.AI_RATE_LIMITED,
                    "当前使用人数较多，请稍后再试"), CONGESTION_RETRY_AFTER_SECONDS);
        }
        return ChatPrecheck.pass();
    }

    /** AI01 入口：预检已过（并发槽已持有），起异步编排，立即回 emitter；槽在生成结束后释放 */
    public SseEmitter chat(ChatDTO dto) {
        GlmClient client = clientProvider.getIfAvailable();
        if (client == null) {
            rateLimiter.releaseGlobalSlot();
            throw new BizException(AiErrorCode.AI_NOT_ENABLED);
        }
        //userId 必须在请求线程捕获：UserHolder 是 ThreadLocal，异步线程里取不到
        Long userId = com.viomanager.common.auth.UserHolder.getUser().getUserId();
        SseEmitter emitter = new SseEmitter(CHAT_TIMEOUT_MILLIS);
        chatExecutor.execute(() -> {
            try {
                runChat(client, dto, userId, emitter);
            } finally {
                rateLimiter.releaseGlobalSlot();
            }
        });
        return emitter;
    }

    private void runChat(GlmClient client, ChatDTO dto, Long userId, SseEmitter emitter) {
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
        StringBuilder reply = new StringBuilder();
        GlmSseParser parser = new GlmSseParser();
        long startedAt = System.currentTimeMillis();
        try {
            AiConversation conversation = dto.getConversationId() == null
                    ? null : loadOwnedConversation(dto.getConversationId(), userId);
            long messageId = IdWorker.getId();
            ObjectNode meta = MAPPER.createObjectNode();
            meta.put("messageId", String.valueOf(messageId));
            meta.put("conversationId", dto.getConversationId() == null
                    ? null : String.valueOf(dto.getConversationId()));
            sendEvent(emitter, "meta", meta);

            GlmClient.StreamHandle handle = client.prepareStream(
                    buildRequestBody(client, dto.getContent(),
                            conversation == null ? List.of() : loadHistory(conversation.getId())),
                    line -> consumeLine(emitter, parser, reply, line));
            handleRef.set(handle);
            handle.start();
            //流正常读完：落库后收流（done 事件在 consumeLine 里已发）
            recordIfConversation(dto, conversation, reply.toString(),
                    isInterrupted(parser, cancelled) ? 1 : 0, parser, startedAt);
            emitter.complete();
        } catch (Exception e) {
            //异常路径的收尾绝不许再抛——任何逃逸都会让 emitter 悬挂到超时
            try {
                if (cancelled.get()) {
                    //客户端断开/超时：已生成部分照常落库（incomplete=1），静默收尾
                    recordIfConversation(dto, resolveOwnedOrNull(dto.getConversationId(), userId),
                            reply.toString(), 1, parser, startedAt);
                } else {
                    log.warn("AI chat 流式生成失败：{}", e.getMessage());
                    recordIfConversation(dto, resolveOwnedOrNull(dto.getConversationId(), userId),
                            reply.toString(), 1, parser, startedAt);
                    ObjectNode error = MAPPER.createObjectNode();
                    error.put("code", e instanceof BizException biz ? biz.getCode()
                            : AiErrorCode.AI_STREAM_ERROR.getCode());
                    error.put("message", e instanceof BizException biz ? biz.getMessage()
                            : AiErrorCode.AI_STREAM_ERROR.getMessage());
                    try {
                        sendEvent(emitter, "error", error);
                    } catch (Exception ignored) {
                        //连 error 事件都发不出去 = 客户端已断开
                    }
                }
            } catch (Exception secondary) {
                log.error("AI chat 异常收尾二次失败：{}", secondary.getMessage());
            }
            emitter.complete();
        } finally {
            keepAlive.cancel(false);
        }
    }

    /** 上游每一行的处理：文本增量发 delta，[DONE] 发 done（length → interrupted 映射见 §5） */
    private void consumeLine(SseEmitter emitter, GlmSseParser parser, StringBuilder reply, String line) {
        GlmStreamEvent event = parser.accept(line);
        if (event == null) {
            return;
        }
        if (event.text() != null) {
            reply.append(event.text());
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

    /** 无会话模式直接跳过；reply 为空（无产出）也不落——库里不留"问了没答"的孤儿提问 */
    private void recordIfConversation(ChatDTO dto, AiConversation conversation, String replyText,
                                      int incomplete, GlmSseParser parser, long startedAt) {
        if (conversation == null || replyText.isEmpty()) {
            return;
        }
        recorder.record(conversation, dto.getContent(), replyText, incomplete,
                parser.getPromptTokens(), parser.getCompletionTokens(),
                System.currentTimeMillis() - startedAt);
    }

    private AiConversation loadOwnedConversation(Long conversationId, Long userId) {
        AiConversation conversation = resolveOwnedOrNull(conversationId, userId);
        if (conversation == null) {
            //预检与执行之间会话被删：按 9101 收流（边缘 case，简单处理）
            throw new BizException(AiErrorCode.AI_CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }

    /** 异步线程里 UserHolder 不可用，userId 由请求线程捕获传入 */
    private AiConversation resolveOwnedOrNull(Long conversationId, Long userId) {
        if (conversationId == null) {
            return null;
        }
        return conversationMapper.selectOne(new QueryWrapper<AiConversation>()
                .eq("id", conversationId).eq("user_id", userId));
    }

    /** 历史上下文：最近 N 条完整对话（incomplete=1 的断头回复不送，断内容会误导模型） */
    private List<AiMessage> loadHistory(Long conversationId) {
        List<AiMessage> latest = messageMapper.selectList(new QueryWrapper<AiMessage>()
                .eq("conversation_id", conversationId)
                .in("role", "user", "assistant")
                .ne("incomplete", 1)
                .orderByDesc("id")
                .last("limit " + HISTORY_LIMIT));
        return latest.reversed();
    }

    /** 中断判定：客户端断开/超时，或上游 length 截断（超 2048 token） */
    private boolean isInterrupted(GlmSseParser parser, AtomicBoolean cancelled) {
        return cancelled.get() || "length".equals(parser.getFinishReason());
    }

    /** OpenAI 兼容请求体：系统提示词服务端固定；历史 + 本次提问；max_tokens 拍板 2048 */
    private String buildRequestBody(GlmClient client, String content, List<AiMessage> history) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", client.getModel());
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", AiSystemPrompt.TEXT);
        for (AiMessage message : history) {
            messages.addObject().put("role", message.getRole()).put("content", message.getContent());
        }
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
