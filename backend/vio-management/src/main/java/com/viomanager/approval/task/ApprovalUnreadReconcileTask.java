package com.viomanager.approval.task;

import com.viomanager.approval.support.ApprovalUnreadCache;
import com.viomanager.approval.support.UnreadCounter;
import com.viomanager.approval.vo.UnreadCountVO;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 插入点②对账兜底（方案 §4.②）：每 10 分钟把近 7 天有变动的成员红点重算一遍。
 * 它兜的三个窟窿：MQ 全挂（outbox 停投，事件全丢）、消费者写 Redis 失败（事件消费了但键没刷）、
 * 缓存与库的时钟差竞态。只刷**已存在的键**——不存在的键不会脏，下次读自然回填，
 * 也避免给七天里所有被touch过的成员凭空造零值键。
 * 开关 vio.cache.unread-enabled=false 时整个任务空转（没缓存就没"对"可"账"）。
 * 刷新即重算整写：与消费者、读路径 miss 回填是同一段计数逻辑，幂等不漂移。
 */
@Slf4j
@Component
public class ApprovalUnreadReconcileTask {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private UnreadCounter unreadCounter;

    @Resource
    private ApprovalUnreadCache approvalUnreadCache;

    @Scheduled(fixedDelay = 600_000)
    public void scheduledReconcile() {
        int refreshed = reconcile();
        if (refreshed > 0) {
            log.info("红点对账任务：本次刷新 {} 个成员的缓存键", refreshed);
        }
    }

    /** 圈范围重算，返回刷新的键数（测试直调） */
    public int reconcile() {
        if (!approvalUnreadCache.isEnabled()) {
            return 0;
        }
        //1.近 7 天有变动的成员（发起人 ∪ 目标人）——approval.updated_at 建索引前全表扫，
        //  200 人量级毫秒级，不值得为对账任务加索引
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<Long> memberIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT m FROM ("
                        + "SELECT applicant_id AS m FROM approval WHERE updated_at > ? AND applicant_id IS NOT NULL "
                        + "UNION "
                        + "SELECT target_member_id AS m FROM approval WHERE updated_at > ? AND target_member_id IS NOT NULL"
                        + ") t",
                Long.class, since, since);
        //2.只刷已存在的键；单人 Redis 故障记一笔继续（下轮再来）
        int refreshed = 0;
        for (Long memberId : memberIds) {
            if (!approvalUnreadCache.hasKey(memberId)) {
                continue;
            }
            try {
                UnreadCountVO counts = unreadCounter.countFor(memberId);
                approvalUnreadCache.write(memberId, counts);
                refreshed++;
            } catch (DataAccessException e) {
                log.warn("红点对账回写失败（memberId={}），本轮跳过该成员：{}", memberId, e.getMessage());
            }
        }
        return refreshed;
    }
}
