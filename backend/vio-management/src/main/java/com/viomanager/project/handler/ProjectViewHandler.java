package com.viomanager.project.handler;

import com.viomanager.approval.handler.ApprovalHandler;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.ProjectErrorCode;
import com.viomanager.common.exception.BizException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 项目查看授权审批回调（PROJECT_VIEW）：更新型审批，bizId = project_view_grant.id。
 * 通过 → grant CAS 转 ACTIVE（LIMITED 的 expire_at 建行时已落，至此生效）；
 * 驳回 → 置失效（EXPIRED，清单 §3.7 口径）。
 * ONCE 的消耗不在回调里——发生在 PR03 详情（requireVisibleProjectConsumingOnce）。
 */
@Slf4j
@Component
public class ProjectViewHandler implements ApprovalHandler {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public String bizType() {
        return "PROJECT_VIEW";
    }

    @Override
    public Long onApproved(Long approvalId, Long bizId, String payloadJson) {
        if (bizId == null) {
            throw new BizException(ProjectErrorCode.NOT_FOUND);
        }
        //CAS：PENDING → ACTIVE（0 行=并发/重复处理，抛错连审批一起回滚防状态漂移）
        int rows = jdbcTemplate.update(
                "UPDATE project_view_grant SET status = 'ACTIVE', updated_at = updated_at "
                        + "WHERE id = ? AND status = 'PENDING' AND deleted = 0", bizId);
        if (rows == 0) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED);
        }
        return null; //更新型审批：无新建对象
    }

    @Override
    public void onRejected(Long approvalId, Long bizId, String payloadJson) {
        if (bizId == null) {
            return;
        }
        //驳回置失效（EXPIRED）；已是终态静默跳过（驳回流水仍由审批中心记录）
        jdbcTemplate.update(
                "UPDATE project_view_grant SET status = 'EXPIRED', updated_at = updated_at "
                        + "WHERE id = ? AND status = 'PENDING' AND deleted = 0", bizId);
    }
}
