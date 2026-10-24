package com.viomanager.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.ai.limit.AiRateLimiter;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI02 探针（默认上下文 = 未配 GLM_API_KEY 零装配）：
 * 核心口径——未装配也回 code 0 + enabled:false，前端灰化入口不报错刷屏（接口清单 §3）。
 * 限流余量按登录人算：预扣两次后探针应显示 4；每用例独立登录名防内存限流器跨用例串数。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiStatusTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Resource
    private AiRateLimiter rateLimiter;

    @Test
    void probeReturnsDisabledWhenNotConfigured() throws Exception {
        String token = login("ai_probe_off");
        mockMvc.perform(get("/api/ai/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.model").value("glm-4-flash"))
                .andExpect(jsonPath("$.data.rateLimitRemaining").value(6));
    }

    @Test
    void probeReflectsPersonalRateLimitQuota() throws Exception {
        String token = login("ai_probe_quota");
        Long userId = userIdOf("ai_probe_quota");
        rateLimiter.tryAcquire(userId);
        rateLimiter.tryAcquire(userId);
        mockMvc.perform(get("/api/ai/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rateLimitRemaining").value(4));
    }

    @Test
    void probeRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/ai/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    /** 未装配时 chat 走 HTTP 层 9100 标准 JSON，不进 SSE 流（清单 §5） */
    @Test
    void chatRejectedWith9100WhenNotConfigured() throws Exception {
        String token = login("ai_probe_chat_off");
        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
                        .content("{\"content\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(9100));
    }

    // ---------- 支撑 ----------

    private String login(String loginName) throws Exception {
        SysUser user = new SysUser();
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole("STUDENT");
        user.setRealName("AI探针-" + loginName);
        user.setDefaultPassword(0);
        user.setStatus(1);
        sysUserMapper.insert(user);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"pass123456\"}"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    private Long userIdOf(String loginName) {
        SysUser user = sysUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysUser>()
                        .eq("login_name", loginName));
        return user.getId();
    }
}
