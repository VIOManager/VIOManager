package com.viomanager.audit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import jakarta.annotation.Resource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计查询 AQ01-03：分页筛选/部门管理员子树过滤/学生 1003/动态类型枚举/详情技术列。
 * 探针日志固定 id 段 9201-9203（biz_type 前缀 AQ_PROBE 与真实业务隔离），事务回滚零残留。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuditQueryTest {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Resource
    private JdbcTemplate jdbcTemplate;

    private String adminToken;

    private String deptToken;

    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("aq_admin", "SUPER_ADMIN", null);
        //部门管理员挂组织 2（程序设计协会）：子树 = {2}，组织 3（电子设计协会）不在其内
        deptToken = login("aq_dept", "DEPT_ADMIN", 2L);
        studentToken = login("aq_student", "STUDENT", null);
        insertAudit(9201L, "审计探针甲", 2L, "AQ_PROBE", "审计探针类型", 1, null, LocalDateTime.now().minusHours(2));
        insertAudit(9202L, "审计探针乙", 3L, "AQ_PROBE", "审计探针类型", 0, "模拟失败", LocalDateTime.now().minusHours(1));
        insertAudit(9203L, "审计探针甲", 2L, "AQ_PROBE_2", "另一探针类型", 1, null, LocalDateTime.now().minusHours(3));
    }

    @Test
    void adminSeesAllProbeLogsNewestFirst() throws Exception {
        mockMvc.perform(get("/api/audit-logs").param("bizType", "AQ_PROBE")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2))
                //按发生时间倒序：晚一小时插入的 9202 在前
                .andExpect(jsonPath("$.data.list[0].id").value("9202"))
                .andExpect(jsonPath("$.data.list[0].bizTypeLabel").value("审计探针类型"));
    }

    @Test
    void keywordAndSuccessAndTimeFilters() throws Exception {
        mockMvc.perform(get("/api/audit-logs").param("keyword", "探针乙")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value("9202"));
        mockMvc.perform(get("/api/audit-logs").param("bizType", "AQ_PROBE").param("success", "0")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].errorMessage").value("模拟失败"));
        String since = LocalDateTime.now().minusMinutes(90).format(TIME);
        mockMvc.perform(get("/api/audit-logs").param("bizType", "AQ_PROBE").param("startTime", since)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value("9202"));
    }

    @Test
    void deptAdminSeesOnlyOwnSubtree() throws Exception {
        //组织 3 的日志对组织 2 的部门管理员不可见
        mockMvc.perform(get("/api/audit-logs").param("bizType", "AQ_PROBE")
                        .header("Authorization", "Bearer " + deptToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value("9201"));
    }

    @Test
    void deptDetailOutOfScopeHiddenAsNotFound() throws Exception {
        //越权与不存在统一 1004，不给探测口子
        mockMvc.perform(get("/api/audit-logs/9202")
                        .header("Authorization", "Bearer " + deptToken))
                .andExpect(jsonPath("$.code").value(1004));
        mockMvc.perform(get("/api/audit-logs/9201")
                        .header("Authorization", "Bearer " + deptToken))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void studentForbidden() throws Exception {
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    void bizTypesAreDynamicFromTable() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/audit-logs/biz-types")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        List<Map<String, Object>> types = JsonPath.read(result.getResponse().getContentAsString(), "$.data[*]");
        boolean found = types.stream().anyMatch(t ->
                "AQ_PROBE".equals(t.get("bizType")) && "审计探针类型".equals(t.get("label")));
        Assertions.assertTrue(found, "探针类型应出现在动态枚举里: " + types);
    }

    @Test
    void detailCarriesTechColumns() throws Exception {
        mockMvc.perform(get("/api/audit-logs/9202")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.httpMethod").value("POST"))
                .andExpect(jsonPath("$.data.uri").value("/api/probe"))
                .andExpect(jsonPath("$.data.requestParams").value("{\"k\":\"v\"}"))
                .andExpect(jsonPath("$.data.success").value(0));
    }

    @Test
    void unknownIdNotFound() throws Exception {
        mockMvc.perform(get("/api/audit-logs/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(1004));
    }

    // ---------- 支撑 ----------

    private String login(String loginName, String role, Long orgId) throws Exception {
        SysUser user = new SysUser();
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole(role);
        user.setRealName("审计探针-" + loginName);
        user.setOrgId(orgId);
        user.setDefaultPassword(0);
        user.setStatus(1);
        sysUserMapper.insert(user);
        MvcResult loginResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"pass123456\"}"))
                .andReturn();
        return JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data.token");
    }

    private void insertAudit(long id, String realName, Long orgId, String bizType,
                             String label, int success, String error, LocalDateTime at) {
        jdbcTemplate.update("INSERT INTO audit_log (id, user_id, login_name, real_name, org_id, role, "
                        + "ip, user_agent, http_method, uri, biz_type, biz_type_label, summary, "
                        + "request_params, success, error_message, cost_millis, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, null, "aq_probe", realName, orgId, "STUDENT", "127.0.0.1", "probe-ua", "POST",
                "/api/probe", bizType, label, "探针摘要", "{\"k\":\"v\"}", success, error, 12L, Timestamp.valueOf(at));
    }
}
