package com.viomanager.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * M6 并发测试套件（方案 §7.7 矩阵直测）：双耗 / 挂载防重 / 状态 CAS。
 * ⚠️ 不带 @Transactional——子线程里 service 开独立事务真实提交，主线程回滚不了；
 * 靠独立 ID 段（org 931x / semester 95011 / member 811xx / team 99801 / 项目名 PMC- 前缀）
 * + @AfterEach 按特征清理。不走审批接口（避免幂等键与审批单残留），grant/ref 直插。
 */
class PmConcurrencyTest extends PmTestSupport {

    private static final Long ORG_A = 9311L;
    private static final Long SEMESTER = 95011L;
    private static final Long TEAM = 99801L;
    private static final Long OWNER = 81101L;
    private static final Long OUTSIDER = 81102L;

    private String ownerToken;
    private String outsiderToken;

    @BeforeEach
    void setUp() throws Exception {
        insertOrg(ORG_A, "PMC软件学院", "/0/9311/");
        //直插学期（不走 insertCurrentSemester——它会 UPDATE 全表 is_current，无事务会污染 dev 库）
        jdbcTemplate.update("INSERT INTO semester (id, name, status, is_current) "
                + "VALUES (?, 'PMC并发测试学期', 'ACTIVE', 0)", SEMESTER);
        insertMember(OWNER, "PMC所有人", ORG_A, "STUDENT");
        insertMember(OUTSIDER, "PMC外人", ORG_A, "STUDENT");
        ownerToken = createUserAndLogin("pmc_owner", "STUDENT", ORG_A, OWNER);
        outsiderToken = createUserAndLogin("pmc_outsider", "STUDENT", ORG_A, OUTSIDER);
    }

    @AfterEach
    void cleanUp() {
        //子表 → 主表，按独立 ID 段/前缀特征清理（无事务，必须手动）
        jdbcTemplate.update("DELETE FROM project_task WHERE project_id IN "
                + "(SELECT id FROM project WHERE name LIKE 'PMC-%')");
        jdbcTemplate.update("DELETE FROM team_project_ref WHERE team_id = " + TEAM);
        jdbcTemplate.update("DELETE FROM project_view_grant WHERE applicant_id IN (?, ?) "
                + "OR project_id IN (SELECT id FROM project WHERE name LIKE 'PMC-%')", OWNER, OUTSIDER);
        jdbcTemplate.update("DELETE FROM project_member WHERE member_id IN (?, ?) "
                + "OR project_id IN (SELECT id FROM project WHERE name LIKE 'PMC-%')", OWNER, OUTSIDER);
        jdbcTemplate.update("DELETE FROM project WHERE name LIKE 'PMC-%'");
        jdbcTemplate.update("DELETE FROM team WHERE id = " + TEAM);
        jdbcTemplate.update("DELETE FROM semester WHERE id = " + SEMESTER);
        jdbcTemplate.update("DELETE FROM member WHERE id IN (?, ?)", OWNER, OUTSIDER);
        jdbcTemplate.update("DELETE FROM sys_user WHERE login_name LIKE 'pmc\\_%'");
        jdbcTemplate.update("DELETE FROM sys_org WHERE id = " + ORG_A);
        jdbcTemplate.update("DELETE FROM audit_log WHERE login_name LIKE 'pmc\\_%'");
    }

    private long createProject(String visibility) throws Exception {
        String body = createProject(ownerToken,
                "{\"name\":\"PMC-并发项目-" + System.nanoTime() + "\",\"visibility\":\"" + visibility
                        + "\",\"semesterId\":" + SEMESTER + ",\"orgId\":" + ORG_A + "}");
        assertEquals(0, (int) JsonPath.read(body, "$.code"), body);
        return Long.parseLong(JsonPath.read(body, "$.data.id"));
    }

    /** N 线程并发执行 HTTP 调用（CountDownLatch 对齐起跑），收集响应 code */
    private List<Integer> concurrent(int threads, ConcurrentCall call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return call.run();
            }));
        }
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> future : futures) {
            codes.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
        return codes;
    }

    @FunctionalInterface
    private interface ConcurrentCall {
        int run() throws Exception;
    }

    // ---------------------------------------------------------------- 双耗（§7.7）

    @Test
    void concurrentOnceDetailConsumesExactlyOnce() throws Exception {
        long projectId = createProject("PRIVATE");
        //直插 ACTIVE ONCE 授权（避开审批接口的幂等键残留）
        jdbcTemplate.update("INSERT INTO project_view_grant (id, project_id, applicant_id, "
                        + "applicant_name, grant_type, used_count, status) "
                        + "VALUES (79001, ?, ?, 'PMC外人', 'ONCE', 0, 'ACTIVE')",
                projectId, OUTSIDER);
        //2 线程并发详情：条件 UPDATE 只放一个（另一个 7003），used_count 恰好 1
        List<Integer> codes = concurrent(2, () -> (int) JsonPath.read(mockMvc.perform(
                        withToken(get("/api/projects/" + projectId), outsiderToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        assertEquals(1, codes.stream().filter(c -> c == 0).count(), "恰好一次成功: " + codes);
        assertEquals(1, codes.stream().filter(c -> c == 7003).count(), "另一次被拒: " + codes);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT used_count FROM project_view_grant WHERE id = 79001", Integer.class),
                "只消耗一次");
    }

    // ---------------------------------------------------------------- 挂载防重（§7.7）

    @Test
    void concurrentAttachCreatesSingleMount() throws Exception {
        long projectId = createProject("ORG");
        jdbcTemplate.update("INSERT INTO team (id, name, contest_id, semester_id, org_id, "
                        + "leader_member_id, status) VALUES (?, 'PMC并发队', 1, ?, ?, ?, 'ACTIVE')",
                TEAM, SEMESTER, ORG_A, OWNER);
        //8 线程并发挂载（无赛道——uk 对 NULL 失效，靠 team 行锁 + 先查后插防双挂载）
        List<Integer> codes = concurrent(8, () -> (int) JsonPath.read(mockMvc.perform(
                        withToken(post("/api/teams/" + TEAM + "/projects"), ownerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"projectId\":" + projectId + "}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        assertEquals(1, codes.stream().filter(c -> c == 0).count(), "恰好一次挂载成功: " + codes);
        assertEquals(7, codes.stream().filter(c -> c == 7007).count(), "其余全部 7007: " + codes);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_project_ref WHERE team_id = ? AND project_id = ? "
                        + "AND deleted = 0", Integer.class, TEAM, projectId), "只有一条挂载行");
    }

    // ---------------------------------------------------------------- 状态 CAS（§7.7）

    @Test
    void concurrentStatusUpdateSingleWinner() throws Exception {
        long projectId = createProject("ORG");
        //2 线程并发置 FINISHED：casStatus 条件更新只有一个赢家，另一个 1007
        List<Integer> codes = concurrent(2, () -> (int) JsonPath.read(mockMvc.perform(
                        withToken(put("/api/projects/" + projectId), ownerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"FINISHED\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        assertEquals(1, codes.stream().filter(c -> c == 0).count(), "恰好一次流转成功: " + codes);
        assertEquals(1, codes.stream().filter(c -> c == 1007).count(), "另一个 1007: " + codes);
        assertEquals("FINISHED", jdbcTemplate.queryForObject(
                "SELECT status FROM project WHERE id = ?", String.class, projectId));
    }
}
