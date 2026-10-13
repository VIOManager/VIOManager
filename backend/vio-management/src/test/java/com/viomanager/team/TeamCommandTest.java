package com.viomanager.team;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.viomanager.approval.ApprovalTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * 赛队写操作验证：TE03 组队申请→审批单 / TE06 队员确认 / TE08 退出 / TE12 解散(4009) / TE13 编辑。
 */
@TestPropertySource(properties = "vio.audit.async=false")
class TeamCommandTest extends ApprovalTestBase {

    private static final long TEAM_ID = 88001L;
    private static final long CONTEST_ID = 4900101L;
    private static final long TRACK_ID = 4700101L;
    private static final long TEMPLATE_ID = 4800101L;

    private String leaderToken;
    private String memberToken;

    @BeforeEach
    void setUp() throws Exception {
        insertOrg(9301L, "软件学院", "/0/9301/");
        jdbcTemplate.update(
                "INSERT INTO contest_template (id, name, code, category, default_rules, status, sort_no, deleted) "
                        + "VALUES (?, ?, ?, ?, ?, 1, 0, 0)",
                TEMPLATE_ID, "电控模板", "DK", "电控",
                "{\"memberLimit\":5,\"prefillLimit\":2,\"allowRepeatJoin\":false,\"crossTeamLeader\":false,\"maxAdvisorCount\":2}");
        jdbcTemplate.update(
                "INSERT INTO contest (id, name, template_id, semester_id, org_id, status, source_type, deleted) "
                        + "VALUES (?, ?, ?, 999, NULL, 'REGISTERING', 'MANUAL', 0)",
                CONTEST_ID, "校电控赛", TEMPLATE_ID);
        jdbcTemplate.update(
                "INSERT INTO contest_track (id, contest_id, name, code, sort_no, status, deleted) "
                        + "VALUES (?, ?, ?, ?, 0, 1, 0)", TRACK_ID, CONTEST_ID, "硬件赛道", "HW");
        insertMember(8811L, "张三", "STUDENT", 9301L);
        insertMember(8812L, "李四", "STUDENT", 9301L);
        insertMember(8815L, "钱七", "STUDENT", 9301L);
        jdbcTemplate.update(
                "INSERT INTO team (id, name, contest_id, track_id, semester_id, org_id, leader_member_id, status) "
                        + "VALUES (?, ?, ?, ?, 999, ?, ?, 'ACTIVE')",
                TEAM_ID, "梦之队", CONTEST_ID, TRACK_ID, 9301L, 8811L);
        insertTeamMember(TEAM_ID, 8811L, "张三", 1, "CONFIRMED");
        insertTeamMember(TEAM_ID, 8812L, "李四", 0, "CONFIRMED");
        insertTeamMember(TEAM_ID, 8815L, "钱七", 0, "UNCONFIRMED");
        leaderToken = createUserAndLogin("tc_leader", "STUDENT", 9301L, 8811L);
        memberToken = createUserAndLogin("tc_member", "STUDENT", 9301L, 8812L);
    }

    @Test
    void createTeamGeneratesApproval() throws Exception {
        // 8813 是另一个学院的学生，未在任何队，可以建队
        insertOrg(9302L, "机械学院", "/0/9302/");
        insertMember(8813L, "王五", "STUDENT", 9302L);
        String token = createUserAndLogin("tc_creator", "STUDENT", 9302L, 8813L);
        mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Idempotency-Key", "tc-create-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contestId\":\"" + CONTEST_ID + "\",\"trackId\":\"" + TRACK_ID + "\","
                                + "\"name\":\"航天队\",\"projectName\":\"火星车\",\"prefillMemberIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.approvalId").exists());
        // 验证审批单已生成
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval WHERE biz_type = 'TEAM_CREATE' AND status = 'PENDING' AND deleted = 0",
                Integer.class);
        assert count != null && count > 0 : "应生成 TEAM_CREATE 审批单";
    }

    @Test
    void confirmMember() throws Exception {
        // 钱七(8815)本人确认加入
        mockMvc.perform(post("/api/teams/" + TEAM_ID + "/members/8815/confirm")
                        .header("Authorization", "Bearer " + createUserAndLogin("tc_qian", "STUDENT", 9301L, 8815L))
                        .header("X-Idempotency-Key", "tc-confirm-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"CONFIRM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM team_member WHERE team_id = ? AND member_id = 8815 AND deleted = 0",
                String.class, TEAM_ID);
        assert "CONFIRMED".equals(status) : "确认后状态应为 CONFIRMED";
    }

    @Test
    void exitTeam() throws Exception {
        mockMvc.perform(post("/api/teams/" + TEAM_ID + "/exit")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("X-Idempotency-Key", "tc-exit-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM team_member WHERE team_id = ? AND member_id = 8812 AND deleted = 0",
                String.class, TEAM_ID);
        assert "EXITED".equals(status) : "退出后状态应为 EXITED";
    }

    @Test
    void dissolveBlockedByMaterial() throws Exception {
        // 插一条材料 → 解散应 4009
        jdbcTemplate.update(
                "INSERT INTO material_submission (id, requirement_id, task_id, contest_id, team_id, "
                        + "semester_id, org_id, file_id, file_name) VALUES (?, ?, ?, ?, ?, 999, ?, ?, ?)",
                System.nanoTime(), 50001L, 51001L, CONTEST_ID, TEAM_ID, 9301L, 60001L, "报告.pdf");
        mockMvc.perform(post("/api/teams/" + TEAM_ID + "/dissolve")
                        .header("Authorization", "Bearer " + leaderToken)
                        .header("X-Idempotency-Key", "tc-dissolve-blocked-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"解散\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4009));
    }

    @Test
    void dissolveOk() throws Exception {
        mockMvc.perform(post("/api/teams/" + TEAM_ID + "/dissolve")
                        .header("Authorization", "Bearer " + leaderToken)
                        .header("X-Idempotency-Key", "tc-dissolve-ok-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"解散\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        String teamStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM team WHERE id = ?", String.class, TEAM_ID);
        assert "DISSOLVED".equals(teamStatus) : "解散后赛队状态应为 DISSOLVED";
    }

    @Test
    void updateTeam() throws Exception {
        mockMvc.perform(put("/api/teams/" + TEAM_ID)
                        .header("Authorization", "Bearer " + leaderToken)
                        .header("X-Idempotency-Key", "tc-update-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"梦之队改\",\"projectName\":\"智能小车V2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        String name = jdbcTemplate.queryForObject(
                "SELECT name FROM team WHERE id = ?", String.class, TEAM_ID);
        assert "梦之队改".equals(name) : "队名应已更新";
    }

    private void insertMember(long id, String name, String type, long orgId) {
        jdbcTemplate.update(
                "INSERT INTO member (id, name, org_id, member_type) VALUES (?, ?, ?, ?)",
                id, name, orgId, type);
    }

    private void insertTeamMember(long teamId, long memberId, String name, int isLeader, String status) {
        jdbcTemplate.update(
                "INSERT INTO team_member (id, team_id, member_id, member_name, member_org_id, is_leader, status, joined_at) "
                        + "VALUES (?, ?, ?, ?, 9301, ?, ?, NOW())",
                System.nanoTime(), teamId, memberId, name, isLeader, status);
    }
}
