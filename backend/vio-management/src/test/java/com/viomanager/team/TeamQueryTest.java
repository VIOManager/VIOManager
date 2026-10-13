package com.viomanager.team;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.viomanager.approval.ApprovalTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 赛队查询验证：TE01 列表数据范围 / TE02 详情 viewerRole / TE11 成员字段裁剪。
 */
class TeamQueryTest extends ApprovalTestBase {

    private static final long TEAM_ID = 88001L;
    private static final long CONTEST_ID = 4900101L;
    private static final long TRACK_ID = 4700101L;
    private static final long TEMPLATE_ID = 4800101L;

    private String superToken;
    private String deptAToken;
    private String deptBToken;
    private String leaderToken;
    private String memberToken;
    private String bystanderToken;

    @BeforeEach
    void setUp() throws Exception {
        insertOrg(9301L, "软件学院", "/0/9301/");
        insertOrg(9302L, "机械学院", "/0/9302/");
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
        insertMember(8811L, "张三", "STUDENT", 9301L, "软件学院", "软工1班", "13800000001", "0001");
        insertMember(8812L, "李四", "STUDENT", 9301L, "软件学院", "软工2班", "13800000002", "0002");
        insertMember(8813L, "王五", "STUDENT", 9302L, "机械学院", "机械1班", "13800000003", "0003");
        jdbcTemplate.update(
                "INSERT INTO team (id, name, contest_id, track_id, semester_id, org_id, leader_member_id, status) "
                        + "VALUES (?, ?, ?, ?, 999, ?, ?, 'ACTIVE')",
                TEAM_ID, "梦之队", CONTEST_ID, TRACK_ID, 9301L, 8811L);
        insertTeamMember(TEAM_ID, 8811L, "张三", 1, "CONFIRMED", 9301L);
        insertTeamMember(TEAM_ID, 8812L, "李四", 0, "CONFIRMED", 9301L);
        superToken = createUserAndLogin("tq_super", "SUPER_ADMIN", null, null);
        deptAToken = createUserAndLogin("tq_dept_a", "DEPT_ADMIN", 9301L, null);
        deptBToken = createUserAndLogin("tq_dept_b", "DEPT_ADMIN", 9302L, null);
        leaderToken = createUserAndLogin("tq_leader", "STUDENT", 9301L, 8811L);
        memberToken = createUserAndLogin("tq_member", "STUDENT", 9301L, 8812L);
        bystanderToken = createUserAndLogin("tq_bystander", "STUDENT", 9302L, 8813L);
    }

    @Test
    void listScoped() throws Exception {
        mockMvc.perform(get("/api/teams").header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("梦之队"));
        mockMvc.perform(get("/api/teams").header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/teams").header("Authorization", "Bearer " + deptBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/teams").header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void detailAndViewerRole() throws Exception {
        mockMvc.perform(get("/api/teams/" + TEAM_ID).header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewerRole").value("LEADER"))
                .andExpect(jsonPath("$.data.name").value("梦之队"))
                .andExpect(jsonPath("$.data.members.length()").value(2));
        mockMvc.perform(get("/api/teams/" + TEAM_ID).header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewerRole").value("MEMBER"));
        mockMvc.perform(get("/api/teams/" + TEAM_ID).header("Authorization", "Bearer " + bystanderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4008));
    }

    @Test
    void memberFieldMasking() throws Exception {
        // 队长看成员：能看到李四的手机号
        mockMvc.perform(get("/api/teams/" + TEAM_ID + "/members").header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].name").value("李四"))
                .andExpect(jsonPath("$.data[1].phone").value("13800000002"));
        // 队员看成员：队长的手机号被裁剪（null），本人手机号可见
        mockMvc.perform(get("/api/teams/" + TEAM_ID + "/members").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("张三"))
                .andExpect(jsonPath("$.data[0].phone").isEmpty())
                .andExpect(jsonPath("$.data[1].name").value("李四"))
                .andExpect(jsonPath("$.data[1].phone").value("13800000002"));
    }

    private void insertMember(long id, String name, String type, long orgId,
                              String college, String className, String phone, String idCardLast4) {
        jdbcTemplate.update(
                "INSERT INTO member (id, name, org_id, member_type, college, class_name, phone, id_card_last4) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, name, orgId, type, college, className, phone, idCardLast4);
    }

    private void insertTeamMember(long teamId, long memberId, String name, int isLeader, String status, long orgId) {
        jdbcTemplate.update(
                "INSERT INTO team_member (id, team_id, member_id, member_name, member_org_id, is_leader, status, joined_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())",
                idSeq(), teamId, memberId, name, orgId, isLeader, status);
    }

    private long idSeq() {
        return System.nanoTime();
    }
}
