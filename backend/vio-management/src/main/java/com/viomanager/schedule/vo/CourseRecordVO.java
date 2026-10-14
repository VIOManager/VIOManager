package com.viomanager.schedule.vo;

import lombok.Data;

/**
 * 课表记录出参（TT08 管理端核对，前端 CourseRecord 消费形状）。
 */
@Data
public class CourseRecordVO {

    /** 记录 ID */
    private Long id;

    /** 周次 */
    private Integer weekNo;

    /** 星期：1-7 */
    private Integer weekday;

    /** 节次索引：1-5 */
    private Integer slotIndex;

    /** 归一化班级键 */
    private String classKey;

    /** 课程名称 */
    private String courseName;

    /** 任课教师 */
    private String teacher;

    /** 上课地点 */
    private String location;

    /** 周次类型：ALL / ODD / EVEN */
    private String weekParity;
}
