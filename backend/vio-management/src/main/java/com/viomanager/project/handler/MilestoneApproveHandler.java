package com.viomanager.project.handler;

import com.viomanager.project.support.MilestoneSyncSupport;
import com.viomanager.project.support.PmQueryCache;
import com.viomanager.common.enums.ProjectErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.approval.handler.ApprovalHandler;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 时间节点审批回调（MILESTONE_APPROVE）：更新型审批，bizId = milestone_node.id。
 * 通过 → 节点 CAS 转 APPROVED、回填 approved_at / approved_by（审批人），
 *        并触发 MilestoneSyncSupport 给挂载该比赛的 ACTIVE 项目批量建任务；
 * 驳回 → 转 REJECTED，驳回意见从审批流水（approval_record 最新 REJECT/REFUSE 的
 *        comment_text）带入 reject_reason——SPI 回调参数不含意见，只能查流水。
 * 回调在审批处理的同一事务内：CAS 失败（节点已被并发处理）抛错连审批一起回滚，
 * 不会出现"单据已批但节点没动"的中间态。
 */
@Slf4j
@Component
public class MilestoneApproveHandler implements ApprovalHandler {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private MilestoneSyncSupport syncSupport;

    @Resource
    private PmQueryCache queryCache;

    @Override
    public String bizType() {
        return "MILESTONE_APPROVE";
    }

    @Override
    public Long onApproved(Long approvalId, Long bizId, String payloadJson) {
        if (bizId == null) {
            throw new BizException(ProjectErrorCode.NOT_FOUND);
        }
        //1.CAS：PENDING → APPROVED（审批人 sys_user.id 记入 approved_by）
        int rows = jdbcTemplate.update(
                "UPDATE milestone_node SET status = 'APPROVED', approved_at = NOW(), "
                        + "approved_by = (SELECT operator_id FROM approval_record "
                        + "WHERE approval_id = ? AND action = 'APPROVE' "
                        + "ORDER BY id DESC LIMIT 1), updated_at = updated_at "
                        + "WHERE id = ? AND status = 'PENDING' AND deleted = 0",
                approvalId, bizId);
        if (rows == 0) {
            //节点已不是 PENDING（并发重放/被删）——抛错让整单回滚，杜绝状态漂移
            throw new BizException(ProjectErrorCode.MILESTONE_STATE_NOT_ALLOWED);
        }
        //2.同步：给挂载该比赛的 ACTIVE 项目批量建任务（行锁内 synced 防重）
        syncSupport.syncOnApproved(bizId);
        //3.缓存失效：该比赛的 APPROVED 列表多了一条（TTL 60s 兜底，主动 evict 即时生效）
        String contestId = jdbcTemplate.queryForObject(
                "SELECT contest_id FROM milestone_node WHERE id = ?", String.class, bizId);
        if (contestId != null) {
            queryCache.evict("milestone:approved:" + contestId);
        }
        return null; //更新型审批：无新建对象，bizId 不变
    }

    @Override
    public void onRejected(Long approvalId, Long bizId, String payloadJson) {
        if (bizId == null) {
            return;
        }
        //驳回意见：流水里最新一条 REJECT/REFUSE 的 comment_text（SPI 参数不含意见）
        List<String> reasons = jdbcTemplate.queryForList(
                "SELECT comment_text FROM approval_record WHERE approval_id = ? "
                        + "AND action IN ('REJECT', 'REFUSE') ORDER BY id DESC LIMIT 1",
                String.class, approvalId);
        String reason = reasons.isEmpty() ? null : reasons.get(0);
        jdbcTemplate.update(
                "UPDATE milestone_node SET status = 'REJECTED', reject_reason = ?, "
                        + "updated_at = updated_at WHERE id = ? AND status = 'PENDING' AND deleted = 0",
                reason, bizId);
    }
}
