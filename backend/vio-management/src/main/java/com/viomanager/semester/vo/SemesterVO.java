package com.viomanager.semester.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 学期出参（S01 列表与 S03 编辑回显共用一份字段口径）。
 * 字段名对齐表结构（totalWeeks / PREPARING），前端 semester.ts 的 weekCount/PLANNED
 * 是其自创词，联调时前端对齐（表结构是唯一契约）。
 */
@Data
public class SemesterVO {

    private String id;

    private String name;

    private LocalDate startDate;

    private LocalDate endDate;

    /** PREPARING / ACTIVE / ARCHIVED */
    private String status;

    /** 是否当前学期 */
    private Boolean isCurrent;

    /** 教学周数 */
    private Integer totalWeeks;

    /** 归档时间，未归档为 null */
    private LocalDateTime archivedAt;

    private String remark;
}
