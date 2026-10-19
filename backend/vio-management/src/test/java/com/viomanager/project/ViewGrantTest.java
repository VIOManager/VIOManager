package com.viomanager.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

/**
 * M6 查看申请与授权测试（PR29–PR31 + ONCE 消耗）：
 * 申请→审批→ACTIVE 全链路（真实审批）、重复提单 7011、ONCE 详情消耗（用完 EXPIRED 再看 7003）、
 * 靠身份可见者不消耗授权、LIMITED 不消耗且到期失效、驳回置失效、撤销与越权。
 */
@Transactional
class ViewGrantTest extends PmTestSupport {

    private static final Long ORG_SOFT = 9301L;
    private static final Long ORG_MECH = 9302L;
    private static final Long SEMESTER = 95001L;

    private String ownerToken;
    private String outsiderToken;
    private String sameOrgStudentToken;
    private String superToken;
    private long projectId;

    @BeforeEach
    void setUp() throws Exception {
        insertOrg(ORG_SOFT, "软件学院", "/0/9301/");
        insertOrg(ORG_MECH, "机械学院", "/0/9302/");
        insertCurrentSemester(SEMESTER, "2026-2027学年第一学期-PM授权测试");
        insertMember(80001L, "项目A", ORG_SOFT, "STUDENT");
        insertMember(80002L, "外人B", ORG_MECH, "STUDENT");
        insertMember(80003L, "同门C", ORG_SOFT, "STUDENT");
        ownerToken = createUserAndLogin("pm7_owner", "STUDENT", ORG_SOFT, 80001L);
        outsiderToken = createUserAndLogin("pm7_outsider", "STUDENT", ORG_MECH, 80002L);
        sameOrgStudentToken = createUserAndLogin("pm7_same_org", "STUDENT", ORG_SOFT, 80003L);
        superToken = createAdminAndLogin("pm7_super", "SUPER_ADMIN");
        //PRIVATE 项目：只有授权能看的外部门学生视角最干净
        String body = createProject(ownerToken, "{\"name\":\"授权验证项目\",\"visibility\":\"PRIVATE\"}");
        assertEquals(0, (int) JsonPath.read(body, "$.code"), body);
        projectId = Long.parseLong(JsonPath.read(body, "$.data.id"));
    }

