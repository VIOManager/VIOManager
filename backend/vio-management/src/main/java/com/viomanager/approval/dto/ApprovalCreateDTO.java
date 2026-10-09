package com.viomanager.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建审批单参数（进程内契约 ApprovalClient 用，非 HTTP 接口）。
 * 各业务线填齐业务类型与双方信息后调 ApprovalClient.createApproval。
 * ⚠️ ID 口径：applicantMemberId / targetMemberId 是人员档案 ID，approverOrgId 是组织 ID。
 */
@Data
public class ApprovalCreateDTO {

    /** 业务类型（九种已登记词表取值，见 Approval 实体注释） */
    @NotBlank(message = "bizType 不能为空")
    private String bizType;

    /** 关联业务 ID（申请时尚未建正式数据可空） */
    private Long bizId;

    /** 业务标题（列表展示，如"xxx 队参赛报名"） */
    @NotBlank(message = "bizTitle 不能为空")
    private String bizTitle;

    /** 申请内容快照 JSON（回调时原样传回业务线） */
    private String payloadJson;

    /** 发起人人员 ID */
    @NotNull(message = "applicantMemberId 不能为空")
    private Long applicantMemberId;

    /** 发起人姓名冗余 */
    @NotBlank(message = "applicantName 不能为空")
    private String applicantName;

    /** 目标人人员 ID（邀请类审批必填，其余可空） */
    private Long targetMemberId;

    /** 目标人姓名冗余 */
    private String targetMemberName;

    /** 审批责任组织 ID（决定哪个管理员收到待办） */
    @NotNull(message = "approverOrgId 不能为空")
    private Long approverOrgId;

    /** 申请说明 */
    private String applyReason;

    /** 关联赛队 ID（赛队类审批填） */
    private Long teamId;

    /** 关联比赛 ID */
    private Long contestId;

    /** 所属学期 ID */
    private Long semesterId;
}
