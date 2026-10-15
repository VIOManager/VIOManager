package com.viomanager.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;

/**
 * M3 赛队挂载测试（PR11–PR15）：队长校验 1003、同学期 7008、同队同项目同赛道防重 7007
 * （含可空赛道的 NULL 比较）、解绑置 RELEASED、同键再挂载复用行（uk 约束下不撞键）、
 * 镜像列表双向可见性、可挂载池口径（PUBLIC/ORG 同部门/参与，授权不进池）。
 * B 线 team/team_member/contest_track 行用 jdbcTemplate 直插（M3 只读 B 线数据）。
 */
@Transactional
class TeamProjectTest extends PmTestSupport {

    private static final Long ORG_SOFT = 9301L;
    private static final Long ORG_MECH = 9302L;
    private static final Long SEMESTER = 95001L;
    private static final Long SEMESTER_OLD = 95002L;
    private static final Long CONTEST = 88001L;
    private static final Long TRACK_EMB = 88101L;
    private static final Long TRACK_AI = 88102L;
    private static final Long TEAM = 99901L;
    private static final Long TEAM_MECH = 99902L;

    private String ownerToken;
    private String leaderToken;
    private String teammateToken;
    private String outsiderToken;
    private String deptSoftToken;
    private long projectId;

    @BeforeEach
    void setUp() throws Exception {
        insertOrg(ORG_SOFT, "软件学院", "/0/9301/");
        insertOrg(ORG_MECH, "机械学院", "/0/9302/");
        insertCurrentSemester(SEMESTER, "2026-2027学年第一学期-PM挂载测试");
        jdbcTemplate.update("INSERT INTO semester (id, name, status, is_current) VALUES (?, ?, 'ARCHIVED', 0)",
                SEMESTER_OLD, "2025-2026学年-PM挂载测试");
        //B 线：比赛 + 两条赛道 + 软件学院队（队长 80002）+ 机械学院队
        jdbcTemplate.update("INSERT INTO contest (id, name, semester_id, org_id, status) "
                + "VALUES (?, '蓝桥杯', ?, ?, 'RUNNING')", CONTEST, SEMESTER, ORG_SOFT);
        jdbcTemplate.update("INSERT INTO contest_track (id, contest_id, name, status) "
                + "VALUES (?, ?, '嵌入式组', 1)", TRACK_EMB, CONTEST);
        jdbcTemplate.update("INSERT INTO contest_track (id, contest_id, name, status) "
                + "VALUES (?, ?, 'AI组', 1)", TRACK_AI, CONTEST);
        insertTeam(TEAM, "软院一队", CONTEST, TRACK_EMB, SEMESTER, ORG_SOFT, 80002L);
        insertTeam(TEAM_MECH, "机械一队", CONTEST, null, SEMESTER, ORG_MECH, 80003L);
        insertMember(80001L, "项目A", ORG_SOFT, "STUDENT");
        insertMember(80002L, "队长B", ORG_SOFT, "STUDENT");
        insertMember(80003L, "队员C", ORG_MECH, "STUDENT");
        insertMember(80004L, "外人D", ORG_MECH, "STUDENT");
        //项目（ORG，软件学院，当前学期）
        ownerToken = createUserAndLogin("pm3_owner", "STUDENT", ORG_SOFT, 80001L);
        String body = createProject(ownerToken,
                "{\"name\":\"挂载验证项目\",\"visibility\":\"ORG\",\"contestId\":" + CONTEST + "}");
        assertEquals(0, (int) JsonPath.read(body, "$.code"), body);
        projectId = Long.parseLong(JsonPath.read(body, "$.data.id"));
        //身份：软院一队队长（80002，挂载操作人）、队友（80004？不，队友设为同队的机械学生 80003 不合理——
        //另造队员 80005 在软院一队）、外部门学生、部门管理员
        insertMember(80005L, "队友E", ORG_SOFT, "STUDENT");
        jdbcTemplate.update("INSERT INTO team_member (id, team_id, member_id, member_name, is_leader, status) "
                + "VALUES (70001, ?, 80002, '队长B', 1, 'CONFIRMED')", TEAM);
        jdbcTemplate.update("INSERT INTO team_member (id, team_id, member_id, member_name, is_leader, status) "
                + "VALUES (70002, ?, 80005, '队友E', 0, 'CONFIRMED')", TEAM);
        leaderToken = createUserAndLogin("pm3_leader", "STUDENT", ORG_SOFT, 80002L);
        teammateToken = createUserAndLogin("pm3_teammate", "STUDENT", ORG_SOFT, 80005L);
        outsiderToken = createUserAndLogin("pm3_outsider", "STUDENT", ORG_MECH, 80004L);
        deptSoftToken = createUserAndLogin("pm3_dept_soft", "DEPT_ADMIN", ORG_SOFT, null);
    }

