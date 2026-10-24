package com.viomanager.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.ai.client.GlmClient;
import com.viomanager.ai.config.AiProperties;
import com.viomanager.ai.dto.ChatDTO;
import com.viomanager.ai.entity.AiConversation;
import com.viomanager.ai.entity.AiMessage;
import com.viomanager.ai.limit.AiRateLimiter;
import com.viomanager.ai.mapper.AiConversationMapper;
import com.viomanager.ai.mapper.AiMessageMapper;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.AiErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
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
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI01 聊天编排：emitter 生命周期 + 预检 + 线程池 + 落库；多轮生成细节在 AiChatFlow。
 * 事件序 meta → tool×N → delta×N → done｜error（接口清单 §5）；
 * 两种模式：conversationId=null 纯流式不落库；带会话则历史拼接上下文 + 流结束统一落库（AiChatRecorder）。
 * 客户端断开 → onError/onTimeout 关上游流立即停生成，已生成部分落库 incomplete=1。
 */
@Slf4j
@Service
public class AiChatService {

    /** keep-alive 注释行间隔（清单 §5：每 15s 防代理断连） */
    private static final long KEEP_ALIVE_SECONDS = 15;

    /** 生成总超时：超时后 emitter 自动 complete，已生成部分按中断处理 */
    private static final long CHAT_TIMEOUT_MILLIS = 180_000L;

    /** 并发满的 Retry-After 建议秒数（比按人限流短：槽位几秒内就有人让出） */
    private static final long CONGESTION_RETRY_AFTER_SECONDS = 2L;

    /** 送入模型的历史条数上限（user+assistant 各算一条）：防上下文无限膨胀，取最近 N 条 */
    private static final int HISTORY_LIMIT = 20;

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

    private final AiRateLimiter rateLimiter;

    private final AiChatFlow flow;

    private final ExecutorService chatExecutor;

    public AiChatService(ObjectProvider<GlmClient> clientProvider, AiProperties properties,
                         AiConversationMapper conversationMapper, AiMessageMapper messageMapper,
                         AiChatRecorder recorder, AiRateLimiter rateLimiter, AiChatFlow flow) {
        this.clientProvider = clientProvider;
        this.properties = properties;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.recorder = recorder;
        this.rateLimiter = rateLimiter;
        this.flow = flow;
        //池大小 = 全局并发上限（拍板 5）：并发闸门在 preflight 的信号量，池只做执行器
        this.chatExecutor = Executors.newFixedThreadPool(properties.getMaxConcurrent(), r -> {
            Thread t = new Thread(r, "ai-chat-worker");
            t.setDaemon(true);
            return t;
        });
    }

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

