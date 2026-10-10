package com.viomanager.common.result;

import com.viomanager.common.enums.ErrorCode;
import java.io.Serializable;

/**
 * 统一响应体：全组所有 HTTP 接口的唯一出参外壳。
 * 三字段 code / message / data；成功 code=0、message="ok"，失败 data=null，
 * 其余细节以 Apifox 与《全局接口与开发规范》§2.2 为准，本类不重复。
 */
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成功码固定为 0（规范 §4 段位表）。 */
    public static final int SUCCESS_CODE = 0;
    private static final String SUCCESS_MESSAGE = "ok";

    private final int code;
    private final String message;
    private final T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 无数据成功响应。 */
    public static <T> Result<T> ok() {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, null);
    }

    /** 携带数据的成功响应。 */
    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    /** 按错误码失败响应，message 取枚举自带文案。 */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** 按错误码失败响应，message 覆盖为更具体的说明（如带上字段名）。 */
    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }

    /** 按自定义 code/message 失败响应，仅用于错误码枚举之外的临时场景。 */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}
