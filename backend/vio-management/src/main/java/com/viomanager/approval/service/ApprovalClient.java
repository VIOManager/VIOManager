package com.viomanager.approval.service;

import com.viomanager.approval.dto.ApprovalCreateDTO;

/**
 * 审批单创建的进程内契约（非 HTTP，各业务线直接注入调用）。
 * 使用方：B 线 TE03/04/07/09（建队/加人/移除/指导教师）、C 线 A03（成果提交）、A 线 S05（学期切换）。
 */
public interface ApprovalClient {

    /**
     * 创建一张 PENDING 审批单并记 SUBMIT 流水，返回审批单 ID。
     * 有效期按 vio.approval.expire-days（默认 7 天），到期未处理由定时任务转 EXPIRED。
     */
    Long createApproval(ApprovalCreateDTO dto);
}