    private void insertTeam(Long id, String name, Long contestId, Long trackId, Long semesterId,
                            Long orgId, Long leaderId) {
        jdbcTemplate.update("INSERT INTO team (id, name, contest_id, track_id, semester_id, org_id, "
                + "leader_member_id, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                id, name, contestId, trackId, semesterId, orgId, leaderId);
    }

    /** 挂载请求；不预断言 HTTP 状态——1003（含非队长拒绝）由骨架映射为 HTTP 403，其余业务码 200 */
    private String attach(String token, long teamId, String body) throws Exception {
        return mockMvc.perform(withToken(post("/api/teams/" + teamId + "/projects"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String detach(String token, long teamId, long projectId) throws Exception {
        return mockMvc.perform(withToken(delete(
                        "/api/teams/" + teamId + "/projects/" + projectId), token))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String teamsOfProject(String token, long projectId) throws Exception {
        return mockMvc.perform(withToken(get("/api/projects/" + projectId + "/teams"), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String projectsOfTeam(String token, long teamId) throws Exception {
        //1003（非本队成员）由骨架映射为 HTTP 403，不预断言状态
        return mockMvc.perform(withToken(get("/api/teams/" + teamId + "/projects"), token))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    // ---------------------------------------------------------------- PR13 校验链

    @Test
    void leaderAttachesProjectEndToEnd() throws Exception {
        //队长挂载成功（带赛道）
        assertEquals(0, (int) JsonPath.read(
                attach(leaderToken, TEAM,
                        "{\"projectId\":" + projectId + ",\"trackId\":" + TRACK_EMB + "}"),
                "$.code"));
        //落库：冗余列 contest/semester 来自赛队
        assertEquals(CONTEST, jdbcTemplate.queryForObject(
                "SELECT contest_id FROM team_project_ref WHERE team_id = ? AND project_id = ? "
                        + "AND deleted = 0", Long.class, TEAM, projectId));
        assertEquals(SEMESTER, jdbcTemplate.queryForObject(
                "SELECT semester_id FROM team_project_ref WHERE team_id = ? AND project_id = ? "
                        + "AND deleted = 0", Long.class, TEAM, projectId));
    }

    @Test
    void nonLeaderCannotAttachOrDetach() throws Exception {
        //非队长（项目所有人也不是队长）挂载 → 1003（骨架将 1003 映射为 HTTP 403）
        String denied = attach(ownerToken, TEAM, "{\"projectId\":" + projectId + "}");
        assertEquals(1003, (int) JsonPath.read(denied, "$.code"), denied);
        //队里的普通成员也不行
        assertEquals(1003, (int) JsonPath.read(
                attach(teammateToken, TEAM, "{\"projectId\":" + projectId + "}"), "$.code"));
        //解绑同样仅队长
        assertEquals(1003, (int) JsonPath.read(
                detach(teammateToken, TEAM, projectId), "$.code"));
        //赛队不存在 → 1004
        assertEquals(1004, (int) JsonPath.read(
                attach(leaderToken, 88888L, "{\"projectId\":" + projectId + "}"), "$.code"));
    }

    @Test
    void semesterMismatchRejected() throws Exception {
        //项目在当前学期，旧学期队伍挂载 → 7008
        insertTeam(99903L, "老学期队", CONTEST, null, SEMESTER_OLD, ORG_SOFT, 80002L);
        assertEquals(7008, (int) JsonPath.read(
                attach(leaderToken, 99903L, "{\"projectId\":" + projectId + "}"), "$.code"));
    }

    @Test
    void duplicateActiveMountRejectedIncludingNullTrack() throws Exception {
        //无赛道挂载
        assertEquals(0, (int) JsonPath.read(
                attach(leaderToken, TEAM, "{\"projectId\":" + projectId + "}"), "$.code"));
        //同队同项目同赛道（都无赛道，NULL=NULL）再挂 → 7007（uk 对 NULL 不生效，靠先查后插）
        assertEquals(7007, (int) JsonPath.read(
                attach(leaderToken, TEAM, "{\"projectId\":" + projectId + "}"), "$.code"));
        //不同赛道不冲突（uk 生效域内合法）
        assertEquals(0, (int) JsonPath.read(
                attach(leaderToken, TEAM,
                        "{\"projectId\":" + projectId + ",\"trackId\":" + TRACK_EMB + "}"),
                "$.code"));
        //同部门另一队（一个项目可同时被多支赛队挂载打不同比赛）→ 成功
        insertTeam(99904L, "软院二队", CONTEST, null, SEMESTER, ORG_SOFT, 80002L);
        assertEquals(0, (int) JsonPath.read(
                attach(leaderToken, 99904L, "{\"projectId\":" + projectId + "}"), "$.code"));
        //跨部门队挂 ORG 项目 → 队长不可见 → 7003（规则 6 不跨部门）
        String mechLeader = createUserAndLogin("pm3_mech_leader", "STUDENT", ORG_MECH, 80003L);
        assertEquals(7003, (int) JsonPath.read(
                attach(mechLeader, TEAM_MECH, "{\"projectId\":" + projectId + "}"), "$.code"));
    }

    @Test
    void invalidTrackRejected() throws Exception {
        //不属于该比赛的赛道 → 1001
        assertEquals(1001, (int) JsonPath.read(
                attach(leaderToken, TEAM, "{\"projectId\":" + projectId + ",\"trackId\":77777}"),
                "$.code"));
    }

    // ---------------------------------------------------------------- 解绑与复用

    @Test
    void detachThenReattachReusesRow() throws Exception {
        assertEquals(0, (int) JsonPath.read(
                attach(leaderToken, TEAM, "{\"projectId\":" + projectId + "}"), "$.code"));
        //解绑 → RELEASED + released_at
        assertEquals(0, (int) JsonPath.read(detach(leaderToken, TEAM, projectId), "$.code"));
        assertEquals("RELEASED", jdbcTemplate.queryForObject(
                "SELECT status FROM team_project_ref WHERE team_id = ? AND project_id = ? AND deleted = 0",
                String.class, TEAM, projectId));
        //已解绑的再解 → 1004
        assertEquals(1004, (int) JsonPath.read(detach(leaderToken, TEAM, projectId), "$.code"));
        //同键再挂载：复用 RELEASED 行置回 ACTIVE（uk 同键活行仅一行，不撞键）
        assertEquals(0, (int) JsonPath.read(
                attach(leaderToken, TEAM, "{\"projectId\":" + projectId + "}"), "$.code"));
        assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                "SELECT status FROM team_project_ref WHERE team_id = ? AND project_id = ? AND deleted = 0",
                String.class, TEAM, projectId));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT released_at FROM team_project_ref WHERE team_id = ? AND project_id = ? AND deleted = 0",
                java.sql.Timestamp.class, TEAM, projectId), "复用行须清掉旧解绑时间");
        //全程只有一行（复用而非新增）
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_project_ref WHERE team_id = ? AND project_id = ? AND deleted = 0",
                Integer.class, TEAM, projectId));
    }

    // ---------------------------------------------------------------- 镜像列表

    @Test
    void mirrorListsRespectVisibility() throws Exception {
        assertEquals(0, (int) JsonPath.read(
                attach(leaderToken, TEAM, "{\"projectId\":" + projectId + "}"), "$.code"));
        //PR11 项目侧（项目可见者可看）：所有人看到赛队信息
        String projectView = teamsOfProject(ownerToken, projectId);
        assertEquals(0, (int) JsonPath.read(projectView, "$.code"));
        assertEquals("软院一队", JsonPath.read(projectView, "$.data[0].teamName"));
        assertEquals("蓝桥杯", JsonPath.read(projectView, "$.data[0].contestName"));
        //项目不可见者（外部门学生）→ 7003
        assertEquals(7003, (int) JsonPath.read(teamsOfProject(outsiderToken, projectId), "$.code"));
        //PR12 赛队侧：队长/队友可看
        String teamView = projectsOfTeam(leaderToken, TEAM);
        assertEquals(0, (int) JsonPath.read(teamView, "$.code"));
        assertTrue(teamView.contains("挂载验证项目"), teamView);
        assertEquals(0, (int) JsonPath.read(projectsOfTeam(teammateToken, TEAM), "$.code"));
        //部门管理员（赛队组织在其子树内）可看；外人不可看 → 1003
        assertEquals(0, (int) JsonPath.read(projectsOfTeam(deptSoftToken, TEAM), "$.code"));
        assertEquals(1003, (int) JsonPath.read(projectsOfTeam(outsiderToken, TEAM), "$.code"));
    }

    // ---------------------------------------------------------------- 可挂载池

    @Test
    void attachablePoolScope() throws Exception {
        //同部门 ORG 项目（在池）+ PUBLIC 项目（在池）+ 外部门 ORG 项目（不在池）
        String publicBody = createProject(ownerToken, "{\"name\":\"公开池项目\",\"visibility\":\"PUBLIC\"}");
        assertEquals(0, (int) JsonPath.read(publicBody, "$.code"));
        long publicId = Long.parseLong(JsonPath.read(publicBody, "$.data.id"));
        String mechBody = createProject(outsiderToken,
                "{\"name\":\"机械池项目\",\"visibility\":\"ORG\",\"orgId\":" + ORG_MECH + "}");
        assertEquals(0, (int) JsonPath.read(mechBody, "$.code"));
        //队长视角（软件学院）：两个都在池；机械 ORG 项目不在
        String pool = mockMvc.perform(withToken(get("/api/projects/attachable"), leaderToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertEquals(0, (int) JsonPath.read(pool, "$.code"));
        assertTrue(pool.contains("挂载验证项目"), pool);
        assertTrue(pool.contains("公开池项目"), pool);
        assertTrue(!pool.contains("机械池项目"), "外部门 ORG 项目不进池: " + pool);
        //机械学院队长视角：自己部门的 ORG 项目在池，软件学院 ORG 不在，PUBLIC 在
        String mechPool = mockMvc.perform(withToken(get("/api/projects/attachable"), outsiderToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(mechPool.contains("机械池项目"), mechPool);
        assertTrue(!mechPool.contains("挂载验证项目"), "外部门 ORG 项目不进池: " + mechPool);
        assertTrue(mechPool.contains("公开池项目"), mechPool);
        //contestId 筛选：只留带比赛的项目
        String filtered = mockMvc.perform(withToken(get("/api/projects/attachable")
                        .queryParam("contestId", String.valueOf(CONTEST)), leaderToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(filtered.contains("挂载验证项目"), filtered);
        assertTrue(!filtered.contains("公开池项目"), "无比赛项目被筛掉: " + filtered);
        //参与的口径：机械学生参与软件项目（成员）后，该 ORG 项目进他的池
        jdbcTemplate.update("INSERT INTO project_member (id, project_id, member_id, member_name, "
                + "project_role) VALUES (70003, ?, 80004, '外人D', 'MEMBER')", projectId);
        String joinedPool = mockMvc.perform(withToken(get("/api/projects/attachable"), outsiderToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(joinedPool.contains("挂载验证项目"), "参与后进池: " + joinedPool);
    }
}
