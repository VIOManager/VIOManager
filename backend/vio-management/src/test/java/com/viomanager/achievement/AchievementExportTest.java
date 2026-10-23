package com.viomanager.achievement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.viomanager.approval.ApprovalTestBase;
import java.io.ByteArrayInputStream;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 获奖导出验证（A07）：学生只导本人、范围内存在未通过成果 5008（宁拒不漏）、
 * 管理员全量、无可导出成果 1004；xlsx 用 POI 逐行断言。
 */
@TestPropertySource(properties = "vio.audit.async=false")
class AchievementExportTest extends ApprovalTestBase {

    private static final long TEAM_ID = 88013L;
    private static final long CONTEST_ID = 4900203L;

    private String ownerToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        insertOrg(9311L, "导出学院", "/0/9311/");
        insertMember(8821L, "成果归属人", 9311L);
        insertMember(8822L, "另一位归属人", 9311L);
        jdbcTemplate.update(
                "INSERT INTO team (id, name, contest_id, semester_id, org_id, leader_member_id, status) "
                        + "VALUES (?, ?, ?, 999, ?, ?, 'ACTIVE')",
                TEAM_ID, "导出测试队", CONTEST_ID, 9311L, 8821L);
        ownerToken = createUserAndLogin("aet_owner", "STUDENT", 9311L, 8821L);
        adminToken = createUserAndLogin("aet_admin", "SUPER_ADMIN", 9311L, null);
    }

    @Test
    void studentBlockedByPendingAchievement() throws Exception {
        //本人名下一条 APPROVED 一条 PENDING：范围内存在未通过成果 → 5008
        insertAchievement(7000301L, 8821L, "已通过的成果", "APPROVED");
        insertAchievement(7000302L, 8821L, "待审批的成果", "PENDING");
        export(ownerToken).andExpect(jsonPath("$.code").value(5008));
    }

    @Test
    void studentExportsOwnOnly() throws Exception {
        //两条本人 APPROVED + 一条他人 APPROVED：学生只导出自己的两条（表头 + 2 行）
        insertAchievement(7000301L, 8821L, "我的省一", "APPROVED");
        insertAchievement(7000302L, 8821L, "我的校二", "APPROVED");
        insertAchievement(7000303L, 8822L, "别人的成果", "APPROVED");
        XSSFSheet sheet = exportAndParse(ownerToken, null);
        assert sheet.getLastRowNum() == 2 : "学生导出应只有本人的 2 条（含表头共 3 行），实际 "
                + sheet.getLastRowNum();
        assert "我的省一".equals(sheet.getRow(1).getCell(0).getStringCellValue());
    }

    @Test
    void adminExportsAll() throws Exception {
        //管理员按队导出（本人的 + 他人的一并带出）。不带 teamId 的全量导出在演示种子下会撞 5008
        //——种子里 803 的载体成果 702 是 PENDING，闸门宁拒不漏属正确行为，故用 teamId 圈定范围
        //来测"管理员能看他人成果"这一核心意图（2026-10-23 种子新增 702 后跟进调整）
        insertAchievement(7000301L, 8821L, "我的省一", "APPROVED");
        insertAchievement(7000303L, 8822L, "别人的成果", "APPROVED");
        insertAchievement(7000304L, 8822L, "别人的校二", "APPROVED");
        XSSFSheet sheet = exportAndParse(adminToken, TEAM_ID);
        assert sheet.getLastRowNum() == 3
                : "管理员按队导出应带出本队 3 条（含表头共 4 行），实际 "
                + sheet.getLastRowNum();
        //我插的那条应带出队名（loadTeamNames 批量查）
        String teamOfMine = null;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            if ("我的省一".equals(sheet.getRow(i).getCell(0).getStringCellValue())) {
                teamOfMine = sheet.getRow(i).getCell(5).getStringCellValue();
                break;
            }
        }
        assert "导出测试队".equals(teamOfMine) : "成果行应带出队伍名，实际 " + teamOfMine;
    }

    @Test
    void studentWithoutAchievementsRejected() throws Exception {
        //本人名下一条都没有 → 1004 没有可导出的成果
        export(ownerToken).andExpect(jsonPath("$.code").value(1004));
    }

    private org.springframework.test.web.servlet.ResultActions export(String token) throws Exception {
        return export(token, null);
    }

    private org.springframework.test.web.servlet.ResultActions export(String token, Long teamId) throws Exception {
        return mockMvc.perform(get("/api/achievements/export"
                        + (teamId == null ? "" : "?teamId=" + teamId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    /** 导出并按 POI 解析成工作表（xlsx 为 zip 容器，顺带验证了魔数） */
    private XSSFSheet exportAndParse(String token, Long teamId) throws Exception {
        MvcResult result = export(token, teamId).andReturn();
        byte[] body = result.getResponse().getContentAsByteArray();
        assert body.length > 4 && body[0] == 'P' && body[1] == 'K' : "响应应是 xlsx（zip 容器 PK 魔数）";
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(body))) {
            return workbook.getSheet("获奖清单");
        }
    }

    private void insertAchievement(long id, long ownerMemberId, String name, String status) {
        jdbcTemplate.update(
                "INSERT INTO achievement (id, team_id, contest_id, semester_id, org_id, owner_member_id, "
                        + "owner_name, achievement_type, name, status) "
                        + "VALUES (?, ?, ?, 999, 9311, ?, '归属人', 'AWARD', ?, ?)",
                id, TEAM_ID, CONTEST_ID, ownerMemberId, name, status);
    }

    private void insertMember(long id, String name, long orgId) {
        jdbcTemplate.update(
                "INSERT INTO member (id, name, org_id, member_type) VALUES (?, ?, ?, 'STUDENT')",
                id, name, orgId);
    }
}
