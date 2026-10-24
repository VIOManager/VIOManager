package com.viomanager.common.enums;

/**
 * 3xxx 人员档案错误码（规范 §4 段位表归 A 线维护）。
 * 与接口清单 docs/api/A线-基础数据-接口清单.md §1 一致。
 * 3002 场景说明：M04 编辑入参刻意不收学号字段，该码按契约占位定义，当前无触发路径。
 */
public enum MemberErrorCode implements ErrorCode {

    /** 学号已存在（M03 新建 / M07 导入外的手工场景；导入里已存在按契约跳过不报错） */
    STUDENT_NO_EXISTS(3001, "学号已存在"),

    /** 学号创建后不可修改（M04；入参不收学号，占位定义） */
    STUDENT_NO_IMMUTABLE(3002, "学号不可修改"),

    /** 该人员是活跃队伍的队长，不能删除（M05） */
    MEMBER_IS_LEADER(3003, "队长身份的成员不能删除，请先转移队长或解散队伍"),

    /** 不能删除当前登录账号绑定的人员档案（M05） */
    MEMBER_IS_SELF(3004, "不能删除当前登录账号绑定的人员"),

    /** Excel 导入存在错误行（M07，message 配合逐行 errors 明细） */
    IMPORT_ROW_ERROR(3005, "Excel 导入存在错误行，请查看逐行错误明细"),

    /** 批量删除单次超上限（M05B；3011 起——3006-3009 已被组织/学期模块占用） */
    MEMBER_BATCH_LIMIT(3011, "批量删除人数超过单次上限"),

    /** 无权查看该人员的敏感信息（M02 深档 / M10 身份证 / M11 证件照签名） */
    MEMBER_SENSITIVE_DENIED(3010, "无权查看该人员的敏感信息");

    private final int code;
    private final String message;

    MemberErrorCode(int code, String message) {
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
