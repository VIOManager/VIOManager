package com.viomanager.common.enums;

/**
 * 6xxx 审批中心错误码（规范 §4 段位表归组长维护）。
 * Step 9 审批接口开始使用，本步先占位定义，与骨架接口清单 §1 码表一致。
 */
public enum ApprovalErrorCode implements ErrorCode {

    /** 审批单不存在，或已被处理过（条件更新返回 0 行时也归此类）。 */
    NOT_FOUND_OR_HANDLED(6001, "审批单不存在或已处理"),

    /** 当前用户无权处理该审批。 */
    NO_PERMISSION(6002, "无权处理该审批"),

    /** 审批单当前状态不允许该操作。 */
    STATE_NOT_ALLOWED(6003, "当前审批状态不允许该操作");

    private final int code;
    private final String message;

    ApprovalErrorCode(int code, String message) {
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
