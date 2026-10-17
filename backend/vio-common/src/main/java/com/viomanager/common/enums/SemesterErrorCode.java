package com.viomanager.common.enums;

/**
 * 3xxx 学期管理错误码（规范 §4 段位表归 A 线维护）。
 * 与接口清单 docs/api/A线-基础数据-接口清单.md §1 一致。
 */
public enum SemesterErrorCode implements ErrorCode {

    /** 学期名称已存在（S02 创建 / S03 改名，建表唯一键 uk_semester_name 兜底） */
    SEMESTER_NAME_EXISTS(3008, "学期名称已存在"),

    /** 学期有关联比赛/赛队/成果/切换任务，禁止删除（S04） */
    SEMESTER_HAS_BUSINESS(3009, "学期有关联业务数据，禁止删除");

    private final int code;
    private final String message;

    SemesterErrorCode(int code, String message) {
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
