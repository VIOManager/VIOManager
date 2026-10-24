package com.viomanager.ai.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 助手配置项（application.yml 的 vio.ai 段，接口清单 §7 装配与限流）。
 * 三件套 GLM_BASE_URL / GLM_MODEL / GLM_API_KEY 全部环境变量承载：
 * 云端 = 智谱 open.bigmodel.cn；本地 = 自部署 OpenAI 兼容推理（如 Ollama 的 http://localhost:11434/v1），
 * 切换只动配置不动代码（2026-10-24 拍板"本地云端双兼容"）。
 * ⚠️ 红线：api-key 绝不进代码、不进库、不进日志——toString 已排除该字段。
 */
@Data
@ToString(exclude = "apiKey")
@Component
@ConfigurationProperties(prefix = "vio.ai")
public class AiProperties {

    /** OpenAI 兼容口根地址（客户端拼 /chat/completions）；默认智谱云端 */
    private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";

    /** 模型名；默认智谱免费档，部署时 GLM_MODEL 覆盖 */
    private String model = "glm-4-flash";

    /** API 密钥；空 = 零装配零 Bean（RocketMQ 同款口径），AI02 探针回 enabled:false */
    private String apiKey = "";

    /** 按人限流：每分钟次数（2026-10-24 拍板 6） */
    private int rateLimitPerMinute = 6;

    /** 全局并发上限：同时在生成的请求数（2026-10-24 拍板 5） */
    private int maxConcurrent = 5;

    /** 单次回复 token 上限，超限截断记 interrupted（2026-10-24 拍板 2048） */
    private int maxCompletionTokens = 2048;

    /** 装配条件：key 非空才建 GlmClient Bean */
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
