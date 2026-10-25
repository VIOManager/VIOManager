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
 * 按提问内容分叉四种行为，覆盖正常流 / length 截断 / 上游故障 / 慢速占槽 / 工具调用：
 *   默认      → 两个 delta + stop + usage(12,5) + [DONE]
 *   TRUNCATE  → 一个 delta + finish_reason=length + [DONE]
 *   FAILUPSTREAM → HTTP 500
 *   SLOWUPSTREAM → 延迟 4 秒（占并发槽）
 *   TOOLCALL（第 1 次请求）→ tool_calls 流；第 2 次（带工具结果）→ 正常流
 * ⚠️ JVM 共享单例：所有 AI 测试类的 @DynamicPropertySource 拿同一个端口，
 * 上下文缓存 key 才能一致——多 key 会各起新上下文，Hikari 池累加打爆 MySQL 连接数
 * （全量 332 测实测 Too many connections 连坐 62 错）。工作线程 daemon，JVM 可正常退出。
 */
public final class GlmFakeUpstream implements AutoCloseable {

    public static final String TRUNCATE_KEYWORD = "TRUNCATE";

    public static final String FAIL_KEYWORD = "FAILUPSTREAM";

    /** 慢速流：延迟 4 秒再回，测试全局并发闸门时用来占住槽位 */
    public static final String SLOW_KEYWORD = "SLOWUPSTREAM";

    /** 工具调用演练：该关键字的第 1 次请求回 tool_calls 流，第 2 次（带工具结果）回正常流 */
    public static final String TOOLCALL_KEYWORD = "TOOLCALL";

    private static volatile GlmFakeUpstream shared;

    private final HttpServer server;

    private final AtomicReference<String> lastRequest = new AtomicReference<>();

    private GlmFakeUpstream(HttpServer server) {
        this.server = server;
    }

    /** JVM 共享实例：双检锁懒启动，各测试类直接用（无需也不许自行 start/close） */
    public static GlmFakeUpstream shared() throws IOException {
        if (shared == null) {
            synchronized (GlmFakeUpstream.class) {
                if (shared == null) {
                    shared = start();
                }
            }
        }
        return shared;
    }

    public static GlmFakeUpstream start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        //daemon 工作线程：共享单例无人关闭，非 daemon 会挂住 surefire 的 fork JVM
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "glm-fake-upstream");
            t.setDaemon(true);
            return t;
        }));
        GlmFakeUpstream upstream = new GlmFakeUpstream(server);
        server.createContext("/api/paas/v4/chat/completions", upstream::handle);
        server.start();
        return upstream;
    }

    /** 注入 vio.ai.base-url 的值（含 /api/paas/v4 根） */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/paas/v4";
    }

    /** 最近一次打到假上游的请求体（断言系统提示词/历史拼接/max_tokens/工具结果） */
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
        if (body.contains(SLOW_KEYWORD)) {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        //工具往返分叉按消息语义而非请求序号：共享单例的全局计数会被并跑的其他测试类消耗掉
        if (body.contains(TOOLCALL_KEYWORD) && !body.contains("\"role\":\"tool\"")) {
            respond(exchange, 200, "text/event-stream",
                    "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                            + "\"id\":\"call_test_1\",\"type\":\"function\","
                            + "\"function\":{\"name\":\"queryMyAchievements\",\"arguments\":\"\"}}]}}]}\n\n"
                            //模型过程自语（工具调用之后的伪代码）：编排层必须抑制，不得发给用户
                            + "data: {\"choices\":[{\"delta\":{\"content\":"
                            + "\"defaultManager.queryMyAchievements()\"}}]}\n\n"
                            + "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                            + "\"function\":{\"arguments\":\"{}\"}}]}}]}\n\n"
                            + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}\n\n"
                            + "data: [DONE]\n\n");
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
