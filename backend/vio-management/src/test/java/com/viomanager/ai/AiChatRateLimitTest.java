package com.viomanager.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.ai.limit.AiRateLimiter;
import com.viomanager.ai.support.GlmFakeUpstream;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterAll;
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
 * AI01 限流三件套挂载验证（接口清单 §7 拍板：每人 6 次/分 · 全局并发 5 · 单次 2048）：
 * 第 7 次 9102 + Retry-After 头；他人配额独立；全局并发第 6 个请求快速失败。
 * 全部走无会话模式（不落库），@Transactional 只兜 login 用户行的回滚。
 * 2048 截断已由 max_tokens 请求参数 + length→interrupted 映射覆盖（AiChatStreamTest）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiChatRateLimitTest {

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
    private AiRateLimiter rateLimiter;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Test
    void seventhChatRejectedWith9102AndRetryAfter() throws Exception {
        String token = login("rl_a");
        for (int i = 1; i <= 6; i++) {
            MvcResult result = mockMvc.perform(chatRequest(token, "第" + i + "问"))
                    .andExpect(request().asyncStarted())
                    .andReturn();
            mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        }
        MvcResult rejected = mockMvc.perform(chatRequest(token, "第7问"))
                .andExpect(status().isOk())
                .andReturn();
        String body = rejected.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(9102,
                (int) JsonPath.read(body, "$.code"), "第 7 次应 9102：" + body);
        String retryAfter = rejected.getResponse().getHeader("Retry-After");
        org.junit.jupiter.api.Assertions.assertNotNull(retryAfter, "9102 必须带 Retry-After 头");
        int seconds = Integer.parseInt(retryAfter);
        org.junit.jupiter.api.Assertions.assertTrue(seconds >= 1 && seconds <= 60,
                "Retry-After 应在 1-60 秒内，实际 " + seconds);
    }

    @Test
    void otherUserQuotaIndependent() throws Exception {
        String tokenA = login("rl_b1");
        String tokenB = login("rl_b2");
        for (int i = 1; i <= 6; i++) {
            MvcResult result = mockMvc.perform(chatRequest(tokenA, "甲的第" + i + "问"))
                    .andExpect(request().asyncStarted())
                    .andReturn();
            mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        }
        mockMvc.perform(chatRequest(tokenA, "甲的第7问"))
                .andExpect(jsonPath("$.code").value(9102));
        //甲触发限流不影响乙的首问
        MvcResult bFirst = mockMvc.perform(chatRequest(tokenB, "乙的首问"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(bFirst)).andExpect(status().isOk());
    }

    /**
     * 全局并发闸门：预占 4 槽 + 1 个 SLOW 慢流占住第 5 槽，第 6 个并发请求快速失败 9102（Retry-After=2）。
     * 刻意不并发 perform——MockMvc 的 async 状态机不支持并发压测（Async not started 假阴），
     * 信号量本身的并发行为已由 AiRateLimiterTest.globalSlotLimitsAndReleases 单测覆盖。
     */
    @Test
    void sixthConcurrentChatRejectedWhileFiveRunning() throws Exception {
        AiRateLimiter limiter = rateLimiter;
        for (int i = 0; i < 4; i++) {
            org.junit.jupiter.api.Assertions.assertTrue(limiter.tryAcquireGlobalSlot(), "预占前 4 个槽");
        }
        try {
            //第 5 槽：SLOW 慢流（4s）占住，不等它完成——完成即释放，第 6 个就能进了
            String fifthToken = login("rl_c5");
            MvcResult fifth = mockMvc.perform(chatRequest(fifthToken,
                            GlmFakeUpstream.SLOW_KEYWORD + " 慢问题"))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            String sixthToken = login("rl_c6");
            MvcResult rejected = mockMvc.perform(chatRequest(sixthToken, "第6个并发的提问"))
                    .andExpect(status().isOk())
                    .andReturn();
            String body = rejected.getResponse().getContentAsString();
            org.junit.jupiter.api.Assertions.assertEquals(9102,
                    (int) JsonPath.read(body, "$.code"), "并发满应快速失败 9102：" + body);
            org.junit.jupiter.api.Assertions.assertEquals("2",
                    rejected.getResponse().getHeader("Retry-After"), "并发满 Retry-After 短值 2 秒");

            mockMvc.perform(asyncDispatch(fifth)).andExpect(status().isOk());
        } finally {
            for (int i = 0; i < 4; i++) {
                limiter.releaseGlobalSlot();
            }
        }
    }

    // ---------- 支撑 ----------

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder chatRequest(
            String token, String content) {
        return post("/api/ai/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("{\"content\":\"" + content + "\"}");
    }

    private String login(String suffix) throws Exception {
        String loginName = "ai_rl_" + suffix;
        SysUser user = new SysUser();
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole("STUDENT");
        user.setRealName("AI限流-" + suffix);
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
