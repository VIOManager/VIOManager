package com.viomanager.common.enums;

/**
 * 2xxx 认证与账号错误码（规范 §4 段位表归组长维护）。
 * Step 3 认证接口开始使用，本步先占位定义，与骨架接口清单 §1 码表一致。
 */
public enum AuthErrorCode implements ErrorCode {

    /** 登录名或密码错误（不区分提示，避免撞库探测）。 */
    LOGIN_FAILED(2001, "登录名或密码错误"),

    /** 账号已停用。 */
    ACCOUNT_DISABLED(2002, "账号已停用"),

    /** 登录名已存在（绑定/改登录名时）。 */
    LOGIN_NAME_EXISTS(2003, "登录名已存在"),

    /** 修改密码时原密码不正确。 */
    OLD_PASSWORD_WRONG(2004, "原密码不正确");

    private final int code;
    private final String message;

    AuthErrorCode(int code, String message) {
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
