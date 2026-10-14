package com.viomanager.common.enums;

/**
 * 8xxx 绩效段错误码（8040 起，规范 §4 段位表；8001–8039 归排班见 ScheduleErrorCode）。
 * 码值与含义严格照《绩效统计-接口清单.md》§1 错误码表。
 *
 * ⚠️ 本文件位于 common.enums 属**公共组件**：按规范 §7.6，开 PR 前须在群里报备。
 */
public enum PerformanceErrorCode implements ErrorCode {

    /** 周报不存在。 */
    WEEKLY_NOT_FOUND(8040, "周报不存在"),

    /** 同人同周同子组织仅一份周报（表唯一键兜底，service 先查后插）。 */
    WEEKLY_DUPLICATE(8041, "该周该子组织已提交过周报"),

    /** 周报当前状态不允许该操作（如非 SUBMITTED 打分、非 REJECTED 重提）。 */
    WEEKLY_STATUS_NOT_ALLOWED(8042, "周报当前状态不允许该操作"),

    /** 打分维度集合须与该子组织 enabled 规则完全一致。 */
    DIMENSION_MISMATCH(8043, "打分维度与该子组织规则不符"),

    /** 单维度得分超过该维度满分。 */
    SCORE_EXCEED_MAX(8044, "维度得分超过满分"),

    /** 仅本人可操作自己的周报。 */
    NOT_WEEKLY_OWNER(8045, "仅本人可操作自己的周报"),

    /** 该子组织未配置绩效规则（规则先行，未配置无法打分）。 */
    RULE_NOT_CONFIGURED(8046, "该子组织未配置绩效规则，请先配置"),

    /** 周次超出学期教学周数（semester.total_weeks）。 */
    PERF_WEEK_OUT_OF_RANGE(8047, "周次超出学期教学周数");

    private final int code;
    private final String message;

    PerformanceErrorCode(int code, String message) {
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
