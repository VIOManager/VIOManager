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

    /** 云端智谱默认口（compose 透传空串时的回退，见 getBaseUrl 注释） */
    static final String DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/paas/v4";

    static final String DEFAULT_MODEL = "glm-4-flash";

    /** OpenAI 兼容口根地址（客户端拼 /chat/completions）；默认智谱云端 */
    private String baseUrl = DEFAULT_BASE_URL;

    /** 模型名；默认智谱免费档，部署时 GLM_MODEL 覆盖 */
    private String model = DEFAULT_MODEL;

    /** API 密钥；空 = 零装配零 Bean（RocketMQ 同款口径），AI02 探针回 enabled:false */
    private String apiKey = "";

    /** 按人限流：每分钟次数（2026-10-24 拍板 6） */
    private int rateLimitPerMinute = 6;

    /** 全局并发上限：同时在生成的请求数（2026-10-24 拍板 5） */
    private int maxConcurrent = 5;

    /** 单次回复 token 上限，超限截断记 interrupted（2026-10-24 拍板 2048） */
    private int maxCompletionTokens = 2048;

    /**
     * 采样温度（yml 的 vio.ai.temperature 可调）。2026-10-25 实测教训：0.3 低温把
     * qwen2.5:7b 的坏模式焊死了（同题 6/6 反问或文本伪调用，含编造结果）；
     * 默认 0.7 下工具调用按训练分布命中约四成——查询助手调温前先小样本实测。
     */
    private double temperature = 0.7;

    /** 装配条件：key 非空才建 GlmClient Bean */
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 空串回退默认：compose 的 GLM_BASE_URL:- 透传在宿主未配置时给容器的是空字符串而非 unset，
     * Spring 的 ${GLM_BASE_URL:默认} 只在变量不存在时生效——空串会穿透打坏请求地址。
     */
    public String getBaseUrl() {
        return baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl;
    }

    /** 同 getBaseUrl：GLM_MODEL 空串回退默认模型 */
    public String getModel() {
        return model == null || model.isBlank() ? DEFAULT_MODEL : model;
    }
}
