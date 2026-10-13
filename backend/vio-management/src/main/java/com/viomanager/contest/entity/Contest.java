package com.viomanager.contest.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 比赛实例实体，对应表 contest。
 * 状态机：DRAFT → PUBLISHED → REGISTERING → RUNNING → FINISHED → ARCHIVED。
 * override_rules 只存被覆盖的键，生效规则 = 模板默认 ← 比赛覆盖 ← 赛道覆盖。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("contest")
public class Contest extends BaseEntity {

    /** 比赛名称 */
    private String name;

    /** 来源模板 ID */
    private Long templateId;

    /** 所属学期 ID */
    private Long semesterId;

    /** 归属组织（部门管理员数据范围依据） */
    private Long orgId;

    /** 状态：DRAFT / PUBLISHED / REGISTERING / RUNNING / FINISHED / ARCHIVED */
    private String status;

    /** 报名开始时间 */
    private LocalDateTime registerStartAt;

    /** 报名截止时间 */
    private LocalDateTime registerEndAt;

    /** 比赛开始时间 */
    private LocalDateTime contestStartAt;

    /** 比赛结束时间 */
    private LocalDateTime contestEndAt;

    /** 联系人姓名 */
    private String contactName;

    /** 联系人电话 */
    private String contactPhone;

    /** 比赛描述 */
    private String description;

    /** 比赛级规则覆盖 JSON，仅存被覆盖的键 */
    private String overrideRules;

    /** 来源：MANUAL / CRAWLER / RSS / CRAWLER_POOL */
    private String sourceType;

    /** 外部来源标识（URL 或来源 ID），用于去重 */
    private String sourceRef;

    /** 归档时间 */
    private LocalDateTime archivedAt;
}
