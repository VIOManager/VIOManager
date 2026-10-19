package com.viomanager.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.approval.dto.ApprovalActionDTO;
import com.viomanager.approval.dto.ApprovalCreateDTO;
import com.viomanager.approval.service.ApprovalClient;
import com.viomanager.approval.service.IApprovalActionService;
import com.viomanager.approval.task.ApprovalExpireTask;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import java.util.List;
import java.util.Map;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * 插入点③写入侧验证（方案 Step 5）：状态变更与 outbox 落表同一事务。
 * 覆盖两个写入位：五动作共用链（approve 收口）与 expireOne（过期）。
 * 继承 @Transactional 基类：断言在同一事务内可见，测试结束整体回滚零残留。
 */
@TestPropertySource(properties = "vio.audit.async=false")
class NotifyOutboxWriteTest extends ApprovalTestBase {

    private static final Long ORG_ID = 9301L;
    private static final Long APPLICANT = 8901L;
    private static final Long TARGET = 8902L;

    @Resource
    private IApprovalActionService approvalActionService;

    @Resource
    private ApprovalExpireTask approvalExpireTask;

    @BeforeEach
    void setUp() {
        insertOrg(ORG_ID, "outbox测试学院", "/0/9301/");
    }

    @AfterEach
    void cleanUserHolder() {
        UserHolder.removeUser();
    }

    @Test
    void approveWritesOutboxSameTransaction() {
        //1.建单即落 SUBMIT 事件（有目标人的单落地就推高目标人红点）
        Long approvalId = createApproval(APPLICANT, TARGET);
        Map<String, Object> submitRow = outboxRow(approvalId, "SUBMIT");
        assertEquals("PENDING", submitRow.get("send_status"));
        assertEquals("PENDING", JsonPath.read((String) submitRow.get("payload_json"), "$.toStatus"));

        //2.部门管理员批准 → APPROVE 事件同事务落行
        loginDeptAdmin();
        approvalActionService.approve(approvalId, dto("同意"));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notify_outbox WHERE biz_id = ?", Integer.class, approvalId),
                "建单 + 批准应各落一行");
        Map<String, Object> row = outboxRow(approvalId, "APPROVE");
        assertEquals("approval-status", row.get("topic"));
        assertEquals("PENDING", row.get("send_status"));
        assertEquals(0, ((Number) row.get("retry_count")).intValue());
        assertNull(row.get("next_retry_at"));

        //3.payload：动作、目标状态、受影响人 = 发起人 + 目标人
        String payload = (String) row.get("payload_json");
        assertEquals("APPROVE", JsonPath.read(payload, "$.action"));
        assertEquals("APPROVED", JsonPath.read(payload, "$.toStatus"));
        List<?> memberIds = JsonPath.read(payload, "$.memberIds");
        assertEquals(2, memberIds.size());
        assertTrue(memberIds.stream().map(String::valueOf)
                .anyMatch(id -> id.equals(String.valueOf(APPLICANT))));
        assertTrue(memberIds.stream().map(String::valueOf)
                .anyMatch(id -> id.equals(String.valueOf(TARGET))));
    }

    @Test
    void expireWritesOutboxWithApplicantOnly() {
        //1.无目标人的单过期（建单的 SUBMIT 行 + 过期的 EXPIRE 行）
        Long approvalId = createApproval(APPLICANT, null);
        boolean expired = approvalExpireTask.expireOne(approvalId);
        assertTrue(expired);

        //2.EXPIRE 事件落行，受影响人只有发起人
        Map<String, Object> row = outboxRow(approvalId, "EXPIRE");
        assertEquals("PENDING", row.get("send_status"));
        String payload = (String) row.get("payload_json");
        assertEquals("EXPIRE", JsonPath.read(payload, "$.action"));
        assertEquals("EXPIRED", JsonPath.read(payload, "$.toStatus"));
        List<?> memberIds = JsonPath.read(payload, "$.memberIds");
        assertEquals(1, memberIds.size());
        assertEquals(String.valueOf(APPLICANT), String.valueOf(memberIds.get(0)));
    }

    private Long createApproval(Long applicant, Long target) {
        ApprovalCreateDTO dto = new ApprovalCreateDTO();
        dto.setBizType("OUTBOX_WRITE_TEST");
        dto.setBizTitle("outbox 写入侧测试");
        dto.setApplicantMemberId(applicant);
        dto.setApplicantName("写入侧测试");
        dto.setTargetMemberId(target);
        dto.setTargetMemberName(target == null ? null : "目标人");
        dto.setApproverOrgId(ORG_ID);
        return approvalClient.createApproval(dto);
    }

    private void loginDeptAdmin() {
        CurrentUser admin = new CurrentUser();
        admin.setUserId(1L);
        admin.setRole("DEPT_ADMIN");
        admin.setOrgId(ORG_ID);
        UserHolder.saveUser(admin);
    }

    /** 按动作取那一行 outbox（建单 SUBMIT / 状态变更各落一行） */
    private Map<String, Object> outboxRow(Long approvalId, String action) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM notify_outbox WHERE biz_id = ?", approvalId);
        return rows.stream()
                .filter(r -> ((String) r.get("payload_json")).contains("\"action\":\"" + action + "\""))
                .findFirst()
                .orElseThrow(() -> new AssertionError("没找到 " + action + " 事件行"));
    }

    private ApprovalActionDTO dto(String comment) {
        ApprovalActionDTO dto = new ApprovalActionDTO();
        dto.setComment(comment);
        return dto;
    }
}
