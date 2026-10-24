package com.viomanager.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI01 SSE 聊天主干（无会话模式）：本进程起一个 OpenAI 兼容假上游（JDK HttpServer，随机端口），
 * properties 注入占位假 key 与假 baseUrl——只验编排与事件序，绝不碰真实 GLM。
 * 覆盖：meta→delta×N→done 事件序与 usage 统计 / length 截断映射 interrupted /
 * 上游 500 → SSE error 事件 9103 / 请求体带服务端系统提示词与 max_tokens=2048。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiChatStreamTest {

    /** 假上游：静态块起服务保证 @DynamicPropertySource 注入端口时已就绪 */
    private static final HttpServer UPSTREAM;

    private static final int PORT;

    /** 最近一次打到假上游的请求体（断言 system/user 双消息与 max_tokens） */
    private static final AtomicReference<String> LAST_REQUEST = new AtomicReference<>();

    static {
        try {
            UPSTREAM = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            UPSTREAM.createContext("/api/paas/v4/chat/completions", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                LAST_REQUEST.set(body);
                if (body.contains("FAILUPSTREAM")) {
                    respond(exchange, 500, "application/json", "{\"error\":\"upstream down\"}");
                    return;
                }
                String sse = body.contains("TRUNCATE")
                        ? "data: {\"choices\":[{\"delta\":{\"content\":\"被截断的\"}}]}\n\n"
                        + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}\n\n"
                        + "data: [DONE]\n\n"
                        : "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\n"
                        + "data: {\"choices\":[{\"delta\":{\"content\":\"，平台\"}}]}\n\n"
                        + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],"
                        + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":5}}\n\n"
                        + "data: [DONE]\n\n";
                respond(exchange, 200, "text/event-stream", sse);
            });
            UPSTREAM.start();
            PORT = UPSTREAM.getAddress().getPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stopUpstream() {
        UPSTREAM.stop(0);
    }

    @DynamicPropertySource
    static void glmProperties(DynamicPropertyRegistry registry) {
        registry.add("vio.ai.api-key", () -> "test-dummy-key-not-real");
        registry.add("vio.ai.base-url", () -> "http://127.0.0.1:" + PORT + "/api/paas/v4");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Test
    void streamsMetaDeltaDoneWithUsage() throws Exception {
        String token = login("ai_chat_stream_a");
        String body = performChat(token, "介绍下你自己");
        assertTrue(body.contains("event:meta"), "首帧应为 meta：" + body);
        assertTrue(body.contains("\"conversationId\":null"), "无会话模式 conversationId 为 null");
        assertTrue(body.contains("event:delta") && body.contains("\"text\":\"你好\""));
        assertTrue(body.contains("\"text\":\"，平台\""), "delta 应按上游分片多次下发");
        assertTrue(body.contains("event:done"));
        assertTrue(body.contains("\"finishReason\":\"stop\""));
        assertTrue(body.contains("\"prompt\":12") && body.contains("\"completion\":5"));
        assertTrue(!body.contains("event:error"), "正常流不应有 error 事件");

        //请求体口径：服务端系统提示词 + user 消息 + max_tokens 2048 + include_usage
        String sent = LAST_REQUEST.get();
        ArrayNode messages = (ArrayNode) new ObjectMapper().readTree(sent).get("messages");
        Assertions.assertEquals(2, messages.size());
        Assertions.assertEquals("system", messages.get(0).get("role").asText());
        Assertions.assertTrue(messages.get(0).get("content").asText().contains("VIOManager"));
        Assertions.assertEquals("user", messages.get(1).get("role").asText());
        Assertions.assertEquals("介绍下你自己", messages.get(1).get("content").asText());
        Assertions.assertEquals(2048, new ObjectMapper().readTree(sent).get("max_tokens").asInt());
    }

    @Test
    void lengthFinishMappedToInterrupted() throws Exception {
        String token = login("ai_chat_truncate_b");
        String body = performChat(token, "TRUNCATE 超长问题");
        assertTrue(body.contains("\"finishReason\":\"interrupted\""), "length 截断应映射 interrupted：" + body);
    }

    @Test
    void upstreamFailureEmitsError9103() throws Exception {
        String token = login("ai_chat_upstream_c");
        String body = performChat(token, "FAILUPSTREAM 触发上游故障");
        assertTrue(body.contains("event:error"), "上游失败应走 SSE error 事件：" + body);
        assertTrue(body.contains("\"code\":9103"));
    }

    // ---------- 支撑 ----------

    private String performChat(String token, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String login(String loginName) throws Exception {
        SysUser user = new SysUser();
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole("STUDENT");
        user.setRealName("AI流式-" + loginName);
        user.setDefaultPassword(0);
        user.setStatus(1);
        sysUserMapper.insert(user);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"pass123456\"}"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status,
                                String contentType, String payload) throws IOException {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
        exchange.close();
    }
}
