package com.viomanager.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 审批单实体，对应表 approval。
 * ⚠️ ID 口径：applicant_id / target_member_id 是**人员档案 ID**（sys_member），
 * approver_id / BaseEntity.created_by 是**账号 ID**（sys_user）——两种 ID 不通用，
 * 目标人匹配走当前用户的 member_id 字段（进程内契约 ApprovalClient 同此口径）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("approval")
public class Approval extends BaseEntity {

    /** 业务类型：TEAM_CREATE / TEAM_ADD_MEMBER / TEAM_REMOVE_MEMBER / TEAM_ADD_ADVISOR / TEAM_DISSOLVE / ACHIEVEMENT_SUBMIT / PROJECT_VIEW / MILESTONE_APPROVE / SEMESTER_SWITCH */
    private String bizType;

    /** 关联业务 ID：更新型审批（加人/移除）填既有对象 ID；创建型（建队）提交时为空，批准后由回调返回值回填 */
    private Long bizId;

    /** 业务标题，列表展示用（如队名 + 比赛名） */
    private String bizTitle;

    /** 申请内容快照 JSON */
    private String bizPayload;

    /** 状态：PENDING 待处理 / APPROVED 已批准 / REJECTED 已驳回 / CANCELLED 已取消 / EXPIRED 已过期 */
    private String status;

    /** 发起人人员 ID */
    private Long applicantId;

    /** 发起人姓名冗余 */
    private String applicantName;

    /** 目标人人员 ID（如被邀请入队的队员），需其同意/拒绝 */
    private Long targetMemberId;

    /** 目标人姓名冗余 */
    private String targetMemberName;

    /** 审批责任组织 ID，决定哪个管理员收到待办 */
    private Long approverOrgId;

    /** 实际审批人 sys_user.id */
    private Long approverId;

    /** 审批人姓名冗余 */
    private String approverName;

    /** 关联赛队 ID（赛队类审批填） */
    private Long teamId;

    /** 关联比赛 ID（冗余，便于筛选） */
    private Long contestId;

    /** 所属学期 ID */
    private Long semesterId;

    /** 归属组织（数据范围过滤依据），本实现取审批责任组织 */
    private Long orgId;

    /** 申请说明 */
    private String applyReason;

    /** 处理意见 */
    private String handleComment;

    /** 处理时间 */
    private LocalDateTime handledAt;

    /** 过期时间，到期未处理由定时任务转 EXPIRED */
    private LocalDateTime expireAt;

    /** 发起人是否已读结果：1 已读 0 未读 */
    private Integer applicantRead;

    /** 目标人是否已读邀请：1 已读 0 未读 */
    private Integer targetRead;

    /** 审批人是否已读：1 已读 0 未读 */
    private Integer approverRead;
}
