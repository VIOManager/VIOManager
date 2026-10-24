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
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
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
 * AI01 SSE 聊天主干（无会话模式）：JVM 共享假上游（GlmFakeUpstream，见其类注释——
 * 共享是为了所有 AI 测试类同一个上下文缓存 key，不撑爆 MySQL 连接数）。
 * 覆盖：meta→delta×N→done 事件序与 usage 统计 / length 截断映射 interrupted /
 * 上游 500 → SSE error 事件 9103 / 请求体带服务端系统提示词与 max_tokens=2048。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiChatStreamTest {

    /** 最近一次打到假上游的请求体（断言 system/user 双消息与 max_tokens） */
    private static final AtomicReference<String> LAST_REQUEST = new AtomicReference<>();

    @DynamicPropertySource
    static void glmProperties(DynamicPropertyRegistry registry) throws Exception {
        com.viomanager.ai.support.GlmFakeUpstream upstream = com.viomanager.ai.support.GlmFakeUpstream.shared();
        registry.add("vio.ai.api-key", () -> "test-dummy-key-not-real");
        registry.add("vio.ai.base-url", upstream::baseUrl);
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
        String sent = com.viomanager.ai.support.GlmFakeUpstream.shared().lastRequestBody();
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
}
