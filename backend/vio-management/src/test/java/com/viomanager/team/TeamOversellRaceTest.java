package com.viomanager.team;

import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.TeamErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.team.dto.TeamCreateDTO;
import com.viomanager.team.dto.TeamMemberConfirmDTO;
import com.viomanager.team.handler.TeamAddMemberHandler;
import com.viomanager.team.service.ITeamCommandService;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 组队超卖修复（方案 §12.5）的并发验证。
 * 刻意不继承 @Transactional 基类：竞态必须让每个线程真实提交才存在，测试结束手工清场。
 * 依赖本机 Docker Redis（TeamLockSupport 懒连接）；行锁兜底层随 TransactionTemplate 真实生效。
 */
@SpringBootTest
class TeamOversellRaceTest {

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private TeamAddMemberHandler handler;
    @Resource
    private ITeamCommandService commandService;
    @Resource
    private PlatformTransactionManager txManager;

    private long seq;
    private long templateId;
    private long contestId;
    private long trackId;
    private long teamId;
    private long orgId;

    @BeforeEach
    void setUp() {
        seq = System.nanoTime() % 100_000_000L;
        templateId = 700_000_000_000L + seq;
        contestId = 700_000_000_001L + seq;
        trackId = 700_000_000_002L + seq;
        teamId = 700_000_000_003L + seq;
        orgId = 700_000_000_004L + seq;
        jdbcTemplate.update(
                "INSERT INTO sys_org (id, parent_id, name, org_type, path) VALUES (?, 0, ?, 'DEPARTMENT', ?)",
                orgId, "竞态学院" + seq, "/0/" + orgId + "/");
        jdbcTemplate.update(
                "INSERT INTO contest_template (id, name, code, category, default_rules, status, sort_no, deleted) "
                        + "VALUES (?, ?, ?, ?, ?, 1, 0, 0)",
                templateId, "竞态模板" + seq, "RC" + seq, "电控",
                "{\"memberLimit\":2,\"prefillLimit\":2,\"allowRepeatJoin\":false,"
                        + "\"crossTeamLeader\":false,\"maxAdvisorCount\":2}");
        jdbcTemplate.update(
                "INSERT INTO contest (id, name, template_id, semester_id, org_id, status, source_type, deleted) "
                        + "VALUES (?, ?, ?, 999, NULL, 'REGISTERING', 'MANUAL', 0)",
                contestId, "竞态赛" + seq, templateId);
        jdbcTemplate.update(
                "INSERT INTO contest_track (id, contest_id, name, code, sort_no, status, deleted) "
                        + "VALUES (?, ?, ?, ?, 0, 1, 0)", trackId, contestId, "竞态赛道", "RCK");
        jdbcTemplate.update(
                "INSERT INTO team (id, name, contest_id, track_id, semester_id, org_id, leader_member_id, status) "
                        + "VALUES (?, ?, ?, ?, 999, ?, NULL, 'ACTIVE')",
                teamId, "竞态队" + seq, contestId, trackId, orgId);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM team_member WHERE team_id IN (?, ?)", teamId, teamId + 1);
        jdbcTemplate.update("DELETE FROM team WHERE id IN (?, ?)", teamId, teamId + 1);
        // 直建竞态用例的队伍是雪花 id，按比赛清（含其队员行）
        jdbcTemplate.update(
                "DELETE FROM team_member WHERE team_id IN (SELECT id FROM team WHERE contest_id = ?)", contestId);
        jdbcTemplate.update("DELETE FROM team WHERE contest_id = ?", contestId);
        jdbcTemplate.update("DELETE FROM contest_track WHERE contest_id = ?", contestId);
        jdbcTemplate.update("DELETE FROM contest WHERE id = ?", contestId);
        jdbcTemplate.update("DELETE FROM contest_template WHERE id = ?", templateId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", 600_000_000_000L + seq);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id BETWEEN ? AND ?",
                600_000_100_000L + seq * 10, 600_000_100_000L + seq * 10 + 9);
        jdbcTemplate.update("DELETE FROM member WHERE id = ?", 800_000_200_000L + seq);
        jdbcTemplate.update("DELETE FROM member WHERE id BETWEEN ? AND ?",
                800_000_300_000L + seq * 10, 800_000_300_000L + seq * 10 + 9);
        jdbcTemplate.update("DELETE FROM sys_org WHERE id = ?", orgId);
    }