    /**
     * 同步预检（controller 在回 SSE 前调用）：未装配 9100 / 他人会话 9101 /
     * 按人限流 9102（Retry-After 精确秒）/ 全局并发满 9102（短 Retry-After）。
     * 顺序刻意：装配与归属校验不消耗限流配额，都过了才扣次数与并发槽。
     */
    public ChatPrecheck preflight(ChatDTO dto) {
        if (clientProvider.getIfAvailable() == null) {
            return ChatPrecheck.reject(Result.fail(AiErrorCode.AI_NOT_ENABLED), null);
        }
        CurrentUser user = UserHolder.getUser();
        if (dto.getConversationId() != null) {
            boolean owned = conversationMapper.selectCount(new QueryWrapper<AiConversation>()
                    .eq("id", dto.getConversationId()).eq("user_id", user.getUserId())) > 0;
            if (!owned) {
                return ChatPrecheck.reject(Result.fail(AiErrorCode.AI_CONVERSATION_NOT_FOUND), null);
            }
        }
        if (!rateLimiter.tryAcquire(user.getUserId())) {
            return ChatPrecheck.reject(Result.fail(AiErrorCode.AI_RATE_LIMITED),
                    rateLimiter.retryAfterSeconds(user.getUserId()));
        }
        if (!rateLimiter.tryAcquireGlobalSlot()) {
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
        //身份必须请求线程捕获：UserHolder 是 ThreadLocal，异步线程取不到（工具执行时再临时挂回）
        CurrentUser user = UserHolder.getUser();
        SseEmitter emitter = new SseEmitter(CHAT_TIMEOUT_MILLIS);
        chatExecutor.execute(() -> {
            try {
                runChat(client, dto, user, emitter);
            } finally {
                rateLimiter.releaseGlobalSlot();
            }
        });
        return emitter;
    }

    private void runChat(GlmClient client, ChatDTO dto, CurrentUser user, SseEmitter emitter) {
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
        long startedAt = System.currentTimeMillis();
        try {
            AiConversation conversation = dto.getConversationId() == null
                    ? null : loadOwnedConversation(dto.getConversationId(), user.getUserId());
            emitMeta(emitter, dto);
            AiChatFlow.FlowResult result = flow.run(client, dto,
                    conversation == null ? List.of() : loadHistory(conversation.getId()),
                    user, emitter, cancelled);
            //中断三源：客户端断开 / length 截断 / 流中途异常——有产出都照常落库
            boolean interrupted = cancelled.get() || "length".equals(result.finishReason())
                    || result.error() != null;
            recordIfConversation(dto, conversation, result, interrupted, startedAt);
            if (result.error() != null && !cancelled.get()) {
                log.warn("AI chat 流式生成失败：{}", result.error().getMessage());
                emitError(emitter, result.error());
            }
            emitter.complete();
        } catch (Exception e) {
            //Flow 之外的异常（meta/会话加载阶段）：无产出可落，只保证收尾不悬挂
            try {
                if (!cancelled.get()) {
                    log.warn("AI chat 编排失败：{}", e.getMessage());
                    emitError(emitter, e);
                }
            } catch (Exception secondary) {
                log.error("AI chat 异常收尾二次失败：{}", secondary.getMessage());
            }
            emitter.complete();
        } finally {
            keepAlive.cancel(false);
        }
    }

    private void emitMeta(SseEmitter emitter, ChatDTO dto) {
        com.fasterxml.jackson.databind.node.ObjectNode meta =
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        meta.put("messageId", String.valueOf(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId()));
        meta.put("conversationId", dto.getConversationId() == null
                ? null : String.valueOf(dto.getConversationId()));
        try {
            emitter.send(SseEmitter.event().name("meta")
                    .data(meta.toString(), org.springframework.http.MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void emitError(SseEmitter emitter, Exception e) {
        com.fasterxml.jackson.databind.node.ObjectNode error =
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        error.put("code", e instanceof BizException biz ? biz.getCode() : AiErrorCode.AI_STREAM_ERROR.getCode());
        error.put("message", e instanceof BizException biz ? biz.getMessage()
                : AiErrorCode.AI_STREAM_ERROR.getMessage());
        try {
            emitter.send(SseEmitter.event().name("error")
                    .data(error.toString(), org.springframework.http.MediaType.APPLICATION_JSON));
        } catch (Exception ignored) {
            //连 error 事件都发不出去 = 客户端已断开
        }
    }

    /** 无会话模式直接跳过；reply 为空（无产出）也不落——库里不留"问了没答"的孤儿提问 */
    private void recordIfConversation(ChatDTO dto, AiConversation conversation,
                                      AiChatFlow.FlowResult result, boolean interrupted, long startedAt) {
        if (conversation == null || result.reply().isEmpty()) {
            return;
        }
        recorder.record(conversation, dto.getContent(), result.reply(), interrupted ? 1 : 0,
                result.promptTokens(), result.completionTokens(),
                System.currentTimeMillis() - startedAt);
    }

    private AiConversation loadOwnedConversation(Long conversationId, Long userId) {
        AiConversation conversation = conversationMapper.selectOne(new QueryWrapper<AiConversation>()
                .eq("id", conversationId).eq("user_id", userId));
        if (conversation == null) {
            //预检与执行之间会话被删：按 9101 收流（边缘 case，简单处理）
            throw new BizException(AiErrorCode.AI_CONVERSATION_NOT_FOUND);
        }
        return conversation;
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
}
