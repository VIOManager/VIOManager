package com.viomanager.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.viomanager.approval.dto.ApprovalCreateDTO;
import com.viomanager.approval.service.ApprovalClient;
import com.viomanager.approval.support.ApprovalUnreadCache;
import com.viomanager.approval.task.ApprovalUnreadReconcileTask;
import com.viomanager.approval.vo.UnreadCountVO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 插入点②对账兜底（方案 Step 6）：近 7 天有变动成员的**已存在键**被重算刷新；
 * 没有键的成员不凭空造键（不存在的键不会脏，下次读自然回填）。
 * 非事务型（对账直调无所谓事务，数据自清）；红点开、MQ 关——顺带证明
 * relay 不在时建单照常（SUBMIT 事件只是安静地躺在 outbox 里等）。
 */
@SpringBootTest(properties = {
        "vio.cache.unread-enabled=true",
        "spring.datasource.hikari.maximum-pool-size=2"})
class ApprovalUnreadReconcileTest {

    private static final long APPLICANT = 97_000_000L + System.nanoTime() % 2_000_000L;
    private static final long TARGET = APPLICANT + 1;

    @Resource
    private ApprovalClient approvalClient;

    @Resource
    private ApprovalUnreadReconcileTask reconcileTask;

    @Resource
    private ApprovalUnreadCache approvalUnreadCache;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JdbcTemplate jdbcTemplate;

    private Long approvalId;

    @AfterEach
    void cleanUp() {
        if (approvalId != null) {
            jdbcTemplate.update("DELETE FROM notify_outbox WHERE biz_id = ?", approvalId);
            jdbcTemplate.update("DELETE FROM approval_record WHERE approval_id = ?", approvalId);
            jdbcTemplate.update("DELETE FROM approval WHERE id = ?", approvalId);
        }
        stringRedisTemplate.delete(ApprovalUnreadCache.KEY_PREFIX + APPLICANT);
        stringRedisTemplate.delete(ApprovalUnreadCache.KEY_PREFIX + TARGET);
    }

    @Test
    void reconcileRefreshesExistingKeysOnly() {
        //1.建一张 PENDING 单：申请人 / 目标人都进了"近 7 天有变动"圈子
        approvalId = createApproval();
        //2.给申请人预埋一个脏值键（模拟事件丢失后的过期缓存）；目标人不给键
        approvalUnreadCache.write(APPLICANT, vo(5, 5));

        //3.对账：申请人的键被重算成真值（PENDING 单：发起人 result=0，目标人 todo=1），
        //  目标人无键不造键
        int refreshed = reconcileTask.reconcile();
        assertEquals(1, refreshed);
        String applicantKey = ApprovalUnreadCache.KEY_PREFIX + APPLICANT;
        String targetKey = ApprovalUnreadCache.KEY_PREFIX + TARGET;
        assertEquals("0", hashGet(applicantKey, "todo"));
        assertEquals("0", hashGet(applicantKey, "result"));
        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey(targetKey)));
        //4.开关关的口径顺带验证不了（本上下文开着），但 isEnabled 契约在对账第一行就返回
        assertTrue(approvalUnreadCache.isEnabled());
    }

    private Long createApproval() {
        ApprovalCreateDTO dto = new ApprovalCreateDTO();
        dto.setBizType("RECONCILE_TEST");
        dto.setBizTitle("对账任务测试");
        dto.setApplicantMemberId(APPLICANT);
        dto.setApplicantName("对账发起人");
        dto.setTargetMemberId(TARGET);
        dto.setTargetMemberName("对账目标人");
        dto.setApproverOrgId(9701L);
        return approvalClient.createApproval(dto);
    }

    private UnreadCountVO vo(long todo, long result) {
        UnreadCountVO vo = new UnreadCountVO();
        vo.setTodoCount(todo);
        vo.setResultCount(result);
        return vo;
    }

    private String hashGet(String key, String field) {
        Object value = stringRedisTemplate.opsForHash().get(key, field);
        return value == null ? null : value.toString();
    }
}
