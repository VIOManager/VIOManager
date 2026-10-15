package com.viomanager.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * M4 项目任务测试（PR21–PR24）：手工任务 CRUD 与默认值、列表排序与筛选、
 * PR23 权限矩阵（所有人全字段 / 负责人仅置 DONE / 其余 7002）、
 * 完成列语义（置 DONE 回填、离开 DONE 清空）、7012 跨项目防串、删除同步任务。
 */
class TaskTest extends PmTestSupport {

    private static final Long ORG_SOFT = 9301L;
    private static final Long SEMESTER = 95001L;

    private String ownerToken;
    private String assigneeToken;
    private String outsiderToken;
    private long projectId;
    private long otherProjectId;

    @BeforeEach
    void setUp() throws Exception {
        insertOrg(ORG_SOFT, "软件学院", "/0/9301/");
        insertCurrentSemester(SEMESTER, "2026-2027学年第一学期-PM任务测试");
        insertMember(80001L, "项目A", ORG_SOFT, "STUDENT");
        insertMember(80002L, "负责人B", ORG_SOFT, "STUDENT");
        insertMember(80003L, "外人C", ORG_SOFT, "STUDENT");
        ownerToken = createUserAndLogin("pm5_owner", "STUDENT", ORG_SOFT, 80001L);
        assigneeToken = createUserAndLogin("pm5_assignee", "STUDENT", ORG_SOFT, 80002L);
        outsiderToken = createUserAndLogin("pm5_outsider", "STUDENT", ORG_SOFT, 80003L);
        String body = createProject(ownerToken, "{\"name\":\"任务主项目\",\"visibility\":\"ORG\"}");
        assertEquals(0, (int) JsonPath.read(body, "$.code"), body);
        projectId = Long.parseLong(JsonPath.read(body, "$.data.id"));
        String other = createProject(ownerToken, "{\"name\":\"任务旁项目\",\"visibility\":\"ORG\"}");
        otherProjectId = Long.parseLong(JsonPath.read(other, "$.data.id"));
    }

