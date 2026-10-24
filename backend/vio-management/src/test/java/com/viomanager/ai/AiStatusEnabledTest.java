package com.viomanager.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.ai.client.GlmClient;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI02 探针（装配态）：properties 注入占位假 key 起独立上下文——只验证 Bean 装配与探针回显，
 * 不发任何真实请求。⚠️ 该值是测试占位串，不是真实密钥（红线：真 key 绝不进代码）。
 */
@SpringBootTest(properties = "vio.ai.api-key=test-dummy-key-not-real")
@AutoConfigureMockMvc
@Transactional
class AiStatusEnabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private ObjectProvider<GlmClient> clientProvider;

    @Test
    void clientBeanAssembledWhenKeyPresent() {
        Assertions.assertNotNull(clientProvider.getIfAvailable(), "key 非空应装配 GlmClient Bean");
    }

    @Test
    void probeReturnsEnabledWithModel() throws Exception {
        String token = login("ai_probe_on");
        mockMvc.perform(get("/api/ai/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.model").value("glm-4-flash"))
                .andExpect(jsonPath("$.data.rateLimitRemaining").value(6));
    }

    private String login(String loginName) throws Exception {
        SysUser user = new SysUser();
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole("STUDENT");
        user.setRealName("AI探针装配-" + loginName);
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
