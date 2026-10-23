package com.viomanager.schedule.vo;

import lombok.Data;

/**
 * 班级映射出参（TT06 列表，前端 ClassMapping 消费形状）。
 * confirmed 用 Boolean（库内 tinyint，出参转语义布尔）。
 */
@Data
public class ClassMappingVO {

    /** 映射 ID */
    private Long id;

    /** 学期 ID */
    private Long semesterId;

    /** 课表班级原始名（归一化键见库内 class_key） */
    private String classKey;

    /** 档案侧班级名 */
    private String className;

    /** 年级，如 2022 */
    private String grade;

    /** 年级来源：STUDENT_NO / CLASS_NAME */
    private String gradeSource;

    /** 是否已确认（确认后才参与空课统计） */
    private Boolean confirmed;
}
