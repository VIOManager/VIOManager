package com.viomanager.common.exception;

import com.viomanager.common.enums.ErrorCode;

/**
 * 业务异常：业务代码发现规则不满足时抛出，携带错误码与文案，
 * 由全局异常处理器统一转成 Result 响应体。
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    /** 按错误码枚举抛出，message 用枚举自带文案。 */
    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /** 按错误码枚举抛出，message 覆盖为更具体的说明。 */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    /** 按自定义 code/message 抛出，仅用于错误码枚举之外的临时场景。 */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /**
     * 沿异常因果链找最内层的 BizException，没有返回 null。
     * 用途：MyBatis 会把拦截器（如数据范围拒绝）抛出的业务异常包成 MyBatisSystemException，
     * 接到包装异常后用这个方法把真正的业务码解出来。
     */
    public static BizException unwrap(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof BizException) {
                return (BizException) cause;
            }
            cause = cause.getCause();
        }
        return null;
    }
}
