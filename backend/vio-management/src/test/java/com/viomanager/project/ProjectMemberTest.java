package com.viomanager.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;

/**
 * M2 成员与指导教师测试（PR06–PR10）：OWNER 行生成与保护、加人防重 7005、
 * 教师类型校验 7006、越权 7002、可见者可看成员列表、移除后可重加（uk 含 deleted）。
 */
@Transactional
class ProjectMemberTest extends PmTestSupport {

    private static final Long ORG_SOFT = 9301L;
    private static final Long ORG_MECH = 9302L;
    private static final Long SEMESTER = 95001L;

    private String ownerToken;
    private String memberToken;
    private String outsiderToken;
    private String teacherToken;

    @BeforeEach
    void setUp() throws Exception {
        insertOrg(ORG_SOFT, "软件学院", "/0/9301/");
        insertOrg(ORG_MECH, "机械学院", "/0/9302/");
        insertCurrentSemester(SEMESTER, "2026-2027学年第一学期-PM成员测试");
        insertMember(80001L, "项目A", ORG_SOFT, "STUDENT");
        insertMember(80002L, "项目B", ORG_MECH, "STUDENT");
        insertMember(80003L, "项目C", ORG_SOFT, "STUDENT");
        insertMember(80004L, "教师T", ORG_MECH, "TEACHER");
        ownerToken = createUserAndLogin("pm2_owner", "STUDENT", ORG_SOFT, 80001L);
        memberToken = createUserAndLogin("pm2_member_b", "STUDENT", ORG_MECH, 80002L);
        outsiderToken = createUserAndLogin("pm2_outsider_c", "STUDENT", ORG_SOFT, 80003L);
        teacherToken = createUserAndLogin("pm2_teacher", "STUDENT", ORG_MECH, 80004L);
    }

    private long newProject(String token, String name) throws Exception {
        String body = createProject(token, "{\"name\":\"" + name + "\",\"visibility\":\"ORG\"}");
        assertEquals(0, (int) JsonPath.read(body, "$.code"), body);
        return Long.parseLong(JsonPath.read(body, "$.data.id"));
    }

