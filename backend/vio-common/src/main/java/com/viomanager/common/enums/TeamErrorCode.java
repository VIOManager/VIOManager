package com.viomanager.common.enums;

/**
 * 4xxx 比赛、赛队错误码（规范 §4 段位表归 B 线维护）。
 * 与接口清单 docs/api/B线-赛事主线-接口清单.md §1 一致。
 * 4011/4012 属比赛模块（状态流转/模板引用），本期一并占位定义，赛队逻辑不触发。
 */
public enum TeamErrorCode implements ErrorCode {

    /** 比赛当前状态不允许该操作（如组队须 REGISTERING），message 指出缺项 */
    CONTEST_STATE_NOT_ALLOWED(4001, "比赛当前状态不允许该操作"),

    /** 队名在同一比赛下已存在 */
    TEAM_NAME_EXISTS(4002, "队名已存在"),

    /** 队伍人数超过上限 */
    MEMBER_LIMIT_EXCEEDED(4003, "队伍人数超上限"),

    /** 重复参赛限制（allowRepeatJoin=false 时已确认参加同比赛他队） */
    REPEAT_JOIN_LIMITED(4004, "重复参赛限制"),

    /** 目标人已在队或已有待审批申请 */
    TARGET_IN_TEAM_OR_PENDING(4005, "目标人已在队或已有待审批申请"),

    /** 目标人不是教师类型（加指导教师时） */
    TARGET_NOT_TEACHER(4006, "目标人不是教师类型"),

    /** 同队伍同目标已有待审批申请 */
    SAME_TARGET_PENDING(4007, "同队伍同目标已有待审批申请"),

    /** 非本队成员，禁止访问赛队详情（封堵按 ID 枚举越权） */
    NOT_TEAM_MEMBER(4008, "非本队成员，禁止访问赛队详情"),

    /** 队伍存在材料/成果/待审批，禁止解散 */
    DISSOLVE_BLOCKED(4009, "队伍存在材料/成果/待审批，禁止解散"),

    /** 队长不可自我移除或退出 */
    LEADER_NOT_REMOVABLE(4010, "队长不可自我移除或退出"),

    /** 状态流转不可跳转（比赛状态机） */
    STATUS_TRANSITION_INVALID(4011, "状态流转不可跳转"),

    /** 模板已被比赛引用，禁止删除 */
    TEMPLATE_REFERENCED(4012, "模板已被比赛引用，禁止删除");

    private final int code;
    private final String message;

    TeamErrorCode(int code, String message) {
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
