package com.viomanager.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 绩效周报实体，对应表 performance_weekly（V1__init_52_tables.sql 第 1066–1096 行）。
 * 状态机：SUBMITTED 待审核 → SCORED 已打分 / REJECTED 已驳回；驳回不删记录，
 * PF03 修改内容后原记录回 SUBMITTED。同人同周同子组织仅一份（UNIQUE KEY 兜底）。
 * org_id = sub_org_id 作为数据范围过滤锚点。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("performance_weekly")
public class PerformanceWeekly extends BaseEntity {

    /** 上报人人员 ID */
    private Long memberId;

    /** 姓名冗余 */
    private String memberName;

    /** 学期 ID */
    private Long semesterId;

    /** 周次 */
    private Integer weekNo;

    /** 所属子组织 ID */
    private Long subOrgId;

    /** 归属组织（数据范围过滤，= sub_org_id） */
    private Long orgId;

    /** 本周工作内容 */
    private String workContent;

    /** 附件文件 ID（可选） */
    private Long attachFileId;

    /** 附件名冗余 */
    private String attachFileName;

    /** 状态：SUBMITTED 待审核 / SCORED 已打分 / REJECTED 已驳回 */
    private String status;

    /** 提交时间 */
    private LocalDateTime submittedAt;

    /** 加权总分 */
    private BigDecimal totalScore;

    /** 活跃度/功劳标签 JSON 数组（打分后自动挂） */
    private String activityTags;

    /** 打分时间 */
    private LocalDateTime scoredAt;

    /** 打分管理员 sys_user.id */
    private Long scoredBy;

    /** 打分人姓名冗余 */
    private String scoredByName;

    /** 打分评语 */
    private String scoreComment;

    /** 驳回原因 */
    private String rejectReason;
}
