package com.viomanager.schedule.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 课表记录实体，对应表 course_timetable（V1__init_52_tables.sql 第 825–849 行，表结构即契约）。
 * 空课判定：某班级在 (week_no, weekday, slot_index) 无记录即空课；
 * week_type=ODD 仅奇数周占用、EVEN 仅偶数周、ALL 每周；同格多门课取并集。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("course_timetable")
public class CourseTimetable extends BaseEntity {

    /** 来源导入批次 ID */
    private Long importId;

    /** 学期 ID */
    private Long semesterId;

    /** 课表班级名称（原始，未归一化） */
    private String className;

    /** 归一化后的班级键，用于与人员档案班级匹配 */
    private String classKey;

    /** 周次，1 起 */
    private Integer weekNo;

    /** 星期：1 周一 ... 7 周日 */
    private Integer weekday;

    /** 节次索引：1-5 对应五大节 */
    private Integer slotIndex;

    /** 节次名称，如「第1-2节」 */
    private String slotName;

    /** 课程名称 */
    private String courseName;

    /** 任课教师 */
    private String teacherName;

    /** 上课地点 */
    private String classroom;

    /** 周次类型：ALL 每周 / ODD 单周 / EVEN 双周 */
    private String weekType;

    /** 所属组织 */
    private Long orgId;
}
