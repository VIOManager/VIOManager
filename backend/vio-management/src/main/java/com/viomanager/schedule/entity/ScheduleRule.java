package com.viomanager.schedule.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 子组织排班规则实体，对应表 schedule_rule（V1__init_52_tables.sql 第 904–922 行）。
 * 规则匹配：优先「子组织+学期」特定规则，无则回退该子组织默认规则（semester_id 为空）；
 * 未配置的时段视为不启用（8007）。UNIQUE KEY 含 semester_id，MySQL 唯一索引对 NULL 不去重，
 * 默认规则（semester_id NULL）的同键重复由 service 层先查后插兜底。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("schedule_rule")
public class ScheduleRule extends BaseEntity {

    /** 子组织 ID */
    private Long subOrgId;

    /** 学期 ID，为空表示对该子组织所有学期生效（默认规则） */
    private Long semesterId;

    /** 可排班星期：1-7 */
    private Integer weekday;

    /** 可排班节次：1-5 */
    private Integer slotIndex;

    /** 该时段最少人数 */
    private Integer minPerson;

    /** 该时段最多人数 */
    private Integer maxPerson;

    /** 是否启用该时段：1 启用 0 停用 */
    private Integer enabled;

    /** 备注 */
    private String remark;
}
