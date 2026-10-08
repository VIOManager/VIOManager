package com.viomanager.common.enums;

/**
 * 1xxx 通用错误码：全组公共，任何人不得占用与改号（规范 §4 通用码表）。
 * 1000 为骨架补充的兜底码（未捕获异常统一返回），待下次规范修订登记进 §4 表。
 */
public enum CommonErrorCode implements ErrorCode {

    /** 未捕获异常兜底，完整堆栈只进服务端日志，不外泄给前端。 */
    INTERNAL_ERROR(1000, "系统繁忙，请稍后重试"),

    /** 参数校验失败，message 需说明哪个字段。 */
    PARAM_INVALID(1001, "参数校验失败"),

    /** 未登录或令牌过期，必须同时返回 HTTP 401（前端据此跳登录页）。 */
    UNAUTHORIZED(1002, "未登录或令牌已过期"),

    /** 无权限，必须同时返回 HTTP 403。 */
    FORBIDDEN(1003, "无权限"),

    /** 资源不存在。 */
    NOT_FOUND(1004, "资源不存在"),

    /** 重复提交（幂等拦截命中）。 */
    DUPLICATE_SUBMIT(1005, "重复提交，请勿重复操作"),

    /** 当前状态不允许该操作。 */
    STATE_NOT_ALLOWED(1006, "当前状态不允许该操作"),

    /** 数据已被他人修改（乐观锁冲突）。 */
    CONCURRENT_MODIFIED(1007, "数据已被他人修改，请刷新后重试");

    private final int code;
    private final String message;

    CommonErrorCode(int code, String message) {
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
