package com.viomanager.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.ai.dto.ChatDTO;
import com.viomanager.ai.service.AiChatService;
import com.viomanager.ai.service.AiStatusService;
import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.result.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 助手聊天核心接口（接口清单 §3）。
 * HTTP 层错误（未登录 1002 / 未启用 9100 / 限流 9102）不走 SSE，直接标准 Result JSON（清单 §5）：
 * 预检拒绝走 AiChatRejectException → 本类局部 @ExceptionHandler 回 JSON（ResponseEntity 显式
 * contentType 绕过 produces=text/event-stream 的协商锁死）——走异常是为了让 @AuditLog
 * 切面把拒绝记成 success=false（清单 §6 成功失败都记）。
 * JWT 必须；三端共用不裁角色，数据范围在工具调用层按登录人裁剪。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private AiStatusService aiStatusService;

    @Resource
    private AiChatService aiChatService;

    /** AI02 探针：enabled / model / rateLimitRemaining；未装配也回 code 0 + enabled:false */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.ok(aiStatusService.status());
    }

    /**
     * AI01 发消息（SSE 流式）：事件序 meta → tool×N → delta×N → done｜error。
     * 不挂幂等键（清单 §3：生成型操作天然一次性，断流前端"重新生成"重发）。
     * 审计（清单 §6）：bizType=AI，摘要取首问前 50 字，受理成功失败都记。
     */
    @AuditLog(bizType = "AI", label = "AI对话",
            summary = "'AI对话：' + #dto.content.substring(0, "
                    + "T(java.lang.Math).min(50, #dto.content.length()))")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatDTO dto) {
        AiChatService.ChatPrecheck check = aiChatService.preflight(dto);
        if (check.reject() != null) {
            throw new AiChatRejectException(check.reject(), check.retryAfterSeconds());
        }
        return aiChatService.chat(dto);
    }

    /** 预检拒绝的 JSON 出口（本类局部处理器，优先于全局）；显式 contentType 绕协商 */
    @ExceptionHandler(AiChatRejectException.class)
    public ResponseEntity<String> onReject(AiChatRejectException e) throws Exception {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON);
        if (e.getRetryAfterSeconds() != null) {
            builder.header("Retry-After", String.valueOf(e.getRetryAfterSeconds()));
        }
        return builder.body(MAPPER.writeValueAsString(e.getReject()));
    }
}
