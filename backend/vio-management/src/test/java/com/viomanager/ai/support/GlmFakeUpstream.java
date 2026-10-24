package com.viomanager.ai.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenAI 兼容假上游（测试工具）：本进程 JDK HttpServer，随机端口。
 * 按提问内容分叉三种行为，覆盖正常流 / length 截断 / 上游故障：
 *   默认      → 两个 delta + stop + usage(12,5) + [DONE]
 *   TRUNCATE  → 一个 delta + finish_reason=length + [DONE]
 *   FAILUPSTREAM → HTTP 500
 * 只给 AI 聊天测试当上游，绝不连真实 GLM；key 由测试注入占位假值。
 */
public final class GlmFakeUpstream implements AutoCloseable {

    public static final String TRUNCATE_KEYWORD = "TRUNCATE";

    public static final String FAIL_KEYWORD = "FAILUPSTREAM";

    private final HttpServer server;

    private final AtomicReference<String> lastRequest = new AtomicReference<>();

    private GlmFakeUpstream(HttpServer server) {
        this.server = server;
    }

    public static GlmFakeUpstream start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        GlmFakeUpstream upstream = new GlmFakeUpstream(server);
        server.createContext("/api/paas/v4/chat/completions", upstream::handle);
        server.start();
        return upstream;
    }

    /** 注入 vio.ai.base-url 的值（含 /api/paas/v4 根） */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/paas/v4";
    }

    /** 最近一次打到假上游的请求体（断言系统提示词/历史拼接/max_tokens） */
    public String lastRequestBody() {
        return lastRequest.get();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        lastRequest.set(body);
        if (body.contains(FAIL_KEYWORD)) {
            respond(exchange, 500, "application/json", "{\"error\":\"upstream down\"}");
            return;
        }
        String sse = body.contains(TRUNCATE_KEYWORD)
                ? "data: {\"choices\":[{\"delta\":{\"content\":\"被截断的\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}\n\n"
                + "data: [DONE]\n\n"
                : "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"，平台\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":5}}\n\n"
                + "data: [DONE]\n\n";
        respond(exchange, 200, "text/event-stream", sse);
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String payload)
            throws IOException {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
