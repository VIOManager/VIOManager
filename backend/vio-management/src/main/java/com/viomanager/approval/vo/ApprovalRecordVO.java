package com.viomanager.approval.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 审批操作流水出参（详情接口 P09 内嵌）：
 * 动作、操作人、角色、意见、时间（PRD §3.9 要求展示处理意见与时间）。
 */
@Data
public class ApprovalRecordVO {

    /** 动作：SUBMIT / CANCEL / APPROVE / REJECT / AGREE / REFUSE / EXPIRE */
    private String action;

    /** 操作人姓名 */
    private String operatorName;

    /** 操作人角色：APPLICANT / TARGET / APPROVER */
    private String operatorRole;

    /** 意见 */
    private String commentText;

    /** 操作时间 */
    private LocalDateTime createdAt;
}
