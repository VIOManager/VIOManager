package com.viomanager.datascop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.exception.BizException;
import com.viomanager.probe.DictTypeProbeMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.MyBatisSystemException;
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
 * Step 4 验证：数据范围（org 子树）。
 * 组织树：部门A(9001) → 子组织(9002)；部门B(9003) 独立。
 * 人员分布：部门A 2 人、子组织 1 人、部门B 1 人，共 4 人。
 * 断言方案验证栏：全部(4) ⊃ 本部门子树(3) ⊃ 本人；学生对未声明查询默认拒绝。
 * dev 探针接口只在 dev profile 存在，测试激活 dev。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("dev")
class DataScopeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Resource
    private DictTypeProbeMapper dictTypeProbeMapper;

    /** 插入测试成员前的全员数基线：开发库有演示数据等存量，绝对断言改为基线+增量 */
    private long superBaseline;

    /** 插入测试成员前 9001 子树内的成员数基线 */
    private long deptBaseline;

    @BeforeEach
    void setUp() {
        //1.组织树
        insertOrg(9001L, 0L, "部门A", "/0/9001/");
        insertOrg(9002L, 9001L, "子组织A1", "/0/9001/9002/");
        insertOrg(9003L, 0L, "部门B", "/0/9003/");
        //2.记基线（在插入测试成员之前量一遍，口径与拦截器一致）
        superBaseline = countMembers(null);
        deptBaseline = countMembers("/0/9001/");
        //3.人员：部门A 2 人、子组织 1 人、部门B 1 人
        insertMember(9101L, "成员一", 9001L);
        insertMember(9102L, "成员二", 9001L);
        insertMember(9103L, "成员三", 9002L);
        insertMember(9104L, "成员四", 9003L);
        //4.三个角色账号
        insertUser(9201L, "probe_super", "SUPER_ADMIN", null);
        insertUser(9202L, "probe_dept", "DEPT_ADMIN", 9001L);
        insertUser(9203L, "probe_student", "STUDENT", 9002L);
    }

    /** 超管：不注入过滤，看得见基线 + 本测试新增的 4 人 */
    @Test
    void superAdminSeesAllMembers() throws Exception {
        String token = loginOk("probe_super");
        mockMvc.perform(get("/api/dev/scope-probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.data.orgScopeDenied").value(false))
                .andExpect(jsonPath("$.data.orgScopedMemberCount").value((int) (superBaseline + 4)));
    }

    /** 部门管理员：只看得见本部门子树（9001 + 9002）的基线 + 3 人，看不见部门B */
    @Test
    void deptAdminSeesOnlySubtree() throws Exception {
        String token = loginOk("probe_dept");
        mockMvc.perform(get("/api/dev/scope-probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("DEPT_ADMIN"))
                .andExpect(jsonPath("$.data.orgScopeDenied").value(false))
                .andExpect(jsonPath("$.data.orgScopedMemberCount").value((int) (deptBaseline + 3)));
    }

    /** 学生：未声明的 ORG 查询被默认拒绝，SELF 查询（本人登录日志）放行——全部 ⊃ 本部门 ⊃ 本人 */
    @Test
    void studentDeniedByDefaultButSelfQueryWorks() throws Exception {
        String token = loginOk("probe_student");
        mockMvc.perform(get("/api/dev/scope-probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.orgScopeDenied").value(true))
                // 刚才登录成功，本人登录日志至少 1 条
                .andExpect(jsonPath("$.data.myLoginLogCount").value(1));
    }

    /** 兜底验证：不走 HTTP，学生身份直接调未声明注解的 mapper，必须被拦（BizException 会被 MyBatis 包一层） */
    @Test
    void unannotatedQueryThrowsForStudent() {
        CurrentUser student = new CurrentUser();
        student.setUserId(9203L);
        student.setRole("STUDENT");
        student.setOrgId(9002L);
        UserHolder.saveUser(student);
        try {
            // dictTypeProbeMapper 的方法没标 @DataScope，学生身份下必须被拦
            MyBatisSystemException e = assertThrows(MyBatisSystemException.class,
                    () -> dictTypeProbeMapper.selectList(null));
            BizException biz = BizException.unwrap(e);
            assertEquals(1003, biz.getCode());
        } finally {
            UserHolder.removeUser();
        }
    }

    /** 超管数字 ⊇ 部门子树数字 ⊇ 本人数字 的整体关系（用真实接口结果再核一遍） */
    @Test
    void scopeNestingHolds() throws Exception {
        long superCount = probeCount("probe_super");
        long deptCount = probeCount("probe_dept");
        long selfCount = probeSelfCount("probe_student");
        assertTrue(superCount >= deptCount);
        assertTrue(deptCount > selfCount);
    }

    private long probeCount(String loginName) throws Exception {
        String token = loginOk(loginName);
        String body = mockMvc.perform(get("/api/dev/scope-probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Long→String 全局序列化（规范 §2.1），计数在 JSON 里是字符串，读出来 parseLong
        String count = JsonPath.read(body, "$.data.orgScopedMemberCount");
        return Long.parseLong(count);
    }

    private long probeSelfCount(String loginName) throws Exception {
        String token = loginOk(loginName);
        String body = mockMvc.perform(get("/api/dev/scope-probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String count = JsonPath.read(body, "$.data.myLoginLogCount");
        return Long.parseLong(count);
    }

    private String loginOk(String loginName) throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"pass123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return JsonPath.read(mvcResult.getResponse().getContentAsString(), "$.data.token");
    }

    private void insertOrg(Long id, Long parentId, String name, String path) {
        jdbcTemplate.update(
                "INSERT INTO sys_org (id, parent_id, name, org_type, path) VALUES (?, ?, ?, 'DEPARTMENT', ?)",
                id, parentId, name, path);
    }

    /** 全员或指定 path 子树内的成员数（与数据范围拦截器同口径，量基线用） */
    private long countMembers(String pathPrefix) {
        Long count;
        if (pathPrefix == null) {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM member WHERE deleted = 0", Long.class);
        } else {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM member WHERE deleted = 0 AND org_id IN "
                            + "(SELECT id FROM sys_org WHERE path LIKE CONCAT(?, '%') AND deleted = 0)",
                    Long.class, pathPrefix);
        }
        return count == null ? 0 : count;
    }

    private void insertMember(Long id, String name, Long orgId) {
        jdbcTemplate.update("INSERT INTO member (id, name, org_id) VALUES (?, ?, ?)", id, name, orgId);
    }

    private void insertUser(Long id, String loginName, String role, Long orgId) {
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, login_name, password, role, org_id, real_name, default_password, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 0, 1)",
                id, loginName, passwordEncoder.encode("pass123456"), role, orgId, loginName);
    }
}
