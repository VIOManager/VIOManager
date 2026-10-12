package com.viomanager.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.approval.dto.ApprovalCreateDTO;
import com.viomanager.approval.task.ApprovalExpireTask;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Step 9 验证（查询面）：三视角列表的范围隔离、详情含流水、已读标记、过期任务。
 */
@TestPropertySource(properties = "vio.audit.async=false")
class ApprovalQueryTest extends ApprovalTestBase {

    @Resource
    private ApprovalExpireTask expireTask;

    private String applicantToken;

    private String targetToken;

    private String deptAToken;

    private String deptBToken;

    private String superToken;

    @BeforeEach
    void setUp() throws Exception {
        insertOrg(9301L, "软件学院", "/0/9301/");
        insertOrg(9302L, "机械学院", "/0/9302/");
        applicantToken = createUserAndLogin("query_applicant", "STUDENT", 9301L, 8811L);
        targetToken = createUserAndLogin("query_target", "STUDENT", 9301L, 8812L);
        deptAToken = createUserAndLogin("query_dept_a", "DEPT_ADMIN", 9301L, null);
        deptBToken = createUserAndLogin("query_dept_b", "DEPT_ADMIN", 9302L, null);
        superToken = createUserAndLogin("query_super", "SUPER_ADMIN", null, null);
        //3 张单：9301 两张（其一发给目标人 8812）、9302 一张
        createApproval("TEAM_CREATE", 8811L, "张三", null, null, 9301L);
        createApproval("TEAM_ADD_MEMBER", 8811L, "张三", 8812L, "李四", 9301L);
        createApproval("ACHIEVEMENT_SUBMIT", 8812L, "李四", null, null, 9302L);
    }

    @Test
    void listViewsAreScoped() throws Exception {
        //1.MINE：我发起的（申请人 8811）→ 2 张
        mockMvc.perform(get("/api/approvals").param("view", "MINE")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
        //2.TARGET：发给目标人 8812 的 → 1 张
        mockMvc.perform(get("/api/approvals").param("view", "TARGET")
                        .header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].targetMemberName").value("李四"));
        //3.TODO：本部门管理员 → 2 张；别的部门管理员 → 1 张；超管 → 3 张
        mockMvc.perform(get("/api/approvals").param("view", "TODO")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
        mockMvc.perform(get("/api/approvals").param("view", "TODO")
                        .header("Authorization", "Bearer " + deptBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/api/approvals").param("view", "TODO")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));
        //4.学生调 TODO → 403
        mockMvc.perform(get("/api/approvals").param("view", "TODO")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
        //5.分页与筛选：bizType 过滤 + size=1 翻页
        mockMvc.perform(get("/api/approvals").param("view", "TODO")
                        .param("bizType", "TEAM_CREATE")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/api/approvals").param("view", "MINE").param("page", "1").param("size", "1")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list.length()").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(1));
    }

    @Test
    void listFiltersByKeyword() throws Exception {
        //1.标题关键词模糊搜：张三 → 命中张三发起的两张
        mockMvc.perform(get("/api/approvals").param("view", "TODO")
                        .param("keyword", "张三")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
        //2.三张单标题都含"申请" → 全命中
        mockMvc.perform(get("/api/approvals").param("view", "TODO")
                        .param("keyword", "申请")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));
        //3.无命中关键词 → 0 张
        mockMvc.perform(get("/api/approvals").param("view", "TODO")
                        .param("keyword", "查无此队")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void detailContainsPayloadAndRecords() throws Exception {
        //1.创建并批准，凑齐 SUBMIT + APPROVE 两条流水
        Long approvalId = createApproval("TEAM_CREATE", 8811L, "张三", null, null, 9301L);
        mockMvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Authorization", "Bearer " + deptAToken)
                        .header("X-Idempotency-Key", "query-key-approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"可以\"}"))
                .andExpect(status().isOk());
        //2.发起人看详情：快照 + 流水（时间正序）+ 处理意见
        MvcResult result = mockMvc.perform(get("/api/approvals/" + approvalId)
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.records[0].action").value("SUBMIT"))
                .andExpect(jsonPath("$.data.records[1].action").value("APPROVE"))
                .andExpect(jsonPath("$.data.handleComment").value("可以"))
                .andReturn();
        String payload = JsonPath.read(result.getResponse().getContentAsString(), "$.data.bizPayload");
        assertTrue(payload.contains("teamName"), "详情应带申请内容快照");
        //3.无关路人（非发起人 / 目标人 / 责任部门）看详情 → 6002
        String bystanderToken = createUserAndLogin("query_bystander", "STUDENT", 9302L, 8813L);
        mockMvc.perform(get("/api/approvals/" + approvalId)
                        .header("Authorization", "Bearer " + bystanderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(6002));
    }

    @Test
    void markReadByRole() throws Exception {
        //1.目标人视角：待处理红点 1 → 标记已读 → 归零
        mockMvc.perform(get("/api/approvals/unread-count")
                        .header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todoCount").value(1))
                .andExpect(jsonPath("$.data.resultCount").value(0));
        Long targetApprovalId = jdbcTemplate.queryForObject(
                "SELECT id FROM approval WHERE target_member_id = 8812", Long.class);
        mockMvc.perform(put("/api/approvals/read")
                        .header("Authorization", "Bearer " + targetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + targetApprovalId + "],\"asRole\":\"TARGET\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/approvals/unread-count")
                        .header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todoCount").value(0));
        //2.没绑人员档案的账号（纯管理员）两类红点都是 0
        mockMvc.perform(get("/api/approvals/unread-count")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todoCount").value(0))
                .andExpect(jsonPath("$.data.resultCount").value(0));
    }

    @Test
    void expireTaskConvertsOverdue() {
        //1.把一张 9301 的单过期时间改到过去
        Long approvalId = createApproval("TEAM_CREATE", 8811L, "张三", null, null, 9301L);
        jdbcTemplate.update(
                "UPDATE approval SET expire_at = '2000-01-01 00:00:00' WHERE id = ?", approvalId);
        //2.跑过期任务：转 EXPIRED + 记 EXPIRE 流水，没过期的原样保留
        int expired = expireTask.expireOverdue();
        assertTrue(expired >= 1, "到期的单应被转 EXPIRED");
        assertEquals("EXPIRED", jdbcTemplate.queryForObject(
                "SELECT status FROM approval WHERE id = ?", String.class, approvalId));
        Integer expireRecords = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval_record WHERE approval_id = ? AND action = 'EXPIRE'",
                Integer.class, approvalId);
        assertEquals(1, expireRecords);
        Integer stillPending = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval WHERE status = 'PENDING' AND expire_at > NOW()",
                Integer.class);
        assertNotNull(stillPending);
        assertTrue(stillPending >= 2, "未到期的单不受影响");
    }

    /** 调进程内契约造一张审批单 */
    private Long createApproval(String bizType, Long applicantId, String applicantName,
                                Long targetId, String targetName, Long approverOrgId) {
        ApprovalCreateDTO dto = new ApprovalCreateDTO();
        dto.setBizType(bizType);
        dto.setBizTitle(applicantName + "的" + bizType + "申请");
        dto.setPayloadJson("{\"teamName\":\"测试队\"}");
        dto.setApplicantMemberId(applicantId);
        dto.setApplicantName(applicantName);
        dto.setTargetMemberId(targetId);
        dto.setTargetMemberName(targetName);
        dto.setApproverOrgId(approverOrgId);
        return approvalClient.createApproval(dto);
    }
}
