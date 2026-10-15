package com.viomanager.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * M1 项目主体安全测试（PR01–PR05）：可见性八条规则矩阵、学生列表口径、
 * 越权（7002）、重名（7004）、状态机顺序（1006）、乐观锁（1007）、
 * 挂载拦截删除（7010）、级联逻辑删、创建默认值。
 * 子表行（member/advisor/grant/ref）用 jdbcTemplate 直插——M2/M3 的写接口未交付，
 * 但可见性判定依赖的读路径本里程碑就要钉死。
 */
class ProjectSafetyTest extends PmTestSupport {

    private static final Long ORG_SOFT = 9301L;
    private static final Long ORG_SOFT_CHILD = 9303L;
    private static final Long ORG_MECH = 9302L;
    private static final Long SEMESTER = 95001L;

    private String ownerToken;
    private String sameOrgStudentToken;
    private String otherOrgStudentToken;
    private String teacherToken;
    private String deptSoftToken;
    private String deptMechToken;
    private String superToken;

    @BeforeEach
    void setUp() throws Exception {
        //组织树：软件学院（含子组织 9303）/ 机械学院
        insertOrg(ORG_SOFT, "软件学院", "/0/9301/");
        insertOrg(ORG_SOFT_CHILD, "软件-嵌入式组", "/0/9301/9303/");
        insertOrg(ORG_MECH, "机械学院", "/0/9302/");
        insertCurrentSemester(SEMESTER, "2026-2027学年第一学期-PM测试");
        //档案：A/C 在软件学院，B 与教师 T 在机械学院
        insertMember(80001L, "项目A", ORG_SOFT, "STUDENT");
        insertMember(80002L, "项目B", ORG_MECH, "STUDENT");
        insertMember(80003L, "项目C", ORG_SOFT, "STUDENT");
        insertMember(80004L, "教师T", ORG_MECH, "TEACHER");
        ownerToken = createUserAndLogin("pm_owner_a", "STUDENT", ORG_SOFT, 80001L);
        otherOrgStudentToken = createUserAndLogin("pm_student_b", "STUDENT", ORG_MECH, 80002L);
        sameOrgStudentToken = createUserAndLogin("pm_student_c", "STUDENT", ORG_SOFT, 80003L);
        teacherToken = createUserAndLogin("pm_teacher_t", "STUDENT", ORG_MECH, 80004L);
        deptSoftToken = createUserAndLogin("pm_dept_soft", "DEPT_ADMIN", ORG_SOFT, null);
        deptMechToken = createUserAndLogin("pm_dept_mech", "DEPT_ADMIN", ORG_MECH, null);
        superToken = createAdminAndLogin("pm_super", "SUPER_ADMIN");
    }

    /** 建项目并返回 id（默认 ORG / 当前学期 / 本人组织）。id 全局按字符串序列化（雪花防 JS 精度丢失） */
    private long createProject(String token, String name, String visibility) throws Exception {
        String body = createProject(token,
                "{\"name\":\"" + name + "\",\"visibility\":\"" + visibility + "\"}");
        assertEquals(0, (int) JsonPath.read(body, "$.code"), body);
        return Long.parseLong(JsonPath.read(body, "$.data.id"));
    }

