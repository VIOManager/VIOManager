package com.viomanager.common.enums;

/**
 * 7xxx 项目管理错误码（规范 §4 段位表归项目管理线维护）。
 * 码值与含义严格照《docs/api/项目管理-接口清单.md》第 1 章错误码表。
 *
 * ⚠️ 本文件位于 common.enums 属**公共组件**：按规范 §7.6，开 PR 前须在群里报备。
 */
public enum ProjectErrorCode implements ErrorCode {

    /** 项目不存在（含已逻辑删除，不给探测空间）。 */
    NOT_FOUND(7001, "项目不存在"),

    /** 写接口门槛：项目所有人或管理员（2026-10-16 组长拍板：管理员权限最高可执行所有操作；原"仅所有人"口径作废）。 */
    NOT_OWNER(7002, "仅项目所有人或管理员可操作"),

    /** 可见性八条判定全部未命中（前端引导提交查看申请 PR29）。 */
    VIEW_DENIED(7003, "无权查看该项目"),

    /** 同学期同组织内项目名重复（uk_project_name 拦截）。 */
    NAME_EXISTS(7004, "同学期同组织内项目名已存在"),

    /** 添加成员时该人员已在项目内（uk_project_member 拦截）。 */
    ALREADY_MEMBER(7005, "该人员已是项目成员"),

    /** 指导教师必须是 member_type=TEACHER 的人员。 */
    ADVISOR_NOT_TEACHER(7006, "指导教师必须是教师类型人员"),

    /** 同队同项目同赛道不可重复挂载（uk_team_project 拦截）。 */
    ALREADY_MOUNTED(7007, "该赛队已挂载此项目"),

    /** 挂载校验：项目与赛队不在同一学期。 */
    SEMESTER_MISMATCH(7008, "挂载的项目与赛队不在同一学期"),

    /** 时间节点状态机外的操作（仅 DRAFT/REJECTED 可编辑、仅 DRAFT 可删等）。 */
    MILESTONE_STATE_NOT_ALLOWED(7009, "时间节点当前状态不允许该操作"),

    /** 删除项目前须先解绑所有 ACTIVE 挂载。 */
    HAS_ACTIVE_MOUNT(7010, "项目有挂载中的赛队，请先解绑"),

    /** 同一申请人对同一项目已有 PENDING 查看申请。 */
    PENDING_REQUEST_EXISTS(7011, "已存在待审批的查看申请，请勿重复提交"),

    /** 任务不存在或不属于该项目的任务位（路径 taskId 与项目不匹配）。 */
    TASK_NOT_FOUND(7012, "任务不存在或不属于该项目"),

    /** 创建项目的人未绑定人员档案（超管 admin 账号即此态）；此前误报 1001 参数校验失败，2026-10-16 联调拆出。 */
    NO_MEMBER_PROFILE(7013, "当前账号未绑定人员档案，无法创建项目（项目是个人资产，所有人须有档案）");

    private final int code;
    private final String message;

    ProjectErrorCode(int code, String message) {
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