    @Test
    void 六人并发抢两席位_恰好两个进队() throws Exception {
        int threads = 6;
        List<Future<Throwable>> futures = runConcurrent(threads, i ->
                "{\"teamId\":" + teamId + ",\"memberId\":" + (800_000_000_000L + seq * 10 + i)
                        + ",\"memberName\":\"竞态员" + i + "\"}");
        int rejected = 0;
        for (Future<Throwable> f : futures) {
            Throwable t = f.get(60, TimeUnit.SECONDS);
            if (t instanceof BizException b && b.getCode() == TeamErrorCode.MEMBER_LIMIT_EXCEEDED.getCode()) {
                rejected++;
            } else if (t != null) {
                fail("并发加人出现预期外异常: " + t);
            }
        }
        Integer active = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_member WHERE team_id = ? AND deleted = 0 "
                        + "AND status IN ('UNCONFIRMED','CONFIRMED')", Integer.class, teamId);
        assertEquals(2, active, "memberLimit=2，6 人并发只允许 2 人进队（修复前会 6 人全进）");
        assertEquals(4, rejected, "其余 4 人应被人数超上限拦下");
    }

    @Test
    void 同人并发双发_只进一次() throws Exception {
        long memberId = 800_000_100_000L + seq;
        List<Future<Throwable>> futures = runConcurrent(2, i ->
                "{\"teamId\":" + teamId + ",\"memberId\":" + memberId + ",\"memberName\":\"双发员\"}");
        int rejected = 0;
        for (Future<Throwable> f : futures) {
            Throwable t = f.get(60, TimeUnit.SECONDS);
            if (t instanceof BizException b && b.getCode() == TeamErrorCode.TARGET_IN_TEAM_OR_PENDING.getCode()) {
                rejected++;
            } else if (t != null) {
                fail("并发同人加队出现预期外异常: " + t);
            }
        }
        Integer active = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_member WHERE team_id = ? AND member_id = ? AND deleted = 0",
                Integer.class, teamId, memberId);
        assertEquals(1, active, "同一人并发加队只应进一次");
        assertEquals(1, rejected, "第二次应被已在队拦下（uk_team_member 兜底）");
    }

    @Test
    void 跨队并发确认_只成一个() throws Exception {
        long memberId = 800_000_200_000L + seq;
        long teamB = teamId + 1;
        long userId = 600_000_000_000L + seq;
        jdbcTemplate.update(
                "INSERT INTO team (id, name, contest_id, track_id, semester_id, org_id, leader_member_id, status) "
                        + "VALUES (?, ?, ?, ?, 999, ?, NULL, 'ACTIVE')",
                teamB, "竞态B队" + seq, contestId, trackId, orgId);
        // member 行必须在：跨队防线的行为锁就锁在它身上，缺行=防线静默失效（本测试第一次翻车的原因）
        jdbcTemplate.update(
                "INSERT INTO member (id, name, org_id, member_type) VALUES (?, ?, ?, 'STUDENT')",
                memberId, "跨队员", orgId);
        insertUnconfirmed(teamId, memberId, "跨队员");
        insertUnconfirmed(teamB, memberId, "跨队员");
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, login_name, password, role, org_id, member_id, real_name, "
                        + "default_password, status, deleted) VALUES (?, ?, 'x', 'STUDENT', ?, ?, '竞态本人', 0, 1, 0)",
                userId, "race_user" + seq, orgId, memberId);

        int threads = 2;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Throwable>> futures = new ArrayList<>();
        long[] teams = {teamId, teamB};
        for (int i = 0; i < threads; i++) {
            final long target = teams[i];
            futures.add(pool.submit((Callable<Throwable>) () -> {
                barrier.await();
                CurrentUser u = new CurrentUser();
                u.setUserId(userId);
                u.setRole("STUDENT");
                u.setLoginName("race_user" + seq);
                UserHolder.saveUser(u);
                try {
                    TeamMemberConfirmDTO dto = new TeamMemberConfirmDTO();
                    dto.setAction("CONFIRM");
                    commandService.confirmMember(target, memberId, dto);
                    return null;
                } catch (Throwable e) {
                    return e;
                } finally {
                    UserHolder.removeUser();
                }
            }));
        }
        int rejected = 0;
        for (Future<Throwable> f : futures) {
            Throwable t = f.get(60, TimeUnit.SECONDS);
            if (t instanceof BizException b && b.getCode() == TeamErrorCode.REPEAT_JOIN_LIMITED.getCode()) {
                rejected++;
            } else if (t != null) {
                fail("并发确认出现预期外异常: " + t);
            }
        }
        pool.shutdownNow();
        Integer confirmed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_member WHERE member_id = ? AND status = 'CONFIRMED' AND deleted = 0",
                Integer.class, memberId);
        assertEquals(1, confirmed, "allowRepeatJoin=false：两支队并发确认只成一个（修复前两个都成）");
        assertEquals(1, rejected, "另一个确认应被跨队限制拦下");
    }

    @Test
    void 并发同名直建_只成一队() throws Exception {
        // 2026-10-24 队长直建拍板后，K4 同名竞态从"审批回调"搬到"提交即建"路径，
        // 锁比赛行 + 锁内当前读查重必须仍只放行一个（PR#51 加固逻辑平移的回归位）
        int threads = 3;
        String name = "竞态直建队" + seq;
        for (int i = 0; i < threads; i++) {
            long memberId = 800_000_300_000L + seq * 10 + i;
            long userId = 600_000_100_000L + seq * 10 + i;
            jdbcTemplate.update(
                    "INSERT INTO member (id, name, org_id, member_type) VALUES (?, ?, ?, 'STUDENT')",
                    memberId, "直建员" + i, orgId);
            jdbcTemplate.update(
                    "INSERT INTO sys_user (id, login_name, password, role, org_id, member_id, real_name, "
                            + "default_password, status, deleted) VALUES (?, ?, 'x', 'STUDENT', ?, ?, '直建员', 0, 1, 0)",
                    userId, "race_create" + seq + "_" + i, orgId, memberId);
        }
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Throwable>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final long userId = 600_000_100_000L + seq * 10 + i;
            futures.add(pool.submit((Callable<Throwable>) () -> {
                barrier.await();
                CurrentUser u = new CurrentUser();
                u.setUserId(userId);
                u.setRole("STUDENT");
                u.setLoginName("race_create");
                u.setOrgId(orgId);
                UserHolder.saveUser(u);
                try {
                    TeamCreateDTO dto = new TeamCreateDTO();
                    dto.setContestId(contestId);
                    dto.setTrackId(trackId);
                    dto.setName(name);
                    dto.setProjectName("竞态项目");
                    commandService.createTeam(dto);
                    return null;
                } catch (Throwable e) {
                    return e;
                } finally {
                    UserHolder.removeUser();
                }
            }));
        }
        int created = 0;
        int rejected = 0;
        for (Future<Throwable> f : futures) {
            Throwable t = f.get(60, TimeUnit.SECONDS);
            if (t == null) {
                created++;
            } else if (t instanceof BizException b && b.getCode() == TeamErrorCode.TEAM_NAME_EXISTS.getCode()) {
                rejected++;
            } else {
                fail("并发直建出现预期外异常: " + t);
            }
        }
        pool.shutdownNow();
        Integer sameName = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team WHERE contest_id = ? AND name = ? AND deleted = 0",
                Integer.class, contestId, name);
        assertEquals(1, sameName, "同名并发直建库里只应有一行（修复前会双双建队）");
        assertEquals(1, created, "只允许一个请求建成");
        assertEquals(threads - 1, rejected, "其余应被队名唯一 4002 拦下");
    }

    /** 并发直调 handler（每线程独立事务，模拟两个管理员同时批单的生产时序） */
    private List<Future<Throwable>> runConcurrent(int threads, java.util.function.IntFunction<String> payloadOf)
            throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Throwable>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final String payload = payloadOf.apply(i);
            futures.add(pool.submit((Callable<Throwable>) () -> {
                barrier.await();
                CurrentUser u = new CurrentUser();
                u.setUserId(999L);
                u.setRole("SUPER_ADMIN");
                u.setLoginName("race-admin");
                UserHolder.saveUser(u);
                try {
                    TransactionTemplate tx = new TransactionTemplate(txManager);
                    tx.executeWithoutResult(t -> handler.onApproved(1L, null, payload));
                    return null;
                } catch (Throwable e) {
                    return e;
                } finally {
                    UserHolder.removeUser();
                }
            }));
        }
        pool.shutdown();
        return futures;
    }

    private final java.util.concurrent.atomic.AtomicLong rowSeq = new java.util.concurrent.atomic.AtomicLong();

    private void insertUnconfirmed(long team, long memberId, String name) {
        jdbcTemplate.update(
                "INSERT INTO team_member (id, team_id, member_id, member_name, is_leader, status, deleted) "
                        + "VALUES (?, ?, ?, ?, 0, 'UNCONFIRMED', 0)",
                750_000_000_000L + rowSeq.incrementAndGet(), team, memberId, name);
    }
}
