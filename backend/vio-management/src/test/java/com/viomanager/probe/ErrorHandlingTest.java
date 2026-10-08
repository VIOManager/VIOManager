package com.viomanager.probe;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 1 验证：统一响应体与全局异常处理器的行为契约。
 * 覆盖：6001 业务码透传、1001 参数校验（缺参/字段校验）、1000 兜底、
 * 1004 路由未命中、health 走 Result 后的形态。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bizExceptionShouldPassThroughBusinessCode() throws Exception {
        mockMvc.perform(get("/api/test/biz-error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(6001))
                .andExpect(jsonPath("$.message").value("审批单不存在或已处理"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void missingParamShouldReturn1001WithParamName() throws Exception {
        mockMvc.perform(get("/api/test/missing-param"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("参数校验失败：缺少必填参数 name"));
    }

    @Test
    void blankFieldShouldReturn1001WithFieldName() throws Exception {
        mockMvc.perform(post("/api/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("参数校验失败：name 不能为空"));
    }

    @Test
    void unknownErrorShouldReturn1000WithoutStackTrace() throws Exception {
        mockMvc.perform(get("/api/test/runtime-error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后重试"));
    }

    @Test
    void unmatchedRouteShouldReturn1004InUnifiedBody() throws Exception {
        mockMvc.perform(get("/api/no-such-route"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004))
                .andExpect(jsonPath("$.message").value("资源不存在"));
    }

    @Test
    void healthShouldUseResultEnvelope() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }
}