    /** 提交申请，返回 grantId */
    private long requestView(String token, String grantType, String expireAt) throws Exception {
        String body = mockMvc.perform(withToken(
                        post("/api/projects/" + projectId + "/view-requests"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grantType\":\"" + grantType + "\""
                                + (expireAt == null ? "" : ",\"expireAt\":\"" + expireAt + "\"") + "}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertEquals(0, (int) JsonPath.read(body, "$.code"), body);
        return Long.parseLong(JsonPath.read(body, "$.data"));
    }

    /** 走真实审批链路批准该 grant 的审批单 */
    private void approveGrant(String token, long grantId, String key) throws Exception {
        long approvalId = jdbcTemplate.queryForObject(
                "SELECT id FROM approval WHERE biz_type = 'PROJECT_VIEW' AND biz_id = ? "
                        + "AND deleted = 0 ORDER BY id DESC LIMIT 1", Long.class, grantId);
        assertEquals(0, (int) JsonPath.read(mockMvc.perform(withToken(
                        post("/api/approvals/" + approvalId + "/approve"), token)
                        .header("X-Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"同意\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
    }

    private int detailCode(String token) throws Exception {
        return (int) JsonPath.read(mockMvc.perform(withToken(
                        get("/api/projects/" + projectId), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code");
    }

    // ---------------------------------------------------------------- 申请与 ONCE 消耗

    @Test
    void onceGrantConsumedOnDetail() throws Exception {
        //申请 → PENDING；重复提单 7011
        long grantId = requestView(outsiderToken, "ONCE", null);
        assertEquals("PENDING", jdbcTemplate.queryForObject(
                "SELECT status FROM project_view_grant WHERE id = ?", String.class, grantId));
        assertEquals(7011, (int) JsonPath.read(mockMvc.perform(withToken(
                        post("/api/projects/" + projectId + "/view-requests"), outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grantType\":\"ONCE\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        //审批通过 → ACTIVE → 详情可见
        approveGrant(superToken, grantId, "pm7-appr-1");
        assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                "SELECT status FROM project_view_grant WHERE id = ?", String.class, grantId));
        assertEquals(0, detailCode(outsiderToken));
        //ONCE 消耗：used_count=1 且置 EXPIRED，再看 7003
        assertEquals("EXPIRED", jdbcTemplate.queryForObject(
                "SELECT status FROM project_view_grant WHERE id = ?", String.class, grantId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT used_count FROM project_view_grant WHERE id = ?", Integer.class, grantId));
        assertEquals(7003, detailCode(outsiderToken));
    }

    @Test
    void visibleViewerDoesNotConsumeGrant() throws Exception {
        //同部门学生本来就看不到 PRIVATE——换 PUBLIC 项目验证"身份可见不消耗"
        String pubBody = createProject(ownerToken, "{\"name\":\"公开消耗验证\",\"visibility\":\"PUBLIC\"}");
        long pubId = Long.parseLong(JsonPath.read(pubBody, "$.data.id"));
        //外部门学生对 PUBLIC 可见（规则 5），同时给他一张该项目 ONCE 授权
        jdbcTemplate.update("INSERT INTO project_view_grant (id, project_id, applicant_id, "
                        + "applicant_name, grant_type, used_count, status) "
                        + "VALUES (70001, ?, 80002, '外人B', 'ONCE', 0, 'ACTIVE')", pubId);
        assertEquals(0, (int) JsonPath.read(mockMvc.perform(withToken(
                        get("/api/projects/" + pubId), outsiderToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        //靠规则 5 可见 → 授权不消耗
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT used_count FROM project_view_grant WHERE id = 70001", Integer.class));
        assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                "SELECT status FROM project_view_grant WHERE id = 70001", String.class));
    }

    @Test
    void limitedGrantNotConsumedAndExpires() throws Exception {
        long grantId = requestView(outsiderToken, "LIMITED", "2099-01-01 00:00:00");
        approveGrant(superToken, grantId, "pm7-appr-2");
        //LIMITED 浏览不消耗：多次详情 used_count 恒 0
        assertEquals(0, detailCode(outsiderToken));
        assertEquals(0, detailCode(outsiderToken));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT used_count FROM project_view_grant WHERE id = ?", Integer.class, grantId));
        //到期后失效：拨 expire_at 到过去 → 7003
        jdbcTemplate.update("UPDATE project_view_grant SET expire_at = '2020-01-01 00:00:00' "
                + "WHERE id = ?", grantId);
        assertEquals(7003, detailCode(outsiderToken));
        //LIMITED 不带 expireAt → 1001
        assertEquals(1001, (int) JsonPath.read(mockMvc.perform(withToken(
                        post("/api/projects/" + projectId + "/view-requests"), outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grantType\":\"LIMITED\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
    }

    @Test
    void rejectFlowExpiresGrant() throws Exception {
        long grantId = requestView(outsiderToken, "ONCE", null);
        long approvalId = jdbcTemplate.queryForObject(
                "SELECT id FROM approval WHERE biz_type = 'PROJECT_VIEW' AND biz_id = ? "
                        + "AND deleted = 0 ORDER BY id DESC LIMIT 1", Long.class, grantId);
        assertEquals(0, (int) JsonPath.read(mockMvc.perform(withToken(
                        post("/api/approvals/" + approvalId + "/reject"), superToken)
                        .header("X-Idempotency-Key", "pm7-rej-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"不外传\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        //驳回 → 置失效，不可见
        assertEquals("EXPIRED", jdbcTemplate.queryForObject(
                "SELECT status FROM project_view_grant WHERE id = ?", String.class, grantId));
        assertEquals(7003, detailCode(outsiderToken));
    }

    // ---------------------------------------------------------------- PR30/PR31 列表与撤销

    @Test
    void grantListAndRevokePermissions() throws Exception {
        long grantId = requestView(outsiderToken, "ONCE", null);
        approveGrant(superToken, grantId, "pm7-appr-3");
        //PR30：所有人可看授权列表；申请人（学生）不可看 → 1003
        String ownerView = mockMvc.perform(withToken(
                        get("/api/projects/" + projectId + "/view-grants"), ownerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertEquals(0, (int) JsonPath.read(ownerView, "$.code"));
        assertEquals(1, ((java.util.List<?>) JsonPath.read(ownerView, "$.data[*]")).size());
        assertEquals("外人B", JsonPath.read(ownerView, "$.data[0].applicantName"));
        assertEquals(1003, (int) JsonPath.read(mockMvc.perform(withToken(
                        get("/api/projects/" + projectId + "/view-grants"), outsiderToken))
                .andExpect(status().isForbidden())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        //PR31：申请人不能撤（1003）；所有人撤销 → REVOKED → 详情 7003
        assertEquals(1003, (int) JsonPath.read(mockMvc.perform(withToken(
                        put("/api/view-grants/" + grantId + "/revoke"), outsiderToken))
                .andExpect(status().isForbidden())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        assertEquals(0, (int) JsonPath.read(mockMvc.perform(withToken(
                        put("/api/view-grants/" + grantId + "/revoke"), ownerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        assertEquals("REVOKED", jdbcTemplate.queryForObject(
                "SELECT status FROM project_view_grant WHERE id = ?", String.class, grantId));
        assertEquals(7003, detailCode(outsiderToken));
        //终态再撤 → 1006
        assertEquals(1006, (int) JsonPath.read(mockMvc.perform(withToken(
                        put("/api/view-grants/" + grantId + "/revoke"), ownerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
    }
}
