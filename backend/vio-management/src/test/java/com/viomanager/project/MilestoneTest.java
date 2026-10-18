package com.viomanager.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.annotation.Resource;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;

/**
 * M4 时间节点测试（PR16–PR20 + 审批回调 + 同步）：
 * 状态机闸门 7009、管理范围（外部门 1003 / 学生 403）、学生视图仅 APPROVED、
 * 真实审批链路端到端（submit → 超管 approve → 节点 APPROVED + 挂载项目批量生成任务）、
 * 回调重放幂等（synced 防重）、驳回记流水意见、挂载回溯补建。
 */
@Transactional
class MilestoneTest extends PmTestSupport {

    private static final Long ORG_SOFT = 9301L;
    private static final Long ORG_MECH = 9302L;
    private static final Long SEMESTER = 95001L;
    private static final Long CONTEST = 88001L;
    private static final Long TEAM = 99901L;

    private String deptSoftToken;
    private String deptMechToken;
    private String superToken;
    private String studentToken;
    private String ownerToken;
    private long projectId;

    @Resource
    private com.viomanager.project.handler.MilestoneApproveHandler approveHandler;

    @BeforeEach
    void setUp() throws Exception {
        insertOrg(ORG_SOFT, "软件学院", "/0/9301/");
        insertOrg(ORG_MECH, "机械学院", "/0/9302/");
        insertCurrentSemester(SEMESTER, "2026-2027学年第一学期-PM节点测试");
        jdbcTemplate.update("INSERT INTO contest (id, name, semester_id, org_id, status) "
                + "VALUES (?, '蓝桥杯', ?, ?, 'RUNNING')", CONTEST, SEMESTER, ORG_SOFT);
        insertMember(80001L, "项目A", ORG_SOFT, "STUDENT");
        insertMember(80002L, "管理员M", ORG_SOFT, "TEACHER");
        insertMember(80003L, "学生S", ORG_SOFT, "STUDENT");
        //项目（挂到比赛 88001 的赛队上，节点审核通过要给它生成任务）
        ownerToken = createUserAndLogin("pm4_owner", "STUDENT", ORG_SOFT, 80001L);
        String body = createProject(ownerToken,
                "{\"name\":\"节点同步项目\",\"visibility\":\"ORG\",\"contestId\":" + CONTEST + "}");
        assertEquals(0, (int) JsonPath.read(body, "$.code"), body);
        projectId = Long.parseLong(JsonPath.read(body, "$.data.id"));
        jdbcTemplate.update("INSERT INTO team (id, name, contest_id, semester_id, org_id, "
                + "leader_member_id, status) VALUES (?, '软院一队', ?, ?, ?, 80001, 'ACTIVE')",
                TEAM, CONTEST, SEMESTER, ORG_SOFT);
        jdbcTemplate.update("INSERT INTO team_project_ref (id, team_id, project_id, contest_id, "
                + "semester_id, status, attached_by) VALUES (70001, ?, ?, ?, ?, 'ACTIVE', 80001)",
                TEAM, projectId, CONTEST, SEMESTER);
        //部门管理员绑 member 档案（提审 applicantMemberId 必填）
        deptSoftToken = createUserAndLogin("pm4_dept_soft", "DEPT_ADMIN", ORG_SOFT, 80002L);
        deptMechToken = createUserAndLogin("pm4_dept_mech", "DEPT_ADMIN", ORG_MECH, null);
        superToken = createAdminAndLogin("pm4_super", "SUPER_ADMIN");
        studentToken = createUserAndLogin("pm4_student", "STUDENT", ORG_SOFT, 80003L);
    }

