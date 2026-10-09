package com.viomanager.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.viomanager.approval.dto.ApprovalCreateDTO;
import com.viomanager.approval.handler.RecordingTeamHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * Step 9 验证（处理链）：审批全流程与五个动作分支。
 * 覆盖方案验证栏：提交 → 待办出现 → 批准 → 回调执行 → 红点归零；
 * 驳回 / 取消 / 目标人同意与拒绝、状态与权限错误分支。
 */
@TestPropertySource(properties = "vio.audit.async=false")
class ApprovalFlowTest extends ApprovalTestBase {

    private String applicantToken;

    private String targetToken;

    private String deptAToken;

    private String deptBToken;

    private String superToken;

    @BeforeEach
    void setUp() throws Exception {
        RecordingTeamHandler.reset();
        //1.组织树：9301 本部门、9302 别的部门
        insertOrg(9301L, "软件学院", "/0/9301/");
        insertOrg(9302L, "机械学院", "/0/9302/");
        //2.发起人（人员档案 8801）、目标人（8802）都是学生；两部门管理员；超管
        applicantToken = createUserAndLogin("appr_applicant", "STUDENT", 9301L, 8801L);
        targetToken = createUserAndLogin("appr_target", "STUDENT", 9301L, 8802L);
        deptAToken = createUserAndLogin("appr_dept_a", "DEPT_ADMIN", 9301L, null);
        deptBToken = createUserAndLogin("appr_dept_b", "DEPT_ADMIN", 9302L, null);
        superToken = createUserAndLogin("appr_super", "SUPER_ADMIN", null, null);
    }

