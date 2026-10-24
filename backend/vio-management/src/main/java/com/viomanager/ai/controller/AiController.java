package com.viomanager.ai.controller;

import com.viomanager.ai.service.AiStatusService;
import com.viomanager.common.result.Result;
import java.util.Map;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 助手聊天核心接口（接口清单 §3）。
 * AI02 探针挂这里；AI01 SSE 聊天随 Step3 落到同文件（单文件超 300 行前再拆）。
 * JWT 必须——status 的限流余量按登录人算，三端共用不裁角色。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Resource
    private AiStatusService aiStatusService;

    /** AI02 探针：enabled / model / rateLimitRemaining；未装配也回 code 0 + enabled:false */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.ok(aiStatusService.status());
    }
}
