package com.viomanager.schedule.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 课表班级与档案班级映射实体，对应表 class_mapping（V1__init_52_tables.sql 第 851–872 行）。
 * confirmed=1 的班级才参与空课统计（PRD §3.11 铁规则：未确认映射的班级不参与空课统计）。
 * UNIQUE KEY (semester_id, class_key, deleted) 数据库兜底重复导入。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("class_mapping")
public class ClassMapping extends BaseEntity {

    /** 学期 ID */
    private Long semesterId;

    /** 来源导入批次 ID */
    private Long importId;

    /** 课表班级归一化键 */
    private String classKey;

    /** 课表班级原始名 */
    private String classNameRaw;

    /** 档案侧班级名（member.class_name） */
    private String memberClassName;

    /** 年级，如 2022 */
    private String grade;

    /** 年级来源：STUDENT_NO 学号推导 / CLASS_NAME 班级名兜底 */
    private String gradeSource;

    /** 是否已确认映射：1 已确认（参与空课统计）0 未确认 */
    private Integer confirmed;

    /** 确认人 */
    private Long confirmedBy;

    /** 确认时间 */
    private LocalDateTime confirmedAt;

    /** 所属组织 */
    private Long orgId;
}