    /** POST /api/projects/{id}/members 或 /advisors，返回 body */
    private String postJson(String url, String token, String body) throws Exception {
        return mockMvc.perform(withToken(post(url), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String deleteReq(String url, String token) throws Exception {
        return mockMvc.perform(withToken(delete(url), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String members(String token, long id) throws Exception {
        return mockMvc.perform(withToken(get("/api/projects/" + id + "/members"), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    // ---------------------------------------------------------------- OWNER 行与成员列表

    @Test
    void createInsertsOwnerRow() throws Exception {
        long id = newProject(ownerToken, "OWNER行验证项目");
        //建项目即生成 OWNER 行（PR06 列表锚点、PR08 保护前提）
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project_member WHERE project_id = ? AND member_id = 80001 "
                        + "AND project_role = 'OWNER' AND deleted = 0",
                Integer.class, id));
        //列表回显：角色 OWNER + 姓名
        String body = members(ownerToken, id);
        assertEquals(0, (int) JsonPath.read(body, "$.code"));
        List<String> roles = JsonPath.read(body, "$.data[?(@.memberId == '80001')].projectRole");
        assertEquals(1, roles.size(), body);
        assertEquals("OWNER", roles.get(0));
    }

    @Test
    void ownerAddsMemberThenBothSeeList() throws Exception {
        long id = newProject(ownerToken, "加成员验证项目");
        //PR07 添加外部门学生（不走审批）
        assertEquals(0, (int) JsonPath.read(
                postJson("/api/projects/" + id + "/members", ownerToken, "{\"memberId\":80002}"),
                "$.code"));
        //PR06：所有人与成员本人都能看（可见性走八条规则——成员即参与人）
        String ownerView = members(ownerToken, id);
        String memberView = members(memberToken, id);
        assertEquals(2, ((List<?>) JsonPath.read(ownerView, "$.data[*]")).size(), ownerView);
        assertTrue(((List<?>) JsonPath.read(memberView, "$.data[*]")).size() == 2, memberView);
    }

    @Test
    void outsiderCannotSeeMemberListOfOrgProject() throws Exception {
        long id = newProject(ownerToken, "不可见成员列表项目");
        //外部门学生：项目本身不可见（ORG）→ 成员列表同样 7003（读口先过可见性）
        String body = members(memberToken, id);
        assertEquals(7003, (int) JsonPath.read(body, "$.code"), body);
    }

    // ---------------------------------------------------------------- 防重与越权

    @Test
    void duplicateMemberRejected() throws Exception {
        long id = newProject(ownerToken, "防重成员项目");
        assertEquals(0, (int) JsonPath.read(
                postJson("/api/projects/" + id + "/members", ownerToken, "{\"memberId\":80002}"),
                "$.code"));
        //再加同一人 → 7005
        assertEquals(7005, (int) JsonPath.read(
                postJson("/api/projects/" + id + "/members", ownerToken, "{\"memberId\":80002}"),
                "$.code"));
        //人员不存在 → 1004
        assertEquals(1004, (int) JsonPath.read(
                postJson("/api/projects/" + id + "/members", ownerToken, "{\"memberId\":99999}"),
                "$.code"));
    }

    @Test
    void nonOwnerCannotManageMembers() throws Exception {
        long id = newProject(ownerToken, "越权成员项目");
        //同部门学生（项目可见）加人 → 7002
        assertEquals(7002, (int) JsonPath.read(
                postJson("/api/projects/" + id + "/members", outsiderToken, "{\"memberId\":80002}"),
                "$.code"));
        //成员本人也不能加人（只读参与权）
        assertEquals(0, (int) JsonPath.read(
                postJson("/api/projects/" + id + "/members", ownerToken, "{\"memberId\":80002}"),
                "$.code"));
        assertEquals(7002, (int) JsonPath.read(
                postJson("/api/projects/" + id + "/members", memberToken, "{\"memberId\":80003}"),
                "$.code"));
    }

    @Test
    void removeMemberAndOwnerRowProtected() throws Exception {
        long id = newProject(ownerToken, "移除成员项目");
        assertEquals(0, (int) JsonPath.read(
                postJson("/api/projects/" + id + "/members", ownerToken, "{\"memberId\":80002}"),
                "$.code"));
        //PR08 删普通成员成功，列表回到仅 OWNER
        assertEquals(0, (int) JsonPath.read(
                deleteReq("/api/projects/" + id + "/members/80002", ownerToken), "$.code"));
        assertEquals(1, ((List<?>) JsonPath.read(members(ownerToken, id), "$.data[*]")).size());
        //删了可重加（uk 含 deleted，逻辑删后不撞键）
        assertEquals(0, (int) JsonPath.read(
                postJson("/api/projects/" + id + "/members", ownerToken, "{\"memberId\":80002}"),
                "$.code"));
        //OWNER 行不可移除（7002）：所有人不能自我摘除
        assertEquals(7002, (int) JsonPath.read(
                deleteReq("/api/projects/" + id + "/members/80001", ownerToken), "$.code"));
        //移除不存在的成员 → 1004
        assertEquals(1004, (int) JsonPath.read(
                deleteReq("/api/projects/" + id + "/members/80003", ownerToken), "$.code"));
    }

    // ---------------------------------------------------------------- 指导教师

    @Test
    void advisorMustBeTeacherType() throws Exception {
        long id = newProject(ownerToken, "教师类型验证项目");
        //学生类型人员挂教师 → 7006
        assertEquals(7006, (int) JsonPath.read(
                postJson("/api/projects/" + id + "/advisors", ownerToken,
                        "{\"memberId\":80002,\"title\":\"学生冒充\"}"),
                "$.code"));
        //不存在的人 → 1004（与加成员口径一致：被引用的人员档案不存在）
        assertEquals(1004, (int) JsonPath.read(
                postJson("/api/projects/" + id + "/advisors", ownerToken, "{\"memberId\":99999}"),
                "$.code"));
        //真正的教师成功（title 可选带）
        assertEquals(0, (int) JsonPath.read(
                postJson("/api/projects/" + id + "/advisors", ownerToken,
                        "{\"memberId\":80004,\"title\":\"副教授\"}"),
                "$.code"));
        //教师可见项目（规则2）并可看成员列表
        String teacherView = members(teacherToken, id);
        assertEquals(0, (int) JsonPath.read(teacherView, "$.code"), teacherView);
        //重复挂名 → 7005
        assertEquals(7005, (int) JsonPath.read(
                postJson("/api/projects/" + id + "/advisors", ownerToken, "{\"memberId\":80004}"),
                "$.code"));
    }

    @Test
    void removeAdvisorThenRowLogicallyDeleted() throws Exception {
        long id = newProject(ownerToken, "移除教师项目");
        assertEquals(0, (int) JsonPath.read(
                postJson("/api/projects/" + id + "/advisors", ownerToken, "{\"memberId\":80004}"),
                "$.code"));
        //非所有人移除教师 → 7002
        assertEquals(7002, (int) JsonPath.read(
                deleteReq("/api/projects/" + id + "/advisors/80004", teacherToken), "$.code"));
        //所有人移除成功：行逻辑删，教师随之不可见（7003）
        assertEquals(0, (int) JsonPath.read(
                deleteReq("/api/projects/" + id + "/advisors/80004", ownerToken), "$.code"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT deleted FROM project_advisor WHERE project_id = ? AND member_id = 80004",
                Integer.class, id));
        assertEquals(7003, (int) JsonPath.read(members(teacherToken, id), "$.code"));
        //移除不存在的教师 → 1004
        assertEquals(1004, (int) JsonPath.read(
                deleteReq("/api/projects/" + id + "/advisors/80004", ownerToken), "$.code"));
    }
}
