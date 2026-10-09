package com.viomanager.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 审批操作流水实体，对应表 approval_record。
 * 表只有 created_at 没有其余基座尾列（流水只增不改），不继承 BaseEntity，独立定义字段。
 */
@Data
@Accessors(chain = true)
@TableName("approval_record")
public class ApprovalRecord {

    /** 主键（雪花 ID，插入前由代码赋值） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 审批单 ID */
    private Long approvalId;

    /** 动作：SUBMIT 发起 / CANCEL 取消 / APPROVE 批准 / REJECT 驳回 / AGREE 同意 / REFUSE 拒绝 / EXPIRE 过期 */
    private String action;

    /** 操作人 sys_user.id（系统动作如 EXPIRE 为空） */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 操作人角色：APPLICANT 发起人 / TARGET 目标人 / APPROVER 审批人 */
    private String operatorRole;

    /** 意见 / 备注 */
    private String commentText;

    /** 变更前状态 */
    private String fromStatus;

    /** 变更后状态 */
    private String toStatus;

    /** 操作 IP */
    private String ip;

    /** 操作时间 */
    private LocalDateTime createdAt;
}