    /** GET 详情返回响应体字符串 */
    private String detail(String token, long id) throws Exception {
        return mockMvc.perform(withToken(get("/api/projects/" + id), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private int detailCode(String token, long id) throws Exception {
        return (int) JsonPath.read(detail(token, id), "$.code");
    }

    /** PUT 编辑返回响应体字符串 */
    private String update(String token, long id, String body) throws Exception {
        return mockMvc.perform(withToken(put("/api/projects/" + id), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    // ---------------------------------------------------------------- 可见性矩阵

    @Test
    void visibilityMatrixOnOrgProject() throws Exception {
        long id = createProject(ownerToken, "矩阵用例项目", "ORG");
        //规则1 所有人；规则6 ORG 同部门（含子组织用户）；规则3 超管；规则4 部门管理员子树
        assertEquals(0, detailCode(ownerToken, id));
        assertEquals(0, detailCode(sameOrgStudentToken, id));
        assertEquals(0, detailCode(superToken, id));
        assertEquals(0, detailCode(deptSoftToken, id));
        //外部门学生 / 外部门管理员 → 7003（规则8）
        assertEquals(7003, detailCode(otherOrgStudentToken, id));
        assertEquals(7003, detailCode(deptMechToken, id));
    }

    @Test
    void publicProjectVisibleAcrossOrgs() throws Exception {
        long id = createProject(ownerToken, "公开项目", "PUBLIC");
        assertEquals(0, detailCode(otherOrgStudentToken, id));
        assertEquals(0, detailCode(deptMechToken, id));
    }

    @Test
    void privateProjectVisibleOnlyToOwnerAndManagers() throws Exception {
        long id = createProject(ownerToken, "私有项目", "PRIVATE");
        //同部门学生也看不到（PRIVATE 不命中规则5/6），只有所有人/超管/部门管理员可见
        assertEquals(0, detailCode(ownerToken, id));
        assertEquals(0, detailCode(superToken, id));
        assertEquals(0, detailCode(deptSoftToken, id));
        assertEquals(7003, detailCode(sameOrgStudentToken, id));
        assertEquals(7003, detailCode(otherOrgStudentToken, id));
    }

    @Test
    void orgVisibilityCoversChildOrgUsers() throws Exception {
        //项目挂软件学院，子组织 9303 的用户在项目 org 子树内 → 规则6 可见
        String childToken = createUserAndLogin("pm_student_child", "STUDENT", ORG_SOFT_CHILD, null);
        long id = createProject(ownerToken, "子组织可见项目", "ORG");
        assertEquals(0, detailCode(childToken, id));
    }

    @Test
    void memberAndAdvisorSeeProject() throws Exception {
        long id = createProject(ownerToken, "成员教师可见项目", "ORG");
        //成员：外部门的 B 被加为成员后可见（规则2 不限组织）
        jdbcTemplate.update(
                "INSERT INTO project_member (id, project_id, member_id, member_name, project_role) "
                        + "VALUES (70001, ?, 80002, '项目B', 'MEMBER')", id);
        assertEquals(0, detailCode(otherOrgStudentToken, id));
        //指导教师：外部门的教师 T 被挂名后可见（只读，写口另有 7002 拦截）
        jdbcTemplate.update(
                "INSERT INTO project_advisor (id, project_id, member_id, advisor_name, status) "
                        + "VALUES (70002, ?, 80004, '教师T', 'ACTIVE')", id);
        assertEquals(0, detailCode(teacherToken, id));
    }

    @Test
    void activeGrantMakesProjectVisible() throws Exception {
        long id = createProject(ownerToken, "授权可见项目", "PRIVATE");
        assertEquals(7003, detailCode(otherOrgStudentToken, id));
        jdbcTemplate.update(
                "INSERT INTO project_view_grant (id, project_id, applicant_id, grant_type, status) "
                        + "VALUES (70003, ?, 80002, 'LIMITED', 'ACTIVE')", id);
        assertEquals(0, detailCode(otherOrgStudentToken, id));
    }

    // ---------------------------------------------------------------- 学生列表口径

    @Test
    void studentListOnlyShowsParticipatedOrGranted() throws Exception {
        long id = createProject(ownerToken, "列表口径项目", "ORG");
        //同部门学生：详情可见（ORG）但列表不含（学生列表=参与∪授权，PR01 口径）
        String cList = list(sameOrgStudentToken);
        assertEquals(0, (int) JsonPath.read(cList, "$.code"));
        assertFalse(containsProject(sameOrgStudentToken, id), "ORG 可见不应进入学生列表: " + cList);
        //参与后进入列表
        jdbcTemplate.update(
                "INSERT INTO project_member (id, project_id, member_id, member_name, project_role) "
                        + "VALUES (70004, ?, 80003, '项目C', 'MEMBER')", id);
        assertTrue(containsProject(sameOrgStudentToken, id), "成为成员后应出现在列表");
        //授权后进入列表
        jdbcTemplate.update(
                "INSERT INTO project_view_grant (id, project_id, applicant_id, grant_type, status) "
                        + "VALUES (70005, ?, 80002, 'ONCE', 'ACTIVE')", id);
        assertTrue(containsProject(otherOrgStudentToken, id), "获得授权后应出现在列表");
    }

    private String list(String token) throws Exception {
        return mockMvc.perform(withToken(get("/api/projects"), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /** 列表是否含指定项目（JsonPath 过滤精确匹配，不靠字符串 contains） */
    private boolean containsProject(String token, long id) throws Exception {
        java.util.List<?> matched =
                JsonPath.read(list(token), "$.data.list[?(@.id == " + id + ")].id");
        return matched.size() == 1;
    }

    // ---------------------------------------------------------------- 越权与防重

    @Test
    void nonOwnerCannotEditOrDelete() throws Exception {
        long id = createProject(ownerToken, "越权用例项目", "ORG");
        //同部门学生（可见但非所有人）编辑/删除 → 7002
        assertEquals(7002, (int) JsonPath.read(
                update(sameOrgStudentToken, id, "{\"description\":\"篡改\"}"), "$.code"));
        assertEquals(7002, (int) JsonPath.read(
                mockMvc.perform(withToken(delete("/api/projects/" + id), sameOrgStudentToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(), "$.code"));
        //超管可见但同样不可写（清单待确认③：管理员不代管他人项目，仅所有人 7002 从严）
        assertEquals(7002, (int) JsonPath.read(
                update(superToken, id, "{\"description\":\"代管\"}"), "$.code"));
    }

    @Test
    void duplicateNameInSameSemesterOrgRejected() throws Exception {
        createProject(ownerToken, "重名验证项目", "ORG");
        //同学期同组织重名 → 7004
        String dup = createProject(ownerToken,
                "{\"name\":\"重名验证项目\",\"visibility\":\"ORG\"}");
        assertEquals(7004, (int) JsonPath.read(dup, "$.code"), dup);
        //不同组织同名不冲突（唯一键含 org_id）
        String otherOrg = createProject(otherOrgStudentToken,
                "{\"name\":\"重名验证项目\",\"orgId\":" + ORG_MECH + "}");
        assertEquals(0, (int) JsonPath.read(otherOrg, "$.code"), otherOrg);
    }

    // ---------------------------------------------------------------- 状态机与乐观锁

    @Test
    void statusMustFlowInOrder() throws Exception {
        long id = createProject(ownerToken, "状态机项目", "ORG");
        //跳档 ACTIVE→ARCHIVED → 1006
        assertEquals(1006, (int) JsonPath.read(
                update(ownerToken, id, "{\"status\":\"ARCHIVED\"}"), "$.code"));
        //回退/乱序同理
        //按序 ACTIVE→FINISHED→ARCHIVED
        assertEquals(0, (int) JsonPath.read(
                update(ownerToken, id, "{\"status\":\"FINISHED\"}"), "$.code"));
        assertEquals(0, (int) JsonPath.read(
                update(ownerToken, id, "{\"status\":\"ARCHIVED\"}"), "$.code"));
        //已归档不可再流转
        assertEquals(1006, (int) JsonPath.read(
                update(ownerToken, id, "{\"status\":\"ACTIVE\"}"), "$.code"));
        assertEquals("ARCHIVED", jdbcTemplate.queryForObject(
                "SELECT status FROM project WHERE id = ?", String.class, id));
    }

    @Test
    void staleUpdatedAtRejected() throws Exception {
        long id = createProject(ownerToken, "乐观锁项目", "ORG");
        //详情回显的 updatedAt 是前端编辑页要带回的值
        String updatedAt = JsonPath.read(detail(ownerToken, id), "$.data.updatedAt");
        //第一次带当前值编辑成功
        assertEquals(0, (int) JsonPath.read(update(ownerToken, id,
                "{\"description\":\"v2\",\"updatedAt\":\"" + updatedAt + "\"}"), "$.code"));
        //模拟别人已改过：直接把库里的 updated_at 拨到过去（DATETIME 秒级精度下，
        //同秒内连改两次值不前进，无法靠真实时钟构造冲突）
        jdbcTemplate.update("UPDATE project SET updated_at = '2020-01-01 00:00:00' WHERE id = ?", id);
        //再带旧值 → 1007（与当前值不一致=过期快照）
        assertEquals(1007, (int) JsonPath.read(update(ownerToken, id,
                "{\"description\":\"v3\",\"updatedAt\":\"" + updatedAt + "\"}"), "$.code"));
        assertEquals("v2", jdbcTemplate.queryForObject(
                "SELECT description FROM project WHERE id = ?", String.class, id));
    }

    // ---------------------------------------------------------------- 删除与级联

    @Test
    void deleteBlockedByActiveMountThenCascades() throws Exception {
        long id = createProject(ownerToken, "删除级联项目", "ORG");
        jdbcTemplate.update(
                "INSERT INTO project_member (id, project_id, member_id, member_name, project_role) "
                        + "VALUES (70006, ?, 80002, '项目B', 'MEMBER')", id);
        jdbcTemplate.update(
                "INSERT INTO project_task (id, project_id, title) VALUES (70007, ?, '任务1')", id);
        //ACTIVE 挂载拒绝删除 → 7010
        jdbcTemplate.update(
                "INSERT INTO team_project_ref (id, team_id, project_id, contest_id, semester_id, "
                        + "status, attached_by) VALUES (70008, 99999, ?, 88888, ?, 'ACTIVE', 80001)",
                id, SEMESTER);
        assertEquals(7010, (int) JsonPath.read(
                mockMvc.perform(withToken(delete("/api/projects/" + id), ownerToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(), "$.code"));
        //解绑（RELEASED）后可删，主体与子表全部逻辑删
        jdbcTemplate.update(
                "UPDATE team_project_ref SET status = 'RELEASED' WHERE id = 70008");
        assertEquals(0, (int) JsonPath.read(
                mockMvc.perform(withToken(delete("/api/projects/" + id), ownerToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(), "$.code"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT deleted FROM project WHERE id = ?", Integer.class, id));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT deleted FROM project_member WHERE id = 70006", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT deleted FROM project_task WHERE id = 70007", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT deleted FROM team_project_ref WHERE id = 70008", Integer.class));
        //删后详情 → 7001
        assertEquals(7001, detailCode(ownerToken, id));
    }

    // ---------------------------------------------------------------- 创建默认值

    @Test
    void createAppliesDefaults() throws Exception {
        String body = createProject(ownerToken, "{\"name\":\"默认值项目\"}");
        assertEquals(0, (int) JsonPath.read(body, "$.code"), body);
        long id = Long.parseLong(JsonPath.read(body, "$.data.id"));
        //默认：当前学期 / 本人组织 / ORG / ACTIVE / 所有人=memberId
        assertEquals(SEMESTER, jdbcTemplate.queryForObject(
                "SELECT semester_id FROM project WHERE id = ?", Long.class, id));
        assertEquals(ORG_SOFT, jdbcTemplate.queryForObject(
                "SELECT org_id FROM project WHERE id = ?", Long.class, id));
        assertEquals("ORG", jdbcTemplate.queryForObject(
                "SELECT visibility FROM project WHERE id = ?", String.class, id));
        assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                "SELECT status FROM project WHERE id = ?", String.class, id));
        assertEquals(80001L, jdbcTemplate.queryForObject(
                "SELECT owner_member_id FROM project WHERE id = ?", Long.class, id));
        //详情回显跨线名（组织/学期）
        String detailBody = detail(ownerToken, id);
        assertEquals("软件学院", JsonPath.read(detailBody, "$.data.orgName"));
        assertEquals("2026-2027学年第一学期-PM测试", JsonPath.read(detailBody, "$.data.semesterName"));
    }

    @Test
    void adminWithoutMemberProfileCannotCreate() throws Exception {
        //超管账号未绑定人员档案：项目是个人资产，必须落到具体人 → 参数错拦截
        String body = createProject(superToken, "{\"name\":\"无档案项目\"}");
        assertEquals(1001, (int) JsonPath.read(body, "$.code"), body);
    }

    @Test
    void deptAdminListCoversSubtree() throws Exception {
        //部门管理员列表：本子树全部（学生 A 的项目在 9301 子树内）
        createProject(ownerToken, "子树列表项目", "ORG");
        String list = list(deptSoftToken);
        assertEquals(0, (int) JsonPath.read(list, "$.code"));
        //total 同样按字符串序列化（全局 Long→String）
        assertTrue(Long.parseLong(JsonPath.read(list, "$.data.total")) >= 1,
                "部门管理员应看到本子树项目: " + list);
        //外部门管理员列表不含
        String mechList = list(deptMechToken);
        assertFalse(mechList.contains("子树列表项目"), "外部门管理员不应看到: " + mechList);
    }
}