    @Test
    void createSubmitTodoApproveCallbackFullFlow() throws Exception {
        //1.业务线调进程内契约发起建队审批
        Long approvalId = createTeamApproval(9301L, null, "{\"teamName\":\"梦之队\"}");
        //2.落库：PENDING + SUBMIT 流水
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM approval WHERE id = ?", approvalId);
        assertEquals("PENDING", row.get("status"));
        assertEquals(1, countRecords(approvalId, "SUBMIT"));
        //3.部门管理员的待办里出现这张单
        mockMvc.perform(get("/api/approvals").param("view", "TODO").param("status", "PENDING")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].bizTitle").value("张三队参赛报名"));
        //4.管理员批准
        mockMvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Authorization", "Bearer " + deptAToken)
                        .header("X-Idempotency-Key", "appr-key-approve-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"同意组队\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        //5.状态、审批人、流水全部正确
        row = jdbcTemplate.queryForMap("SELECT * FROM approval WHERE id = ?", approvalId);
        assertEquals("APPROVED", row.get("status"));
        assertEquals("同意组队", row.get("handle_comment"));
        assertNotNull(row.get("handled_at"));
        assertEquals(findUserId("appr_dept_a"), row.get("approver_id"));
        assertEquals(1, ((Number) row.get("approver_read")).intValue());
        assertEquals(1, countRecords(approvalId, "APPROVE"));
        //6.回调真的执行了（方案验证栏"批准 → 回调执行建队"）
        assertEquals(1, RecordingTeamHandler.APPROVED_COUNT.get());
        assertEquals(0, RecordingTeamHandler.REJECTED_COUNT.get());
        //7.发起人红点：申请出结果未读 1 → 标记已读 → 归零（方案验证栏"红点归零"）
        mockMvc.perform(get("/api/approvals/unread-count")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultCount").value(1));
        mockMvc.perform(put("/api/approvals/read")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + approvalId + "],\"asRole\":\"APPLICANT\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/approvals/unread-count")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultCount").value(0));
    }

    @Test
    void rejectFlowCallsOnRejected() throws Exception {
        Long approvalId = createTeamApproval(9301L, null, "{\"teamName\":\"白银队\"}");
        mockMvc.perform(post("/api/approvals/" + approvalId + "/reject")
                        .header("Authorization", "Bearer " + deptAToken)
                        .header("X-Idempotency-Key", "appr-key-reject-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"人数超编\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertEquals("REJECTED", jdbcTemplate.queryForObject(
                "SELECT status FROM approval WHERE id = ?", String.class, approvalId));
        assertEquals(1, RecordingTeamHandler.REJECTED_COUNT.get());
        assertEquals(0, RecordingTeamHandler.APPROVED_COUNT.get());
    }

    @Test
    void cancelByApplicantOnly() throws Exception {
        Long approvalId = createTeamApproval(9301L, 8802L, "{\"teamName\":\"青铜队\"}");
        //1.非发起人（目标人）取消 → 6002
        mockMvc.perform(post("/api/approvals/" + approvalId + "/cancel")
                        .header("Authorization", "Bearer " + targetToken)
                        .header("X-Idempotency-Key", "appr-key-cancel-wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(6002));
        //2.发起人取消 → CANCELLED，且不触发任何回调
        mockMvc.perform(post("/api/approvals/" + approvalId + "/cancel")
                        .header("Authorization", "Bearer " + applicantToken)
                        .header("X-Idempotency-Key", "appr-key-cancel-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"不报这个比赛了\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertEquals("CANCELLED", jdbcTemplate.queryForObject(
                "SELECT status FROM approval WHERE id = ?", String.class, approvalId));
        assertEquals(1, countRecords(approvalId, "CANCEL"));
        assertEquals(0, RecordingTeamHandler.APPROVED_COUNT.get());
        assertEquals(0, RecordingTeamHandler.REJECTED_COUNT.get());
    }

    @Test
    void targetAgreeAndRefuse() throws Exception {
        //1.目标人同意：状态 APPROVED + onApproved 回调
        Long agreeId = createTeamApproval(9301L, 8802L, "{\"teamName\":\"黄金队\"}");
        mockMvc.perform(post("/api/approvals/" + agreeId + "/agree")
                        .header("Authorization", "Bearer " + targetToken)
                        .header("X-Idempotency-Key", "appr-key-agree-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"愿意加入\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertEquals("APPROVED", jdbcTemplate.queryForObject(
                "SELECT status FROM approval WHERE id = ?", String.class, agreeId));
        assertEquals(1, RecordingTeamHandler.APPROVED_COUNT.get());
        assertEquals(1, countRecords(agreeId, "AGREE"));
        //2.目标人拒绝：状态 REJECTED + onRejected 回调
        Long refuseId = createTeamApproval(9301L, 8802L, "{\"teamName\":\"铂金队\"}");
        mockMvc.perform(post("/api/approvals/" + refuseId + "/refuse")
                        .header("Authorization", "Bearer " + targetToken)
                        .header("X-Idempotency-Key", "appr-key-refuse-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertEquals("REJECTED", jdbcTemplate.queryForObject(
                "SELECT status FROM approval WHERE id = ?", String.class, refuseId));
        assertEquals(1, RecordingTeamHandler.REJECTED_COUNT.get());
        //3.非目标人（发起人）去同意 → 6002
        Long otherId = createTeamApproval(9301L, 8802L, "{\"teamName\":\"钻石队\"}");
        mockMvc.perform(post("/api/approvals/" + otherId + "/agree")
                        .header("Authorization", "Bearer " + applicantToken)
                        .header("X-Idempotency-Key", "appr-key-agree-wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(6002));
    }

    @Test
    void stateAndPermissionErrors() throws Exception {
        Long approvalId = createTeamApproval(9301L, null, "{\"teamName\":\"王者队\"}");
        //1.学生调管理员接口 → 403（@RequireRole 挡在角色层）
        mockMvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Authorization", "Bearer " + applicantToken)
                        .header("X-Idempotency-Key", "appr-key-stu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
        //2.别的子树的管理员批 → 6002
        mockMvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Authorization", "Bearer " + deptBToken)
                        .header("X-Idempotency-Key", "appr-key-deptb")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(6002));
        //3.本部门管理员批成功
        mockMvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Authorization", "Bearer " + deptAToken)
                        .header("X-Idempotency-Key", "appr-key-depta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        //4.已处理的单再批 → 6003
        mockMvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Authorization", "Bearer " + deptAToken)
                        .header("X-Idempotency-Key", "appr-key-depta-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(6003));
        //5.不存在的单 → 6001
        mockMvc.perform(post("/api/approvals/999999/approve")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "appr-key-none")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(6001));
    }

    @Test
    void unregisteredBizTypeFallsBack() throws Exception {
        // PROJECT_VIEW 没有任何 Handler 注册：兜底顶上（记日志不动作），审批照常能批
        ApprovalCreateDTO dto = new ApprovalCreateDTO();
        dto.setBizType("PROJECT_VIEW");
        dto.setBizTitle("项目查看申请");
        dto.setApplicantMemberId(8801L);
        dto.setApplicantName("张三");
        dto.setApproverOrgId(9301L);
        Long approvalId = approvalClient.createApproval(dto);
        mockMvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Authorization", "Bearer " + deptAToken)
                        .header("X-Idempotency-Key", "appr-key-fallback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertEquals("APPROVED", jdbcTemplate.queryForObject(
                "SELECT status FROM approval WHERE id = ?", String.class, approvalId));
    }

    /** 调进程内契约创建一张 TEAM_CREATE 审批单 */
    private Long createTeamApproval(Long approverOrgId, Long targetMemberId, String payload) {
        ApprovalCreateDTO dto = new ApprovalCreateDTO();
        dto.setBizType("TEAM_CREATE");
        dto.setBizTitle("张三队参赛报名");
        dto.setPayloadJson(payload);
        dto.setApplicantMemberId(8801L);
        dto.setApplicantName("张三");
        dto.setTargetMemberId(targetMemberId);
        dto.setTargetMemberName(targetMemberId == null ? null : "李四");
        dto.setApproverOrgId(approverOrgId);
        return approvalClient.createApproval(dto);
    }

    private int countRecords(Long approvalId, String action) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval_record WHERE approval_id = ? AND action = ?",
                Integer.class, approvalId, action);
        return count == null ? 0 : count;
    }

    private Long findUserId(String loginName) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM sys_user WHERE login_name = ?", Long.class, loginName);
        return ids.isEmpty() ? null : ids.get(0);
    }
}