    private long createNode(String token, String name, String nodeType) throws Exception {
        String body = mockMvc.perform(withToken(
                        post("/api/contests/" + CONTEST + "/milestones"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"nodeType\":\"" + nodeType
                                + "\",\"nodeTime\":\"2026-11-01 18:00:00\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertEquals(0, (int) JsonPath.read(body, "$.code"), body);
        return Long.parseLong(JsonPath.read(body, "$.data.id"));
    }

    private String submit(String token, long nodeId) throws Exception {
        return mockMvc.perform(withToken(post("/api/milestones/" + nodeId + "/submit"), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /** 提审后查 MILESTONE_APPROVE 单 ID（真实审批链路） */
    private long approvalIdOf(long nodeId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM approval WHERE biz_type = 'MILESTONE_APPROVE' AND biz_id = ? "
                        + "AND deleted = 0 ORDER BY id DESC LIMIT 1", Long.class, nodeId);
    }

    private String approve(String token, long approvalId, String key) throws Exception {
        return mockMvc.perform(withToken(
                        post("/api/approvals/" + approvalId + "/approve"), token)
                        .header("X-Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"同意\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String nodeStatus(long nodeId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM milestone_node WHERE id = ?", String.class, nodeId);
    }

    private int taskCount(long pid, long nodeId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project_task WHERE project_id = ? AND milestone_node_id = ? "
                        + "AND deleted = 0", Integer.class, pid, nodeId);
        return count == null ? 0 : count;
    }

    // ---------------------------------------------------------------- 状态机与权限

    @Test
    void stateMachineGuards() throws Exception {
        long id = createNode(deptSoftToken, "报名开始", "REGISTER_START");
        assertEquals("DRAFT", nodeStatus(id));
        //DRAFT 可编辑可删除前先编辑验证
        assertEquals(0, (int) JsonPath.read(mockMvc.perform(withToken(
                        put("/api/milestones/" + id), deptSoftToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"报名开始-改\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        //提审 → PENDING
        assertEquals(0, (int) JsonPath.read(submit(deptSoftToken, id), "$.code"));
        assertEquals("PENDING", nodeStatus(id));
        //PENDING 编辑/删除 → 7009（内容冻结，审批单还引用着）
        assertEquals(7009, (int) JsonPath.read(mockMvc.perform(withToken(
                        put("/api/milestones/" + id), deptSoftToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"偷改\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        assertEquals(7009, (int) JsonPath.read(mockMvc.perform(withToken(
                        delete("/api/milestones/" + id), deptSoftToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        //学生创建 → HTTP 403（@RequireRole 层拦截）
        mockMvc.perform(withToken(post("/api/contests/" + CONTEST + "/milestones"), studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"学生节点\",\"nodeType\":\"OTHER\","
                                + "\"nodeTime\":\"2026-11-01 18:00:00\"}"))
                .andExpect(status().isForbidden());
        //外部门管理员（比赛不在其子树）→ 1003（骨架映射为 HTTP 403）
        assertEquals(1003, (int) JsonPath.read(mockMvc.perform(withToken(
                        post("/api/contests/" + CONTEST + "/milestones"), deptMechToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"跨部门节点\",\"nodeType\":\"OTHER\","
                                + "\"nodeTime\":\"2026-11-01 18:00:00\"}"))
                .andExpect(status().isForbidden())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
    }

    @Test
    void profileLessSuperAdminCannotSubmitButOthersCan() throws Exception {
        //无档案超管可维护节点（建/删不需要 applicant），但提审落审批单必须落到具体人员 → 7014（此前误报 1001）
        long id = createNode(superToken, "超管维护节点", "OTHER");
        assertEquals(7014, (int) JsonPath.read(submit(superToken, id), "$.code"));
        //节点停留 DRAFT（状态转移与审批单同事务回滚）
        assertEquals("DRAFT", nodeStatus(id));
        //无档案部门管理员同理（dept_mech 未绑 member，且比赛在其子树内才走到 applicant 判定——用子树内比赛验证）
        jdbcTemplate.update("INSERT INTO contest (id, name, semester_id, org_id, status) "
                + "VALUES (?, '机械比赛', ?, ?, 'RUNNING')", CONTEST + 1, SEMESTER, ORG_MECH);
        String mechBody = mockMvc.perform(withToken(
                        post("/api/contests/" + (CONTEST + 1) + "/milestones"), deptMechToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"机械节点\",\"nodeType\":\"OTHER\","
                                + "\"nodeTime\":\"2026-11-02 09:00:00\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertEquals(0, (int) JsonPath.read(mechBody, "$.code"), mechBody);
        long mechNodeId = Long.parseLong(JsonPath.read(mechBody, "$.data.id"));
        assertEquals(7014, (int) JsonPath.read(submit(deptMechToken, mechNodeId), "$.code"));
        //绑定档案的管理员提交同一节点 → 0（对照组：能力与档案绑定相关，与角色无关）
        assertEquals(0, (int) JsonPath.read(submit(deptSoftToken, id), "$.code"));
        assertEquals("PENDING", nodeStatus(id));
    }

    @Test
    void studentSeesOnlyApproved() throws Exception {
        long draft = createNode(deptSoftToken, "草稿节点", "OTHER");
        long approved = createNode(deptSoftToken, "生效节点", "OTHER");
        assertEquals(0, (int) JsonPath.read(submit(deptSoftToken, approved), "$.code"));
        assertEquals(0, (int) JsonPath.read(
                approve(superToken, approvalIdOf(approved), "pm4-appr-1"), "$.code"));
        assertEquals("APPROVED", nodeStatus(approved));
        //学生列表：仅 APPROVED
        String body = mockMvc.perform(withToken(
                        get("/api/contests/" + CONTEST + "/milestones"), studentToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertEquals(0, (int) JsonPath.read(body, "$.code"));
        List<?> list = JsonPath.read(body, "$.data[*]");
        assertEquals(1, list.size(), "学生只看到 APPROVED 节点: " + body);
        assertEquals("生效节点", JsonPath.read(body, "$.data[0].name"));
        //管理员看全部（含 DRAFT）
        String mgrView = mockMvc.perform(withToken(
                        get("/api/contests/" + CONTEST + "/milestones"), deptSoftToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertEquals(2, ((List<?>) JsonPath.read(mgrView, "$.data[*]")).size());
        //nodeType 筛选（学生视图内存过滤）
        String filtered = mockMvc.perform(withToken(
                        get("/api/contests/" + CONTEST + "/milestones")
                                .queryParam("nodeType", "REGISTER_START"), studentToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertEquals(0, ((List<?>) JsonPath.read(filtered, "$.data[*]")).size());
    }

    // ---------------------------------------------------------------- 审批链路 + 同步

    @Test
    void approvalFlowSyncsTasksToMountedProjects() throws Exception {
        long nodeId = createNode(deptSoftToken, "作品提交截止", "WORK_SUBMIT");
        assertEquals(0, (int) JsonPath.read(submit(deptSoftToken, nodeId), "$.code"));
        assertEquals(0, (int) JsonPath.read(
                approve(superToken, approvalIdOf(nodeId), "pm4-appr-2"), "$.code"));
        //节点 APPROVED + synced 标志
        assertEquals("APPROVED", nodeStatus(nodeId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT synced FROM milestone_node WHERE id = ?", Integer.class, nodeId));
        //挂载项目生成任务：title=节点名、due_date=节点时间、milestone_node_id 回填
        assertEquals(1, taskCount(projectId, nodeId));
        var task = jdbcTemplate.queryForMap(
                "SELECT title, due_date, status, milestone_node_id FROM project_task "
                        + "WHERE project_id = ? AND milestone_node_id = ? AND deleted = 0",
                projectId, nodeId);
        assertEquals("作品提交截止", task.get("title"));
        assertEquals(nodeId, ((Number) task.get("milestone_node_id")).longValue());
        assertEquals("TODO", task.get("status"));
        //重放防护：审批中心"至多一次"契约下不应有重放；人为重放时 Handler 的 CAS 保护
        //（节点已 APPROVED，条件 UPDATE 0 行）抛 7009 拒绝，任务不可能重复
        org.junit.jupiter.api.Assertions.assertThrows(
                com.viomanager.common.exception.BizException.class,
                () -> approveHandler.onApproved(approvalIdOf(nodeId), nodeId, "{}"));
        assertEquals(1, taskCount(projectId, nodeId), "CAS 保护下重放不生成重复任务");
    }

    @Test
    void rejectFlowRecordsReasonFromRecord() throws Exception {
        long nodeId = createNode(deptSoftToken, "待驳回节点", "OTHER");
        assertEquals(0, (int) JsonPath.read(submit(deptSoftToken, nodeId), "$.code"));
        long approvalId = approvalIdOf(nodeId);
        assertEquals(0, (int) JsonPath.read(mockMvc.perform(withToken(
                        post("/api/approvals/" + approvalId + "/reject"), superToken)
                        .header("X-Idempotency-Key", "pm4-rej-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"时间不对\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        //REJECTED + 流水意见带入 reject_reason
        assertEquals("REJECTED", nodeStatus(nodeId));
        assertEquals("时间不对", jdbcTemplate.queryForObject(
                "SELECT reject_reason FROM milestone_node WHERE id = ?", String.class, nodeId));
        //驳回后可编辑（REJECTED 态）并重新提交
        assertEquals(0, (int) JsonPath.read(mockMvc.perform(withToken(
                        put("/api/milestones/" + nodeId), deptSoftToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"待驳回节点-修正\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT reject_reason FROM milestone_node WHERE id = ?", String.class, nodeId),
                "重新编辑清掉旧驳回原因");
        assertEquals(0, (int) JsonPath.read(submit(deptSoftToken, nodeId), "$.code"));
        assertEquals("PENDING", nodeStatus(nodeId));
    }

    @Test
    void attachBackfillSyncsRetroactively() throws Exception {
        //路径一（审批时同步）：setUp 的项目已挂载，节点审核通过立即生成任务
        long nodeId = createNode(deptSoftToken, "回溯节点", "OTHER");
        assertEquals(0, (int) JsonPath.read(submit(deptSoftToken, nodeId), "$.code"));
        assertEquals(0, (int) JsonPath.read(
                approve(superToken, approvalIdOf(nodeId), "pm4-appr-3"), "$.code"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT synced FROM milestone_node WHERE id = ?", Integer.class, nodeId));
        assertEquals(1, taskCount(projectId, nodeId), "挂载中的项目在审批通过时即时同步");
        //路径二（挂载回溯）：新项目 B 在节点通过**之后**才挂载 → 补建它缺的任务
        String bBody = createProject(ownerToken,
                "{\"name\":\"回溯项目B\",\"visibility\":\"ORG\",\"contestId\":" + CONTEST + "}");
        long projectB = Long.parseLong(JsonPath.read(bBody, "$.data.id"));
        assertEquals(0, taskCount(projectB, nodeId), "未挂载前无任务");
        String attach = mockMvc.perform(withToken(post("/api/teams/" + TEAM + "/projects"),
                        ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + projectB + "}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertEquals(0, (int) JsonPath.read(attach, "$.code"), attach);
        //回溯：节点 synced=1 但项目 B 缺任务 → 存在性检查放行补建
        assertEquals(1, taskCount(projectB, nodeId), "挂载回溯应补建已审核节点的任务");
        assertEquals(1, taskCount(projectId, nodeId), "原项目任务不受影响");
        //重复挂载（先解绑再挂）不重复建：存在性检查兜住
        assertEquals(0, (int) JsonPath.read(mockMvc.perform(withToken(
                        delete("/api/teams/" + TEAM + "/projects/" + projectB), ownerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        String reattach = mockMvc.perform(withToken(post("/api/teams/" + TEAM + "/projects"),
                        ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + projectB + "}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertEquals(0, (int) JsonPath.read(reattach, "$.code"), reattach);
        assertEquals(1, taskCount(projectB, nodeId), "重复挂载不重复建任务");
    }
}
