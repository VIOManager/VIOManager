package com.viomanager.common.enums;

/**
 * 8xxx 排班段错误码（8001–8039，规范 §4 段位表；8040 起归绩效见 PerformanceErrorCode）。
 * 码值与含义严格照《排班管理-接口清单.md》§1 错误码表。
 *
 * ⚠️ 本文件位于 common.enums 属**公共组件**：按规范 §7.6，开 PR 前须在群里报备。
 */
public enum ScheduleErrorCode implements ErrorCode {

    /** 课表文件解析失败（message 带行号与原因）。 */
    PARSE_FAILED(8001, "课表文件解析失败"),

    /** 学期名称归一化失败，批次留 PENDING 等人工指定（TT05）。 */
    SEMESTER_NORMALIZE_FAILED(8002, "学期名称归一化失败，请人工指定学期"),

    /** 导入批次当前状态不允许该操作（如非 PENDING 时 TT05 指定学期）。 */
    IMPORT_STATUS_NOT_ALLOWED(8003, "导入批次当前状态不允许该操作"),

    /** 班级映射不存在（TT07 确认的 id 无效）。 */
    CLASS_MAPPING_NOT_FOUND(8004, "班级映射不存在"),

    /** 同周次同人同子组织仅一条有效排班（拍板记录①，服务层兜底唯一键缺口）。 */
    DUPLICATE_SCHEDULE_IN_WEEK(8005, "该周次该人在该子组织已有排班"),

    /** 该时段排班人数已达上限（schedule_rule.max_person）。 */
    SLOT_FULL(8006, "该时段排班人数已达上限"),

    /** 该时段未启用排班（无规则或 rule.enabled=0）。 */
    SLOT_NOT_ENABLED(8007, "该时段未启用排班"),

    /** 排班与课表冲突，确认强制保存须带 force（保存时打冲突标记）。 */
    COURSE_CONFLICT(8008, "排班与课表冲突"),

    /** 排班人员不属于该子组织（member.org_id ≠ subOrgId）。 */
    MEMBER_NOT_IN_SUB_ORG(8009, "排班人员不属于该子组织"),

    /** 周次超出学期教学周数（semester.total_weeks）。 */
    WEEK_OUT_OF_RANGE(8010, "周次超出学期教学周数");

    private final int code;
    private final String message;

    ScheduleErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
