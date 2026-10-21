package com.viomanager.project.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 比赛时间节点实体，对应表 milestone_node。
 * 部门管理员维护，提交走 MILESTONE_APPROVE 审批；审核通过后同步给挂载该比赛的项目生成任务。
 *
 * ⚠️ 状态机 DRAFT→PENDING→APPROVED/REJECTED 只允许条件更新（防并发跳档）；
 *    仅 DRAFT/REJECTED 可编辑（7009）、仅 DRAFT 可删（7009）。
 * ⚠️ synced 与审核解耦：挂载回溯（PR13）靠它识别"已审核未同步"的节点。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("milestone_node")
public class MilestoneNode extends BaseEntity {

    /** 所属比赛 ID */
    private Long contestId;

    /** 所属学期 ID */
    private Long semesterId;

    /** 归属组织 */
    private Long orgId;

    /** 节点类型：REGISTER_START 报名开始 / REGISTER_END 报名截止 / WORK_SUBMIT 作品提交 / OTHER 其他 */
    private String nodeType;

    /** 节点名称 */
    private String name;

    /** 节点时间 */
    private LocalDateTime nodeTime;

    /** 说明 */
    private String description;

    /** 状态：DRAFT 草稿 / PENDING 待审核 / APPROVED 已通过 / REJECTED 已驳回 */
    private String status;

    /** 审核通过时间 */
    private LocalDateTime approvedAt;

    /** 审核人 */
    private Long approvedBy;

    /** 驳回原因 */
    private String rejectReason;

    /** 是否已同步给挂载项目：1 已同步 0 未同步 */
    private Integer synced;

    /** 同步时间 */
    private LocalDateTime syncedAt;
}
