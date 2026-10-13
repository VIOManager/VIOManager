package com.viomanager.material;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.viomanager.approval.ApprovalTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 材料提交验证（MS02）：fileId 归属校验、文件表权威名/大小覆盖客户端自报、
 * 覆盖上传版本号递增、齐全率经 TeamMaterialStatusService 口子回写 team 表（B/C 清单 §6）。
 */
@TestPropertySource(properties = "vio.audit.async=false")
class MaterialSubmitTest extends ApprovalTestBase {

    private static final long TEAM_ID = 88011L;
    private static final long CONTEST_ID = 4900201L;
    private static final long TASK_ID = 5200201L;
    private static final long REQ_ID = 5300201L;
    private static final long OWN_FILE_ID = 6100201L;
    private static final long FOREIGN_FILE_ID = 6100202L;
    private static final long TXT_FILE_ID = 6100203L;
    private static final long ALIEN_FILE_ID = 6100204L;

    private String leaderToken;

    @BeforeEach
    void setUp() throws Exception {
        insertOrg(9311L, "材料学院", "/0/9311/");
        insertMember(8821L, "材料队长", 9311L);
        jdbcTemplate.update(
                "INSERT INTO team (id, name, contest_id, semester_id, org_id, leader_member_id, status) "
                        + "VALUES (?, ?, ?, 999, ?, ?, 'ACTIVE')",
                TEAM_ID, "材料测试队", CONTEST_ID, 9311L, 8821L);
        jdbcTemplate.update(
                "INSERT INTO team_member (id, team_id, member_id, member_name, member_org_id, is_leader, status, joined_at) "
                        + "VALUES (?, ?, ?, ?, 9311, 1, 'CONFIRMED', NOW())",
                System.nanoTime(), TEAM_ID, 8821L, "材料队长");
        jdbcTemplate.update(
                "INSERT INTO material_task (id, contest_id, semester_id, name) VALUES (?, ?, 999, '参赛材料')",
                TASK_ID, CONTEST_ID);
        jdbcTemplate.update(
                "INSERT INTO material_requirement (id, task_id, contest_id, material_type, name, "
                        + "required_flag, allowed_formats, max_size_mb) "
                        + "VALUES (?, ?, ?, 'REGISTRATION_FORM', '报名表', 1, 'pdf,jpg', 1)",
                REQ_ID, TASK_ID, CONTEST_ID);
        leaderToken = createUserAndLogin("ms_leader", "STUDENT", 9311L, 8821L);
        //三个文件：本人上传的 pdf（权威名与自报名故意不同）/ 他人上传的 / 本人上传的 txt（验白名单用权威名判定）
        insertFile(OWN_FILE_ID, "报名表权威名.pdf", 1024L, userIdOf("ms_leader"));
        insertFile(FOREIGN_FILE_ID, "别人的文件.pdf", 1024L, 999888L);
        insertFile(TXT_FILE_ID, "真名.txt", 1024L, userIdOf("ms_leader"));
    }

    @Test
    void submitOkAndRecomputeViaSeam() throws Exception {
        //客户端谎报名字与大小，落库必须是文件表权威值；唯一必传项交齐 → SUBMITTED 经口子回写
        submit(OWN_FILE_ID, "客户端假名.exe").andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.version").value(1));
        String fileName = jdbcTemplate.queryForObject(
                "SELECT file_name FROM material_submission WHERE requirement_id = ? AND is_current = 1",
                String.class, REQ_ID);
        Long fileSize = jdbcTemplate.queryForObject(
                "SELECT file_size FROM material_submission WHERE requirement_id = ? AND is_current = 1",
                Long.class, REQ_ID);
        assert "报名表权威名.pdf".equals(fileName) : "落库文件名应为文件表权威值，而非客户端自报";
        assert Long.valueOf(1024L).equals(fileSize) : "落库大小应为文件表权威值，而非客户端自报";
        //口子验证：齐全率与状态回写走了 TeamMaterialStatusService（1/1 必传项 → SUBMITTED 100%）
        String materialStatus = jdbcTemplate.queryForObject(
                "SELECT material_status FROM team WHERE id = ?", String.class, TEAM_ID);
        java.math.BigDecimal rate = jdbcTemplate.queryForObject(
                "SELECT material_complete_rate FROM team WHERE id = ?",
                java.math.BigDecimal.class, TEAM_ID);
        assert "SUBMITTED".equals(materialStatus) : "唯一必传项交齐，队伍材料状态应为 SUBMITTED";
        assert rate != null && rate.intValue() == 100 : "齐全率应为 100，实际 " + rate;
    }

    @Test
    void resubmitOverridesVersion() throws Exception {
        submit(OWN_FILE_ID, "第一版.pdf").andExpect(jsonPath("$.data.version").value(1));
        submit(OWN_FILE_ID, "第二版.pdf").andExpect(jsonPath("$.data.version").value(2));
        Integer currentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM material_submission WHERE requirement_id = ? AND is_current = 1",
                Integer.class, REQ_ID);
        assert currentCount != null && currentCount == 1 : "覆盖上传后同一要求只应有一条当前版本";
    }

    @Test
    void rejectsForeignFile() throws Exception {
        //拿别人的 fileId 挂名提交 → 1001（不存在与非本人上传统一文案，不给探测空间）
        submit(FOREIGN_FILE_ID, "别人的.pdf").andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void authoritativeNameGovernsWhitelist() throws Exception {
        //文件表真名是 txt：客户端谎报 .pdf 也过不了白名单（判定用的是权威名）
        submit(TXT_FILE_ID, "谎报.pdf").andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    void rejectsNonTeamMember() throws Exception {
        insertMember(8825L, "路人甲", 9311L);
        String alienToken = createUserAndLogin("ms_alien", "STUDENT", 9311L, 8825L);
        insertFile(ALIEN_FILE_ID, "路人的文件.pdf", 1024L, userIdOf("ms_alien"));
        mockMvc.perform(post("/api/materials/requirements/" + REQ_ID + "/submit")
                        .header("Authorization", "Bearer " + alienToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":" + TEAM_ID + ",\"fileId\":" + ALIEN_FILE_ID
                                + ",\"fileName\":\"路人的.pdf\",\"fileSize\":1024}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5003));
    }

    private ResultActions submit(long fileId, String fileName) throws Exception {
        return mockMvc.perform(post("/api/materials/requirements/" + REQ_ID + "/submit")
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":" + TEAM_ID + ",\"fileId\":" + fileId
                                + ",\"fileName\":\"" + fileName + "\",\"fileSize\":999999}"))
                .andExpect(status().isOk());
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
                        + "VALUES (?, ?, ?, ?, 'MATERIAL', 'CLEAN', ?)",
                id, "material/test/" + id + ".bin", originalName, size, createdBy);
    }
}