    private long createTask(String token, long pid, String body) throws Exception {
        String resp = mockMvc.perform(withToken(post("/api/projects/" + pid + "/tasks"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertEquals(0, (int) JsonPath.read(resp, "$.code"), resp);
        return Long.parseLong(JsonPath.read(resp, "$.data.id"));
    }

    private String update(String token, long pid, long taskId, String body) throws Exception {
        return mockMvc.perform(withToken(put("/api/projects/" + pid + "/tasks/" + taskId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String list(String token, long pid, String query) throws Exception {
        return mockMvc.perform(withToken(get("/api/projects/" + pid + "/tasks" + query), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    // ---------------------------------------------------------------- 基础 CRUD

    @Test
    void manualTaskCrudWithDefaults() throws Exception {
        long t1 = createTask(ownerToken, projectId,
                "{\"title\":\"联调接口\",\"assigneeMemberId\":80002}");
        long t2 = createTask(ownerToken, projectId,
                "{\"title\":\"写文档\",\"sortNo\":1,\"priority\":\"HIGH\"}");
        //默认值：TODO / NORMAL / sortNo 0；负责人姓名冗余
        assertEquals("TODO", jdbcTemplate.queryForObject(
                "SELECT status FROM project_task WHERE id = ?", String.class, t1));
        assertEquals("NORMAL", jdbcTemplate.queryForObject(
                "SELECT priority FROM project_task WHERE id = ?", String.class, t1));
        assertEquals("负责人B", jdbcTemplate.queryForObject(
                "SELECT assignee_name FROM project_task WHERE id = ?", String.class, t1));
        //列表按 sort_no 排序（文档 1 → 联调 0？sort_no 升序：联调(0)在前）
        String body = list(ownerToken, projectId, "");
        assertEquals(0, (int) JsonPath.read(body, "$.code"));
        assertEquals("联调接口", JsonPath.read(body, "$.data[0].title"));
        assertEquals("写文档", JsonPath.read(body, "$.data[1].title"));
        //筛选：status=TODO 全中 / assignee=80002 只中联调
        assertEquals(2, ((List<?>) JsonPath.read(
                list(ownerToken, projectId, "?status=TODO"), "$.data[*]")).size());
        assertEquals(1, ((List<?>) JsonPath.read(
                list(ownerToken, projectId, "?assigneeMemberId=80002"), "$.data[*]")).size());
        //负责人不存在 → 1004（所有人创建路径才走到 assignee 校验）
        assertEquals(1004, (int) JsonPath.read(mockMvc.perform(withToken(
                        post("/api/projects/" + projectId + "/tasks"), ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"幽灵负责人\",\"assigneeMemberId\":99999}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        //学生越权创建 → 7002（先被所有人校验拦截）
        assertEquals(7002, (int) JsonPath.read(mockMvc.perform(withToken(
                        post("/api/projects/" + projectId + "/tasks"), outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"外人的任务\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        //项目可见者可看列表（同部门学生）
        assertEquals(0, (int) JsonPath.read(list(outsiderToken, projectId, ""), "$.code"));
        //删除：仅所有人
        assertEquals(7002, (int) JsonPath.read(mockMvc.perform(withToken(
                        delete("/api/projects/" + projectId + "/tasks/" + t2), outsiderToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        assertEquals(0, (int) JsonPath.read(mockMvc.perform(withToken(
                        delete("/api/projects/" + projectId + "/tasks/" + t2), ownerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT deleted FROM project_task WHERE id = ?", Integer.class, t2));
    }

    // ---------------------------------------------------------------- PR23 权限矩阵

    @Test
    void assigneeCanOnlyMarkDone() throws Exception {
        long taskId = createTask(ownerToken, projectId,
                "{\"title\":\"负责人任务\",\"assigneeMemberId\":80002}");
        //负责人置 DONE：成功，completed_at/by 回填（人员口径）
        assertEquals(0, (int) JsonPath.read(update(assigneeToken, projectId, taskId,
                "{\"status\":\"DONE\"}"), "$.code"));
        assertEquals("DONE", jdbcTemplate.queryForObject(
                "SELECT status FROM project_task WHERE id = ?", String.class, taskId));
        assertEquals(80002L, jdbcTemplate.queryForObject(
                "SELECT completed_by FROM project_task WHERE id = ?", Long.class, taskId));
        //负责人改其他字段 → 7002（窄口：只有完成打卡）
        assertEquals(7002, (int) JsonPath.read(update(assigneeToken, projectId, taskId,
                "{\"title\":\"偷改标题\"}"), "$.code"));
        assertEquals(7002, (int) JsonPath.read(update(assigneeToken, projectId, taskId,
                "{\"priority\":\"HIGH\"}"), "$.code"));
        //非负责人学生 → 7002
        assertEquals(7002, (int) JsonPath.read(update(outsiderToken, projectId, taskId,
                "{\"status\":\"DONE\"}"), "$.code"));
    }

    @Test
    void ownerFullEditAndCompletionSemantics() throws Exception {
        long taskId = createTask(ownerToken, projectId, "{\"title\":\"初版\"}");
        //所有人全字段编辑
        assertEquals(0, (int) JsonPath.read(update(ownerToken, projectId, taskId,
                "{\"title\":\"二版\",\"description\":\"补充说明\",\"assigneeMemberId\":80002,"
                        + "\"priority\":\"HIGH\",\"sortNo\":5,\"status\":\"DOING\"}"), "$.code"));
        var row = jdbcTemplate.queryForMap(
                "SELECT title, description, priority, sort_no, status FROM project_task WHERE id = ?",
                taskId);
        assertEquals("二版", row.get("title"));
        assertEquals("DOING", row.get("status"));
        assertEquals(5, ((Number) row.get("sort_no")).intValue());
        //所有人置 DONE → completed 回填
        assertEquals(0, (int) JsonPath.read(update(ownerToken, projectId, taskId,
                "{\"status\":\"DONE\"}"), "$.code"));
        assertEquals(80001L, jdbcTemplate.queryForObject(
                "SELECT completed_by FROM project_task WHERE id = ?", Long.class, taskId));
        //DONE → TODO：完成列清空（updateById 写不了 NULL，手写 SQL 收口）
        assertEquals(0, (int) JsonPath.read(update(ownerToken, projectId, taskId,
                "{\"status\":\"TODO\"}"), "$.code"));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT completed_at FROM project_task WHERE id = ?",
                java.sql.Timestamp.class, taskId));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT completed_by FROM project_task WHERE id = ?", Long.class, taskId));
        //非法状态/优先级 → 1001
        assertEquals(1001, (int) JsonPath.read(update(ownerToken, projectId, taskId,
                "{\"status\":\"GONE\"}"), "$.code"));
        assertEquals(1001, (int) JsonPath.read(update(ownerToken, projectId, taskId,
                "{\"priority\":\"URGENT\"}"), "$.code"));
    }

    @Test
    void crossProjectTaskGuarded() throws Exception {
        //同步任务（milestone_node_id 非空）也可删：直插一条模拟节点同步任务
        jdbcTemplate.update("INSERT INTO project_task (id, project_id, milestone_node_id, title, "
                + "status) VALUES (70010, ?, 66666, '节点同步任务', 'TODO')", projectId);
        assertEquals(0, (int) JsonPath.read(mockMvc.perform(withToken(
                        delete("/api/projects/" + projectId + "/tasks/70010"), ownerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        //另一个项目的任务挂到本项目操作 → 7012（任务不存在或不属于该项目）
        long otherTask = createTask(ownerToken, otherProjectId, "{\"title\":\"旁项目任务\"}");
        assertEquals(7012, (int) JsonPath.read(update(ownerToken, projectId, otherTask,
                "{\"status\":\"DONE\"}"), "$.code"));
        assertEquals(7012, (int) JsonPath.read(mockMvc.perform(withToken(
                        delete("/api/projects/" + projectId + "/tasks/" + otherTask), ownerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        //不存在的任务 → 7012
        assertEquals(7012, (int) JsonPath.read(update(ownerToken, projectId, 99999L,
                "{\"status\":\"DONE\"}"), "$.code"));
    }
}
