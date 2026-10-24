package com.viomanager.ai.service;

import com.viomanager.ai.client.GlmClient;
import com.viomanager.ai.config.AiProperties;
import com.viomanager.ai.limit.AiRateLimiter;
import com.viomanager.common.auth.UserHolder;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * AI 探针（AI02）：前端灰化入口的数据源（接口清单 §3）。
 * 口径：未装配也回 code 0 + enabled:false——不报错刷屏，前端据此显示说明横幅。
 * enabled = 装配 + 开关双条件：首版开关即装配（AI08/09 管理界面留位不实做，拍板见清单 §11），
 * 后续入口开关落地时在此扩展。
 */
@Service
public class AiStatusService {

    private final ObjectProvider<GlmClient> clientProvider;

    private final AiProperties properties;

    private final AiRateLimiter rateLimiter;

    public AiStatusService(ObjectProvider<GlmClient> clientProvider,
                           AiProperties properties, AiRateLimiter rateLimiter) {
        this.clientProvider = clientProvider;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    /**
     * 探针出参：enabled / model / rateLimitRemaining（当前登录人当前窗口剩余次数）。
     * 未装配时 model 仍回配置值（默认 glm-4-flash）、余量回满额——字段恒在，前端不用判空。
     */
    public Map<String, Object> status() {
        boolean enabled = clientProvider.getIfAvailable() != null;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", enabled);
        data.put("model", properties.getModel());
        data.put("rateLimitRemaining", rateLimiter.remaining(UserHolder.getUser().getUserId()));
        return data;
    }

    /** 装配态判断（chat 入口 9100 用）：client Bean 存在 = 已启用 */
    public boolean isEnabled() {
        return clientProvider.getIfAvailable() != null;
    }
}
