package com.viomanager.ai.controller;

import com.viomanager.ai.service.AiChatService;
import com.viomanager.common.result.Result;

/**
 * AI chat 预检拒绝异常（9100/9101/9102）：由 AiController 的局部 @ExceptionHandler 接住，
 * 以标准 Result JSON + 可选 Retry-After 头返回（不走 SSE）。
 * 刻意走异常而非手写 response：让 @AuditLog 切面记 success=false（清单 §6 成功失败都记）。
 */
public class AiChatRejectException extends RuntimeException {

    private final Result<Void> reject;

    private final Long retryAfterSeconds;

    public AiChatRejectException(Result<Void> reject, Long retryAfterSeconds) {
        super("AI chat 预检拒绝：" + reject.getMessage());
        this.reject = reject;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Result<Void> getReject() {
        return reject;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
