package com.viomanager.schedule.vo;

import lombok.Data;

/**
 * SC01 周排班表单格内的人员条目（前端 ScheduleEntry 消费形状）。
 * courseConflict 用 Boolean（库内 tinyint）。
 */
@Data
public class ScheduleEntryVO {

    /** 排班 ID */
    private Long id;

    /** 人员 ID */
    private Long memberId;

    /** 姓名 */
    private String memberName;

    /** 班级 */
    private String className;

    /** 值班内容 */
    private String dutyName;

    /** 是否与课表冲突（管理员强制保存） */
    private Boolean courseConflict;

    /** 冲突提示 */
    private String conflictNote;

    /** 备注 */
    private String remark;
}
