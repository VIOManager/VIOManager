package com.viomanager.achievement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.viomanager.approval.ApprovalTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * 成果流验证（A03/A06）：管理员直录 APPROVED + §5 联动追加系统生成佐证要求、
 * 学生提交走 ACHIEVEMENT_SUBMIT 审批、evidenceFileId 归属校验（1001）、
 * A06 佐证覆盖上传以文件表权威名落库。
 */
@TestPropertySource(properties = "vio.audit.async=false")
class AchievementFlowTest extends ApprovalTestBase {

    private static final long TEAM_ID = 88011L;
    private static final long CONTEST_ID = 4900201L;
    private static final long TASK_ID = 5200201L;
    private static final long ACH_ID = 7000201L;
    private static final long OWN_FILE_ID = 6100301L;
    private static final long FOREIGN_FILE_ID = 6100302L;

    private String leaderToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        insertOrg(9311L, "成果学院", "/0/9311/");
        insertMember(8821L, "成果队长", 9311L);
        //管理员也要绑人员档案：owner_member_id 列非空，未绑定的管理账号无法作为成果归属人（1001）
        insertMember(8826L, "管理端录入人", 9311L);
        jdbcTemplate.update(
                "INSERT INTO team (id, name, contest_id, semester_id, org_id, leader_member_id, status) "
                        + "VALUES (?, ?, ?, 999, ?, ?, 'ACTIVE')",
                TEAM_ID, "成果测试队", CONTEST_ID, 9311L, 8821L);
        jdbcTemplate.update(
                "INSERT INTO team_member (id, team_id, member_id, member_name, member_org_id, is_leader, status, joined_at) "
                        + "VALUES (?, ?, ?, ?, 9311, 1, 'CONFIRMED', NOW())",
                System.nanoTime(), TEAM_ID, 8821L, "成果队长");
        jdbcTemplate.update(
                "INSERT INTO material_task (id, contest_id, semester_id, name) VALUES (?, ?, 999, '参赛材料')",
                TASK_ID, CONTEST_ID);
        //直接落一条 PENDING 成果供 A06 佐证上传用（owner 是队长 8821）
        jdbcTemplate.update(
                "INSERT INTO achievement (id, team_id, contest_id, semester_id, org_id, owner_member_id, "
                        + "achievement_type, name, status) VALUES (?, ?, ?, 999, ?, ?, 'AWARD', '旧成果', 'PENDING')",
                ACH_ID, TEAM_ID, CONTEST_ID, 9311L, 8821L);
        leaderToken = createUserAndLogin("ach_leader", "STUDENT", 9311L, 8821L);
        adminToken = createUserAndLogin("ach_admin", "SUPER_ADMIN", 9311L, 8826L);
        insertFile(OWN_FILE_ID, "获奖证书权威名.pdf", 2048L, userIdOf("ach_leader"));
        insertFile(FOREIGN_FILE_ID, "别人的证书.pdf", 2048L, 999777L);
    }

    @Test
    void adminCreateApprovedWithLinkage() throws Exception {
        //管理员直录：APPROVED 生效，且 §5 联动给该队任务追加系统生成的 EVIDENCE 必传项
        mockMvc.perform(post("/api/achievements")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":" + TEAM_ID + ",\"type\":\"AWARD\",\"name\":\"省一等奖\","
                                + "\"level\":\"省级\",\"achievedAt\":\"2026-09-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        Integer evidenceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM material_requirement WHERE task_id = ? AND material_type = 'EVIDENCE' "
                        + "AND system_generated = 1 AND deleted = 0",
                Integer.class, TASK_ID);
        assert evidenceCount != null && evidenceCount == 1 : "管理员直录通过后应追加系统生成佐证要求";
    }

    @Test
    void studentCreateGoesApproval() throws Exception {
        //学生队长提交：PENDING + 生成 ACHIEVEMENT_SUBMIT 审批单
        mockMvc.perform(post("/api/achievements")
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":" + TEAM_ID + ",\"type\":\"AWARD\",\"name\":\"校赛二等奖\","
                                + "\"achievedAt\":\"2026-10-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval WHERE biz_type = 'ACHIEVEMENT_SUBMIT' "
                        + "AND status = 'PENDING' AND deleted = 0",
                Integer.class);
        assert count != null && count > 0 : "学生提交应生成 ACHIEVEMENT_SUBMIT 审批单";
    }

    @Test
    void createRejectsForeignEvidenceFile() throws Exception {
        //A03 带他人上传的 evidenceFileId → 1001，且不得生成审批单
        mockMvc.perform(post("/api/achievements")
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":" + TEAM_ID + ",\"type\":\"AWARD\",\"name\":\"假佐证成果\","
                                + "\"achievedAt\":\"2026-10-01\",\"evidenceFileId\":" + FOREIGN_FILE_ID
                                + ",\"evidenceFileName\":\"x.pdf\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM achievement WHERE name = '假佐证成果' AND deleted = 0", Integer.class);
        assert count != null && count == 0 : "归属校验失败的成果不应落库";
    }

    @Test
    void uploadEvidenceRejectsForeignFile() throws Exception {
        //A06 拿别人的 fileId 传佐证 → 1001
        mockMvc.perform(post("/api/achievements/" + ACH_ID + "/evidence")
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":" + FOREIGN_FILE_ID + ",\"fileName\":\"x.pdf\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void uploadEvidenceOk() throws Exception {
        //A06 正常上传：客户端谎报 .exe，落库与 achievement 两列都必须是文件表权威名
        mockMvc.perform(post("/api/achievements/" + ACH_ID + "/evidence")
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":" + OWN_FILE_ID + ",\"fileName\":\"客户端假名.exe\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        String evidenceName = jdbcTemplate.queryForObject(
                "SELECT evidence_file_name FROM achievement WHERE id = ?", String.class, ACH_ID);
        String fileName = jdbcTemplate.queryForObject(
                "SELECT file_name FROM achievement_file WHERE achievement_id = ? AND is_current = 1",
                String.class, ACH_ID);
        assert "获奖证书权威名.pdf".equals(evidenceName) : "achievement 佐证名应为文件表权威值";
        assert "获奖证书权威名.pdf".equals(fileName) : "achievement_file 佐证名应为文件表权威值";
    }

    private Long userIdOf(String loginName) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE login_name = ?", Long.class, loginName);
    }

    private void insertMember(long id, String name, long orgId) {
        jdbcTemplate.update(
                "INSERT INTO member (id, name, org_id, member_type) VALUES (?, ?, ?, 'STUDENT')",
                id, name, orgId);
    }

    private void insertFile(long id, String originalName, long size, Long createdBy) {
        jdbcTemplate.update(
                "INSERT INTO file_object (id, object_key, original_name, file_size, biz_type, scan_status, created_by) "
                        + "VALUES (?, ?, ?, ?, 'ACHIEVEMENT', 'CLEAN', ?)",
                id, "achievement/test/" + id + ".bin", originalName, size, createdBy);
    }
}
