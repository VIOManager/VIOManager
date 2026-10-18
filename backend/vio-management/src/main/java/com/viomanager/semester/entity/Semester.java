package com.viomanager.semester.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 学期实体，对应表 semester。全平台业务数据按学期归档的维度。
 * status 与 is_current 只能由切换引擎（S08）变更，S03 编辑仅动基础信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("semester")
public class Semester extends BaseEntity {

    /** 学期名称，不可重复（如 2026-2027学年第一学期） */
    private String name;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 状态：PREPARING 未开始 / ACTIVE 进行中 / ARCHIVED 已归档（表结构口径；前端若用 PLANNED 需联调对齐） */
    private String status;

    /** 是否当前学期：1 是 0 否，全平台仅一个为 1，由切换引擎维护 */
    private Integer isCurrent;

    /** 教学周数，排班与绩效周次的取值范围 */
    private Integer totalWeeks;

    /** 归档时间（切换引擎执行时回填） */
    private LocalDateTime archivedAt;

    /** 备注 */
    private String remark;
}
