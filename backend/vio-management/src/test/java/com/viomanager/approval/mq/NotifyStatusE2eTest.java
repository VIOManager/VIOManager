package com.viomanager.approval.mq;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.viomanager.approval.dto.ApprovalActionDTO;
import com.viomanager.approval.dto.ApprovalCreateDTO;
import com.viomanager.approval.service.ApprovalClient;
import com.viomanager.approval.service.IApprovalActionService;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 插入点③端到端验证（方案 Step 5）：真连本机 RocketMQ + Redis。
 * 链路：管理员批准（同事务写 outbox）→ relay 扫表投递（2 秒节拍）
 *      → 消费者按人重算红点 → Redis Hash 落键 → 重复消费重算幂等。
 * 刻意不 @Transactional：relay 与消费者在别的线程用自己的连接，回滚型测试它们永远看不见。
 * 组织 / 成员 ID 每轮用 nanoTime 派生，跑一百次也不与真实数据或其他轮次撞键。
 * @DirtiesContext：测试框架会缓存不同配置的上下文且不关闭——不关的话，前面 MQ 测试类的
 * 消费者实例还挂在同一个消费组里，CLUSTERING 模式下 broker 把队列分给它，消息被它的
 * onMessage 拿走（它 unread-enabled=false 直接 return），本类永远等不到。类结束即关，独占组。
 */
@SpringBootTest(properties = {
        "vio.mq-enabled=true",
        "rocketmq.name-server=localhost:9876",
        "vio.cache.unread-enabled=true",
        "spring.datasource.hikari.maximum-pool-size=2"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotifyStatusE2eTest {

    /** 9 位整数段内派生，避开演示组织（小 ID）与其他测试的 93xx 段 */
    private static final long BASE = 94_000_000L + System.nanoTime() % 5_000_000L;
    private static final long ORG_ID = BASE;
    private static final long APPLICANT = BASE + 1;
    private static final long TARGET = BASE + 2;

    @Resource
    private ApprovalClient approvalClient;

    @Resource
    private IApprovalActionService approvalActionService;

    @Resource
    private ApprovalStatusListener approvalStatusListener;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JdbcTemplate jdbcTemplate;

    private Long approvalId;

    @AfterEach
    void cleanUp() {
        UserHolder.removeUser();
        if (approvalId != null) {
            jdbcTemplate.update("DELETE FROM notify_outbox WHERE biz_id = ?", approvalId);
            jdbcTemplate.update("DELETE FROM approval_record WHERE approval_id = ?", approvalId);
            jdbcTemplate.update("DELETE FROM approval WHERE id = ?", approvalId);
        }
        jdbcTemplate.update("DELETE FROM sys_org WHERE id = ?", ORG_ID);
        stringRedisTemplate.delete(ApprovalStatusListener.KEY_PREFIX + APPLICANT);
        stringRedisTemplate.delete(ApprovalStatusListener.KEY_PREFIX + TARGET);
    }

    @Test
    void approveFlowsToRedisUnreadHash() throws Exception {
        //1.组织 + 一张有目标人的单（expire-days 默认 7 天，过期消息不掺和本轮）
        jdbcTemplate.update(
                "INSERT INTO sys_org (id, parent_id, name, org_type, path) VALUES (?, 0, 'outboxE2E', 'DEPARTMENT', ?)",
                ORG_ID, "/0/" + ORG_ID + "/");
        approvalId = createApproval();

        //2.部门管理员批准（service 直调 + UserHolder 塞登录态，绕开 HTTP 层）
        CurrentUser admin = new CurrentUser();
        admin.setUserId(1L);
        admin.setRole("DEPT_ADMIN");
        admin.setOrgId(ORG_ID);
        admin.setRealName("outbox端到端");
        UserHolder.saveUser(admin);
        ApprovalActionDTO dto = new ApprovalActionDTO();
        dto.setComment("端到端同意");
        approvalActionService.approve(approvalId, dto);
        UserHolder.removeUser();

        //3.等事件走完 relay → MQ → 消费者（60 秒窗口：2 秒 relay 节拍 + broker 转发
        //  + 消费者重平衡最坏 20 秒——套件里前面上下文的同组消费者刚离开时会更慢，留足余量；
        //  broker 会保留消息，消费者晚订阅也不会丢）
        String applicantKey = ApprovalStatusListener.KEY_PREFIX + APPLICANT;
        String targetKey = ApprovalStatusListener.KEY_PREFIX + TARGET;
        awaitHash(applicantKey);
        awaitHash(targetKey);

        //4.断言红点：发起人 result=1（自己的单批了没看），目标人 todo=0（单已非 PENDING）
        assertEquals("0", hashGet(applicantKey, "todo"));
        assertEquals("1", hashGet(applicantKey, "result"));
        assertEquals("0", hashGet(targetKey, "todo"));
        assertEquals("0", hashGet(targetKey, "result"));

        //5.重复消费幂等：把 outbox 里的原始 payload 再喂一次消费者，重算后数字不变
        String payload = jdbcTemplate.queryForObject(
                "SELECT payload_json FROM notify_outbox WHERE biz_id = ?", String.class, approvalId);
        approvalStatusListener.onMessage(payload);
        assertEquals("1", hashGet(applicantKey, "result"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notify_outbox WHERE biz_id = ? AND send_status = 'SENT'",
                Integer.class, approvalId));
    }

    private Long createApproval() {
        ApprovalCreateDTO dto = new ApprovalCreateDTO();
        dto.setBizType("OUTBOX_E2E_TEST");
        dto.setBizTitle("outbox 端到端测试");
        dto.setApplicantMemberId(APPLICANT);
        dto.setApplicantName("端到端发起人");
        dto.setTargetMemberId(TARGET);
        dto.setTargetMemberName("端到端目标人");
        dto.setApproverOrgId(ORG_ID);
        return approvalClient.createApproval(dto);
    }

    private void awaitHash(String key) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("60 秒内红点键未出现，链路断了：" + key);
    }

    private String hashGet(String key, String field) {
        Object value = stringRedisTemplate.opsForHash().get(key, field);
        return value == null ? null : value.toString();
    }
}
