package com.viomanager.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.ai.dto.ChatDTO;
import com.viomanager.ai.service.AiChatService;
import com.viomanager.ai.service.AiStatusService;
import com.viomanager.common.enums.AiErrorCode;
import com.viomanager.common.result.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 助手聊天核心接口（接口清单 §3）。
 * HTTP 层错误（未登录 1002 / 未启用 9100）不走 SSE，直接标准 Result JSON（清单 §5）：
 * ⚠️ 9100 不能走 BizException——produces=text/event-stream 会把异常响应的媒体类型锁死，
 * 全局处理器写不出 JSON（协商 406/500）。校验失败时手写 response 绕开 MVC 协商，行为确定。
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
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatDTO dto, HttpServletResponse response) throws IOException {
        if (!aiStatusService.isEnabled()) {
            writeJson(response, Result.fail(AiErrorCode.AI_NOT_ENABLED));
            return null;
        }
        return aiChatService.chat(dto);
    }

    /** 手写标准 Result JSON（响应未交给 SSE 语义前的 HTTP 层错误出口） */
    private void writeJson(HttpServletResponse response, Result<Void> result) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(MAPPER.writeValueAsString(result));
        response.flushBuffer();
    }
}
