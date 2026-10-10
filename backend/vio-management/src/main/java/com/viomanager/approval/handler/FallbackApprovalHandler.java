package com.viomanager.approval.handler;

import lombok.extern.slf4j.Slf4j;

/**
 * 兜底回调：业务类型没注册 Handler 时用它顶上——记日志不动作。
 * 作用：某条线还没实现回调时，审批照常能批，事后翻日志能发现"这单没人接"，
 * 不会静默丢失（方案 Step 9 机制要求）。
 */
@Slf4j
public class FallbackApprovalHandler implements ApprovalHandler {

    @Override
    public String bizType() {
        // 兜底不占业务类型，注册表按缺省逻辑取它
        return "";
    }

    @Override
    public Long onApproved(Long approvalId, Long bizId, String payloadJson) {
        log.warn("审批回调缺实现：approvalId={} bizId={} 已批准但无 Handler 接单，快照：{}",
                approvalId, bizId, payloadJson);
        return null;
    }

    @Override
    public void onRejected(Long approvalId, Long bizId, String payloadJson) {
        log.warn("审批回调缺实现：approvalId={} bizId={} 被驳回，无 Handler 接单", approvalId, bizId);
    }
}
