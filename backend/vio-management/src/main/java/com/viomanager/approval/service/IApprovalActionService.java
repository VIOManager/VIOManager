package com.viomanager.approval.service;

import com.viomanager.approval.dto.ApprovalActionDTO;

/**
 * 审批处理服务：批准 / 驳回 / 取消 / 目标人同意 / 目标人拒绝（P02–P06）。
 */
public interface IApprovalActionService {

    /** 管理员批准（P02）：PENDING → APPROVED，事务内路由 onApproved 回调 */
    void approve(Long id, ApprovalActionDTO dto);

    /** 管理员驳回（P03）：PENDING → REJECTED，路由 onRejected 回调（各线通常无动作） */
    void reject(Long id, ApprovalActionDTO dto);

    /** 发起人取消（P04）：仅 PENDING 可取消，不触发回调 */
    void cancel(Long id, ApprovalActionDTO dto);

    /** 目标人同意（P05，如被邀请队员）：PENDING → APPROVED，路由 onApproved 回调 */
    void agree(Long id, ApprovalActionDTO dto);

    /** 目标人拒绝（P06）：PENDING → REJECTED，路由 onRejected 回调 */
    void refuse(Long id, ApprovalActionDTO dto);
}
