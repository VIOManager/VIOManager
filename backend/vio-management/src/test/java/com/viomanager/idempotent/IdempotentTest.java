package com.viomanager.idempotent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 5 验证：幂等拦截全链路（dev 探针接口，@Profile("dev") 所以测试要激活 dev）。
 * 覆盖方案验证栏两条：同 key 二次请求返回 1005；过期后 key 可复用。
 * 外加：缺头 1001、同键不同参仍拦、业务失败释放 key、未登录 401、落库字段完整性。
 * @Transactional 每例回滚：MockMvc 与切面里的 JdbcTemplate 同线程同事务，幂等记录不留开发库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class IdempotentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Resource
    private JdbcTemplate jdbcTemplate;

    private SysUser probeUser;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        // 幂等接口都在登录墙内：造人 → 登录拿 token
        probeUser = new SysUser();
        probeUser.setLoginName("idem_probe_user");
        probeUser.setPassword(passwordEncoder.encode("pass123456"));
        probeUser.setRole("STUDENT");
        probeUser.setRealName("幂等探针用户");
        probeUser.setDefaultPassword(0);
        probeUser.setStatus(1);
        sysUserMapper.insert(probeUser);
        token = loginOk("idem_probe_user", "pass123456");
    }

    @Test
    void sameKeySecondCallRejected() throws Exception {
        //1.首次请求：成功，记下业务执行次数
        String body1 = probeCall(token, "itest-key-a", "{\"name\":\"first\"}");
        assertEquals(0, (int) JsonPath.read(body1, "$.code"));
        long countAfterFirst = executeCount(body1);
        //2.同 key 第二次：1005，业务不允许再次执行
        String body2 = probeCall(token, "itest-key-a", "{\"name\":\"first\"}");
        assertEquals(1005, (int) JsonPath.read(body2, "$.code"));
        //3.换新 key：正常放行，次数只加一——第二次确实被拦在业务之外
        String body3 = probeCall(token, "itest-key-b", "{\"name\":\"first\"}");
        assertEquals(0, (int) JsonPath.read(body3, "$.code"));
        assertEquals(countAfterFirst + 1, executeCount(body3));
        //4.落库字段完整性：首次结果已回填摘要，发起人已记录
        String summary = jdbcTemplate.queryForObject(
                "SELECT result_summary FROM idempotent_record WHERE idem_key = ?",
                String.class, "itest-key-a");
        assertNotNull(summary);
        assertTrue(summary.startsWith("code=0"));
        Long recordedUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM idempotent_record WHERE idem_key = ?",
                Long.class, "itest-key-a");
        assertEquals(probeUser.getId(), recordedUserId);
    }

    @Test
    void missingKeyHeaderRejected() throws Exception {
        // 规范 §6：标注解的接口幂等键必填，缺失 1001
        String body = mockMvc.perform(post("/api/dev/idem-probe")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(1001, (int) JsonPath.read(body, "$.code"));
    }

    @Test
    void sameKeyDifferentBodyStillRejected() throws Exception {
        // 同 key 不同参：拦截行为只看 key（1005），哈希差异只进日志备查
        String body1 = probeCall(token, "itest-key-c", "{\"name\":\"aaa\"}");
        assertEquals(0, (int) JsonPath.read(body1, "$.code"));
        String body2 = probeCall(token, "itest-key-c", "{\"name\":\"bbb\"}");
        assertEquals(1005, (int) JsonPath.read(body2, "$.code"));
    }

    @Test
    void businessFailureReleasesKey() throws Exception {
        //1.业务失败：1006，切面删占位
        String body1 = probeCall(token, "itest-key-d", "{\"name\":\"x\",\"fail\":true}");
        assertEquals(1006, (int) JsonPath.read(body1, "$.code"));
        //2.同 key 重试：占位已释放，本次正常执行
        String body2 = probeCall(token, "itest-key-d", "{\"name\":\"x\",\"fail\":false}");
        assertEquals(0, (int) JsonPath.read(body2, "$.code"));
    }

    @Test
    void expiredKeyReusable() throws Exception {
        //1.首次成功
        String body1 = probeCall(token, "itest-key-e", "{\"name\":\"x\"}");
        assertEquals(0, (int) JsonPath.read(body1, "$.code"));
        long count1 = executeCount(body1);
        //2.把过期时间改到过去，模拟 24 小时后同 key 再来
        jdbcTemplate.update(
                "UPDATE idempotent_record SET expire_at = '2000-01-01 00:00:00' WHERE idem_key = ?",
                "itest-key-e");
        //3.过期 key 视为新请求：正常执行，业务次数加一
        String body2 = probeCall(token, "itest-key-e", "{\"name\":\"x\"}");
        assertEquals(0, (int) JsonPath.read(body2, "$.code"));
        assertEquals(count1 + 1, executeCount(body2));
    }

    @Test
    void idempotentEndpointRequiresLogin() throws Exception {
        // 幂等接口在登录墙内：不带 token 一律 401 + 1002
        mockMvc.perform(post("/api/dev/idem-probe")
                        .header("X-Idempotency-Key", "itest-key-f")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    /** 打探针接口：带 token + 幂等键，返回响应体字符串 */
    private String probeCall(String token, String idemKey, String body) throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/dev/idem-probe")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return mvcResult.getResponse().getContentAsString();
    }

    /** Long 全局序列化为字符串，读出来要 parse 回数字 */
    private long executeCount(String body) {
        return Long.parseLong(JsonPath.read(body, "$.data.executeCount"));
    }

    /** 登录拿 token（沿用 AuthFlowTest 的写法） */
    private String loginOk(String loginName, String password) throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String body = mvcResult.getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.token");
    }
}
