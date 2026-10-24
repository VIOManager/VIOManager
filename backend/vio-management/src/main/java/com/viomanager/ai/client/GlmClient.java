package com.viomanager.ai.client;

import com.viomanager.ai.config.AiProperties;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * GLM 客户端：OpenAI 兼容口（云端智谱 / 本地 Ollama 同一口子，拍板见接口清单 §11）。
 * 传输用 JDK 21 自带 HttpClient——零新依赖（本机镜像源不稳，零下载是硬要求）；
 * 单例不可变，密钥只存在内存字段，任何日志与异常信息都不得输出它（红线）。
 * Step1 仅落装配骨架（构造 + 连接），SSE 流式与工具调用方法随聊天主干补齐。
 */
public class GlmClient {

    private final String baseUrl;

    private final String model;

    private final String apiKey;

    private final HttpClient http;

    public GlmClient(AiProperties properties) {
        this.baseUrl = stripTrailingSlash(properties.getBaseUrl());
        this.model = properties.getModel();
        this.apiKey = properties.getApiKey();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** OpenAI 兼容 chat/completions 完整地址 */
    String chatCompletionsUrl() {
        return baseUrl + "/chat/completions";
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String getModel() {
        return model;
    }

    String getApiKey() {
        return apiKey;
    }

    HttpClient getHttp() {
        return http;
    }
}
