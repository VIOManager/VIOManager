package com.viomanager.approval.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 审批列表项出参，字段口径 = 骨架接口清单 P01：
 * id bizType bizTitle status applicantName targetMemberName applyReason handleComment handledAt createdAt。
 */
@Data
public class ApprovalVO {

    /** 审批单 ID（JSON 序列化为字符串） */
    private Long id;

    /** 业务类型 */
    private String bizType;

    /** 业务标题 */
    private String bizTitle;

    /** 状态：PENDING / APPROVED / REJECTED / CANCELLED / EXPIRED */
    private String status;

    /** 发起人姓名 */
    private String applicantName;

    /** 目标人姓名 */
    private String targetMemberName;

    /** 申请说明 */
    private String applyReason;

    /** 处理意见 */
    private String handleComment;

    /** 处理时间 */
    private LocalDateTime handledAt;

    /** 发起时间 */
    private LocalDateTime createdAt;
}
