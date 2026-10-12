package com.viomanager.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 6 验证：审计日志全链路（同步模式，写完立即可查）。
 * 覆盖方案验证栏：调注解接口后 audit_log 新增一行，字段映射表每列均有值；
 * 外加失败留痕、敏感参数不落库、被角色校验拒绝的调用同样留痕、XFF 全内网回退。
 * ⚠️ vio.audit.async=false：同步写库才能在 @Transactional 里断言并回滚，
 * 后续各线写涉及审计接口的测试时照抄这个注解，否则异步线程会写出测试事务外的脏数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@TestPropertySource(properties = "vio.audit.async=false")
class AuditLogTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SysUser probeUser;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        probeUser = new SysUser();
        probeUser.setLoginName("audit_probe_user");
        probeUser.setPassword(passwordEncoder.encode("pass123456"));
        probeUser.setRole("STUDENT");
        probeUser.setRealName("审计探针用户");
        probeUser.setDefaultPassword(0);
        probeUser.setStatus(1);
        sysUserMapper.insert(probeUser);
        token = loginOk("audit_probe_user", "pass123456");
    }

    @Test
    void auditRowCapturesAllColumns() throws Exception {
        // 带伪造代理链与 UA 调探针：XFF 首个公网地址才是真实客户端
        mockMvc.perform(post("/api/dev/audit-probe")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-For", "202.97.1.1, 10.0.0.5")
                        .header("User-Agent", "audit-ua-test/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"张三\",\"fail\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        // 方案的字段映射表逐列断言
        Map<String, Object> row = latestAuditRow("DEV_AUDIT_PROBE");
        assertNotNull(row, "审计应新增一行");
        assertEquals(probeUser.getId(), row.get("user_id"));
        assertEquals("audit_probe_user", row.get("login_name"));
        assertEquals("审计探针用户", row.get("real_name"));
        assertEquals("STUDENT", row.get("role"));
        assertEquals("202.97.1.1", row.get("ip"));
        assertEquals("audit-ua-test/1.0", row.get("user_agent"));
        assertEquals("POST", row.get("http_method"));
        assertEquals("/api/dev/audit-probe", row.get("uri"));
        assertEquals("DEV_AUDIT_PROBE", row.get("biz_type"));
        assertEquals("审计探针", row.get("biz_type_label"));
        assertEquals("操作内容：张三", row.get("summary"));
        assertTrue(String.valueOf(row.get("request_params")).contains("name"), "参数摘要应落库");
        assertEquals(1, ((Number) row.get("success")).intValue());
        assertTrue(((Number) row.get("cost_millis")).longValue() >= 0);
        assertNotNull(row.get("created_at"));
    }

    @Test
    void failureRecordedAndRethrown() throws Exception {
        // 业务失败：接口照常报 1006，审计记 success=0 与失败原因
        mockMvc.perform(post("/api/dev/audit-probe")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"李四\",\"fail\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
        Map<String, Object> row = latestAuditRow("DEV_AUDIT_PROBE");
        assertNotNull(row);
        assertEquals(0, ((Number) row.get("success")).intValue());
        assertEquals("探针模拟业务失败", row.get("error_message"));
    }

    @Test
    void sensitiveParamsNotStored() throws Exception {
        // 真实接口改密（A02）：敏感接口参数不落审计——库里只有"谁改了密码"，没有新旧密码
        mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"pass123456\",\"newPassword\":\"new-pass-456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        Map<String, Object> row = latestAuditRow("AUTH_PASSWORD_CHANGE");
        assertNotNull(row);
        assertNull(row.get("request_params"), "敏感接口参数必须留空");
        assertEquals(1, ((Number) row.get("success")).intValue());
        assertEquals("audit_probe_user", row.get("login_name"));
        assertEquals("PUT", row.get("http_method"));
    }

    @Test
    void roleRejectionAlsoAudited() throws Exception {
        // 学生调"仅超管"探针：403 拒绝，但审计照样留一行失败记录（谁试图越权本身就该留痕）
        mockMvc.perform(get("/api/dev/audit-probe/super")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
        Map<String, Object> row = latestAuditRow("DEV_AUDIT_PROBE_SUPER");
        assertNotNull(row);
        assertEquals(0, ((Number) row.get("success")).intValue());
        assertTrue(String.valueOf(row.get("error_message")).contains("仅限"));
    }

    @Test
    void xffAllInternalFallsBackToRemoteAddr() throws Exception {
        // XFF 全是内网地址（内网直连没经过公网代理）：回退直连地址，MockMvc 默认 127.0.0.1
        mockMvc.perform(post("/api/dev/audit-probe")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-For", "10.0.0.5, 192.168.1.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"王五\",\"fail\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        Map<String, Object> row = latestAuditRow("DEV_AUDIT_PROBE");
        assertNotNull(row);
        assertEquals("127.0.0.1", row.get("ip"));
    }

    /** 取指定业务类型最新一行审计（同步模式写在测试事务里，读得到） */
    private Map<String, Object> latestAuditRow(String bizType) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM audit_log WHERE biz_type = ? ORDER BY id DESC", bizType);
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    /** 登录拿 token（沿用 AuthFlowTest 的写法） */
    private String loginOk(String loginName, String password) throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return JsonPath.read(mvcResult.getResponse().getContentAsString(), "$.data.token");
    }
}
