package com.viomanager.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.ai.config.AiProperties;
import com.viomanager.common.enums.AiErrorCode;
import com.viomanager.common.exception.BizException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * GLM 客户端：OpenAI 兼容口（云端智谱 / 本地 Ollama 同一口子，拍板见接口清单 §11）。
 * 传输用 JDK 21 自带 HttpClient——零新依赖（本机镜像源不稳，零下载是硬要求）。
 * ⚠️ 红线：Authorization 头只在这里拼，任何日志与异常信息都不得输出 api-key。
 * 流式读在调用线程阻塞执行（chat 线程池），客户端断开由 StreamHandle.cancel 关流掐断。
 */
public class GlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    /**
     * 建一条流式对话：prepare 只建句柄不发包，start 阻塞读上游逐行回调；
     * 分两步是为了让调用方先把手柄挂进断连回调再发包，取消不落空。
     */
    public StreamHandle prepareStream(String requestBody, Consumer<String> onLine) {
        return new StreamHandle(requestBody, onLine);
    }

    /** 一条上游流的句柄：cancel 关流即掐断生成（客户端断开立即停止） */
    public final class StreamHandle {

        private final String requestBody;

        private final Consumer<String> onLine;

        private volatile boolean cancelled;

        private volatile InputStream in;

        private StreamHandle(String requestBody, Consumer<String> onLine) {
            this.requestBody = requestBody;
            this.onLine = onLine;
        }

        /** 客户端断开/超时时调用；重复调用安全 */
        public synchronized void cancel() {
            cancelled = true;
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    //关流失败不影响语义：读循环会在下一行回调失败后退出
                }
            }
        }

        /** 发请求并逐行读上游（阻塞直至流尾/取消/出错）；回调抛出的异常原样上抛 */
        public void start() throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(chatCompletionsUrl()))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    //响应头超时：流体的读超时由 SseEmitter 总超时兜底（JDK HttpClient 无流级 idle 超时）
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<InputStream> response =
                    http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new BizException(AiErrorCode.AI_UPSTREAM_ERROR,
                        "上游返回 HTTP " + response.statusCode());
            }
            in = response.body();
            if (cancelled) {
                in.close();
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while (!cancelled && (line = reader.readLine()) != null) {
                    onLine.accept(line);
                }
            }
        }
    }

    /** 非流式探测保留位（AI08 管理界面联调用，首版不实做） */
    String getApiKey() {
        return apiKey;
    }

    public String getModel() {
        return model;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** 供测试与排障看请求目标（不含任何凭据） */
    @Override
    public String toString() {
        return "GlmClient{baseUrl=" + baseUrl + ", model=" + model + "}";
    }

    /** 解析上游 JSON 工具方法（Step6 工具调用复用） */
    static JsonNode readTree(String json) throws IOException {
        return MAPPER.readTree(json);
    }
}
