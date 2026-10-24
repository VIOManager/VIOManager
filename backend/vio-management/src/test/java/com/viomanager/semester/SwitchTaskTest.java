package com.viomanager.semester;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import com.viomanager.common.auth.MaintenanceLock;
import com.viomanager.semester.entity.SemesterSwitchTask;
import com.viomanager.semester.mapper.SemesterSwitchTaskMapper;
import java.time.LocalDateTime;
import java.util.Map;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * A 线 Step 5 验证：学期切换 S05-S08（全链路最有分量的状态机）。
 * 固定探针：源学期 930001（当前）、目标 930002、备用目标 930003；
 * 比赛两条（FINISHED 930011 应归档 / RUNNING 930012 应继承）、
 * ACTIVE 赛队 930021 带一名成员 930031。
 * 每例先抹掉演示数据的 is_current 标记保证"当前学期"在测试事务内唯一，
 * 事务回滚后原样恢复。维护锁是 JVM 静态位，@AfterEach 兜底解锁防毒化后续用例。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SwitchTaskTest {

    private static final long FROM_SEMESTER = 930001L;
    private static final long TO_SEMESTER = 930002L;
    private static final long SPARE_SEMESTER = 930003L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SemesterSwitchTaskMapper switchTaskMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Resource
    private JdbcTemplate jdbcTemplate;

    private String superToken;

    private String studentToken;

    private Long superUserId;

    @BeforeEach
    void setUp() throws Exception {
        superToken = login("sw_probe_sa", "SUPER_ADMIN");
        studentToken = login("sw_probe_stu", "STUDENT");
        //1.当前学期唯一化：抹掉演示数据标记（事务内，回滚即恢复）
        jdbcTemplate.update("UPDATE semester SET is_current = 0 WHERE is_current = 1 AND deleted = 0");
        //2.源学期（当前 ACTIVE）与两个目标学期
        jdbcTemplate.update("INSERT INTO semester (id, name, status, is_current) VALUES (930001, '切换源学期', 'ACTIVE', 1)");
        jdbcTemplate.update("INSERT INTO semester (id, name, status, is_current) VALUES (930002, '切换目标学期', 'PREPARING', 0)");
        jdbcTemplate.update("INSERT INTO semester (id, name, status, is_current) VALUES (930003, '切换备用目标', 'PREPARING', 0)");
        //3.比赛：一条已结束（应归档）一条进行中（应继承）
        jdbcTemplate.update("INSERT INTO contest (id, name, semester_id, status) VALUES (930011, '探针已结束赛', 930001, 'FINISHED')");
        jdbcTemplate.update("INSERT INTO contest (id, name, semester_id, status) VALUES (930012, '探针进行中赛', 930001, 'RUNNING')");
        //4.ACTIVE 赛队 + 一名成员（org_id 非空列给 1，无外键约束）
        jdbcTemplate.update("INSERT INTO team (id, name, contest_id, semester_id, org_id, status) VALUES (930021, '探针继承队', 930012, 930001, 1, 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO team_member (id, team_id, member_id, member_name) VALUES (930031, 930021, 930032, '探针队员')");
    }

    @AfterEach
    void releaseMaintenanceLock() {
        MaintenanceLock.unlock();
    }

    @Test
    void createWithWrongPasswordRejected() throws Exception {
        // S05：超管密码二次确认失败 → 1001，不产生任务
        createTask(TO_SEMESTER, "wrong-password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM semester_switch_task WHERE from_semester_id = 930001 AND deleted = 0",
                Integer.class);
        Assertions.assertEquals(0, count, "密码校验失败不应产生切换任务");
    }

    @Test
    void createCapturesSnapshotAndWindow() throws Exception {
        // S05：正确密码 → PENDING_EXECUTE + 30 分钟窗口 + 影响快照三计数
        //（两侧各放宽 1 秒：DATETIME(0) 对小数秒是四舍五入——亚秒 < 0.5 被舍到整秒时，
        // 会落进带毫秒的严格下界之前。原注释只放宽了上界，下界漏了，约五五开偶发红——2026-10-24 根治）
        LocalDateTime before = LocalDateTime.now().plusMinutes(30).minusSeconds(1);
        long taskId = createTaskOk(TO_SEMESTER);
        LocalDateTime after = LocalDateTime.now().plusMinutes(30).plusSeconds(1);
        SemesterSwitchTask task = switchTaskMapper.selectById(taskId);
        Assertions.assertEquals("PENDING_EXECUTE", task.getStatus());
        Assertions.assertEquals(30, task.getWindowMinutes());
        Assertions.assertEquals(0, task.getVersion());
        Assertions.assertEquals("切换源学期", task.getFromSemesterName());
        Assertions.assertEquals("切换目标学期", task.getToSemesterName());
        Assertions.assertNotNull(task.getExecuteAt(), "应落窗口截止时刻");
        Assertions.assertFalse(task.getExecuteAt().isBefore(before) || task.getExecuteAt().isAfter(after),
                "截止时刻应为创建时刻加 30 分钟：executeAt=" + task.getExecuteAt()
                        + ", 窗口=[" + before + ", " + after + "]");
        //快照三计数按值断言（不比对字符串——序列化格式带不带空格不该影响契约）
        Map<String, Object> impact = JsonPath.read(task.getImpactSnapshot(), "$");
        Assertions.assertEquals(1, ((Number) impact.get("contestCount")).intValue(), "快照应含待归档比赛数");
        Assertions.assertEquals(1, ((Number) impact.get("teamCount")).intValue(), "快照应含待继承赛队数");
        Assertions.assertEquals(1, ((Number) impact.get("memberRelationCount")).intValue(), "快照应含成员关系数");
        Assertions.assertEquals(superUserId, task.getInitiatedBy());
    }

    @Test
    void secondPendingTaskRejected() throws Exception {
        // S05：同一时刻只允许一个待执行任务（换目标学期避开幂等键同参）
        createTaskOk(TO_SEMESTER);
        createTask(SPARE_SEMESTER, "pass123456")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void cancelWithinWindowThenRejectsRepeat() throws Exception {
        // S07：窗口内取消 → CANCELLED 且记取消人；已取消再取消 → 1006（换幂等键打状态机）
        long taskId = createTaskOk(TO_SEMESTER);
        mockMvc.perform(post("/api/semesters/switch-tasks/" + taskId + "/cancel")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        SemesterSwitchTask task = switchTaskMapper.selectById(taskId);
        Assertions.assertEquals("CANCELLED", task.getStatus());
        Assertions.assertEquals(superUserId, task.getCancelledBy());
        mockMvc.perform(post("/api/semesters/switch-tasks/" + taskId + "/cancel")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    void executeArchivesInheritsAndSucceeds() throws Exception {
        // S08 主线：归档源学期、激活目标、FINISHED 比赛归档、其余比赛与 ACTIVE 赛队继承、计数回填
        long taskId = createTaskOk(TO_SEMESTER);
        mockMvc.perform(post("/api/semesters/switch-tasks/" + taskId + "/execute")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        //1.任务收尾：SUCCESS + 三计数 + 完成时刻
        SemesterSwitchTask task = switchTaskMapper.selectById(taskId);
        Assertions.assertEquals("SUCCESS", task.getStatus());
        Assertions.assertEquals(1, task.getArchivedContests());
        Assertions.assertEquals(1, task.getInheritedTeams());
        Assertions.assertEquals(1, task.getInheritedMembers());
        Assertions.assertNotNull(task.getFinishedAt());
        //2.学期：源归档退位，目标激活上位
        Map<String, Object> from = semesterRow(FROM_SEMESTER);
        Map<String, Object> to = semesterRow(TO_SEMESTER);
        Assertions.assertEquals("ARCHIVED", from.get("status"));
        Assertions.assertEquals(0, ((Number) from.get("is_current")).intValue());
        Assertions.assertNotNull(from.get("archived_at"));
        Assertions.assertEquals("ACTIVE", to.get("status"));
        Assertions.assertEquals(1, ((Number) to.get("is_current")).intValue());
        //3.比赛分流：FINISHED 归档、RUNNING 继承到新学期
        Assertions.assertEquals("ARCHIVED", contestStatus(930011));
        Assertions.assertEquals(TO_SEMESTER, contestSemester(930012));
        //4.赛队继承 + 成员关系随队（team_member 不动行只计数）
        Assertions.assertEquals(TO_SEMESTER, jdbcTemplate.queryForObject(
                "SELECT semester_id FROM team WHERE id = 930021", Long.class));
        //5.维护锁必须已释放
        Assertions.assertFalse(MaintenanceLock.isLocked(), "执行完成后维护锁应释放");
    }

    @Test
    void executeExpiredWindowRejected() throws Exception {
        // S08：过截止时刻拒绝执行，任务保持待执行由发起人重发
        long taskId = createTaskOk(TO_SEMESTER);
        jdbcTemplate.update("UPDATE semester_switch_task SET execute_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(1), taskId);
        mockMvc.perform(post("/api/semesters/switch-tasks/" + taskId + "/execute")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
        Assertions.assertEquals("PENDING_EXECUTE", switchTaskMapper.selectById(taskId).getStatus());
    }

    @Test
    void engineFailureMarksTaskFailedAndRollsBack() throws Exception {
        // S08 失败路径：源学期当前标记被并发改掉 → 引擎条件更新 0 行抛冲突 →
        // 任务落 FAILED 带原因、引擎事务整体回滚（目标学期未激活）、维护锁仍释放
        long taskId = createTaskOk(TO_SEMESTER);
        jdbcTemplate.update("UPDATE semester SET is_current = 0 WHERE id = ?", FROM_SEMESTER);
        mockMvc.perform(post("/api/semesters/switch-tasks/" + taskId + "/execute")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000));
        SemesterSwitchTask task = switchTaskMapper.selectById(taskId);
        Assertions.assertEquals("FAILED", task.getStatus());
        Assertions.assertNotNull(task.getErrorMessage(), "失败原因必须落库");
        Map<String, Object> to = semesterRow(TO_SEMESTER);
        Assertions.assertEquals(0, ((Number) to.get("is_current")).intValue(), "引擎回滚后目标学期不得激活");
        Assertions.assertFalse(MaintenanceLock.isLocked(), "失败路径维护锁也必须释放");
    }

    @Test
    void maintenanceLockGatesNonSuperadmin() throws Exception {
        // 维护锁生效口径：非超管 503 + 1012（不触发 401 跳登录契约），超管放行观察
        MaintenanceLock.lock();
        try {
            mockMvc.perform(get("/api/semesters")
                            .header("Authorization", "Bearer " + studentToken))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value(1012));
            mockMvc.perform(get("/api/semesters")
                            .header("Authorization", "Bearer " + superToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        } finally {
            MaintenanceLock.unlock();
        }
    }

    // ---------- 支撑 ----------

    private String login(String loginName, String role) throws Exception {
        SysUser user = new SysUser();
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole(role);
        user.setRealName("切换探针-" + role);
        user.setDefaultPassword(0);
        user.setStatus(1);
        sysUserMapper.insert(user);
        if ("SUPER_ADMIN".equals(role)) {
            superUserId = user.getId();
        }
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"pass123456\"}"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    /** 发起切换，返回断言链供调用方补断言 */
    private org.springframework.test.web.servlet.ResultActions createTask(
            long toSemesterId, String password) throws Exception {
        return mockMvc.perform(post("/api/semesters/switch-tasks")
                .header("Authorization", "Bearer " + superToken)
                .header("X-Idempotency-Key", "k" + System.nanoTime())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toSemesterId\":\"" + toSemesterId + "\",\"password\":\"" + password + "\"}"));
    }

    private long createTaskOk(long toSemesterId) throws Exception {
        MvcResult result = createTask(toSemesterId, "pass123456")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return Long.parseLong(JsonPath.read(result.getResponse().getContentAsString(), "$.data"));
    }

    private Map<String, Object> semesterRow(long id) {
        return jdbcTemplate.queryForMap(
                "SELECT status, is_current, archived_at FROM semester WHERE id = ?", id);
    }

    private String contestStatus(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM contest WHERE id = ?", String.class, id);
    }

    private Long contestSemester(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT semester_id FROM contest WHERE id = ?", Long.class, id);
    }
}
