package com.viomanager.probe;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;

/**
 * Step 1 验证：统一响应体与全局异常处理器的行为契约。
 * 覆盖：6001 业务码透传、1001 参数校验（缺参/字段校验）、1000 兜底、
 * 1004 路由未命中、health 走 Result 后的形态。
 * Step 3 认证上线后，探针端点受 JWT 保护，所有用例先登录拿 token 再请求。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        // 造一个测试账号并登录拿 token，后续请求都带上
        SysUser user = new SysUser();
        user.setLoginName("probe_error");
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole("STUDENT");
        user.setRealName("异常探针用户");
        user.setDefaultPassword(0);
        user.setStatus(1);
        sysUserMapper.insert(user);

        MvcResult mvcResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"probe_error\",\"password\":\"pass123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        token = JsonPath.read(mvcResult.getResponse().getContentAsString(), "$.data.token");
    }

    @Test
    void bizExceptionShouldPassThroughBusinessCode() throws Exception {
        mockMvc.perform(get("/api/test/biz-error").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(6001))
                .andExpect(jsonPath("$.message").value("审批单不存在或已处理"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void missingParamShouldReturn1001WithParamName() throws Exception {
        mockMvc.perform(get("/api/test/missing-param").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("参数校验失败：缺少必填参数 name"));
    }

    @Test
    void blankFieldShouldReturn1001WithFieldName() throws Exception {
        mockMvc.perform(post("/api/test/validated")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("参数校验失败：name 不能为空"));
    }

    @Test
    void unknownErrorShouldReturn1000WithoutStackTrace() throws Exception {
        mockMvc.perform(get("/api/test/runtime-error").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后重试"));
    }

    @Test
    void unmatchedRouteShouldReturn1004InUnifiedBody() throws Exception {
        mockMvc.perform(get("/api/no-such-route").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004))
                .andExpect(jsonPath("$.message").value("资源不存在"));
    }

    @Test
    void healthShouldUseResultEnvelope() throws Exception {
        // health 在免认证名单里，不需要 token
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }
}
