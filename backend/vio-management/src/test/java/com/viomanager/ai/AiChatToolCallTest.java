package com.viomanager.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.viomanager.ai.support.GlmFakeUpstream;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
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
 * AI01 工具白名单首版（清单 §2/§5）：tool start/done 事件、以当前用户身份执行、
 * 工具结果作为 role=tool 消息回传上游、最终 done 收口。
 * 测试用户无人员档案——queryMyAchievements 会抛"未绑定人员档案"，
 * 工具失败应转为错误 JSON 给模型而不炸流（首版刻意覆盖此防御路径）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiChatToolCallTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static GlmFakeUpstream upstream;

    @BeforeAll
    static void startUpstream() throws Exception {
        upstream = GlmFakeUpstream.shared();
    }

    @DynamicPropertySource
    static void glmProperties(DynamicPropertyRegistry registry) {
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
    void toolCallRoundTripWithStartDoneEvents() throws Exception {
        String token = login("tc_a");
        String body = performChat(token, GlmFakeUpstream.TOOLCALL_KEYWORD + " 我有哪些贡献？");
        //事件序：meta → tool(start) → tool(done) → delta → done
        Assertions.assertTrue(body.contains("event:tool"), "应有 tool 事件：" + body);
        Assertions.assertTrue(body.contains("\"name\":\"queryMyAchievements\"")
                && body.contains("\"phase\":\"start\"") && body.contains("\"phase\":\"done\""),
                "tool 事件应含 start 与 done 两相：" + body);
        int toolIdx = body.indexOf("event:tool");
        int deltaIdx = body.indexOf("event:delta");
        int doneIdx = body.indexOf("event:done");
        Assertions.assertTrue(toolIdx < deltaIdx && deltaIdx < doneIdx, "事件序应为 tool→delta→done");

        //第二次请求体：role=tool 消息带查询结果（无档案用户 C 线返回空汇总——真实查询结果结构）
        JsonNode messages = JSON.readTree(upstream.lastRequestBody()).get("messages");
        JsonNode toolMessage = null;
        for (JsonNode message : messages) {
            if ("tool".equals(message.path("role").asText())) {
                toolMessage = message;
            }
        }
        Assertions.assertNotNull(toolMessage, "第二次请求应含 role=tool 消息");
        Assertions.assertEquals("call_test_1", toolMessage.path("tool_call_id").asText());
        Assertions.assertTrue(toolMessage.path("content").asText().contains("teams"),
                "工具应以当前用户身份真实执行并回传结果：" + toolMessage.path("content").asText());

        Assertions.assertTrue(body.contains("\"finishReason\":\"stop\""), "工具往返后应正常 done：" + body);
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

    private String login(String suffix) throws Exception {
        String loginName = "ai_tc_" + suffix;
        SysUser user = new SysUser();
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole("STUDENT");
        user.setRealName("AI工具-" + suffix);
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
