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
    CONCURRENT_MODIFIED(1007, "数据已被他人修改，请刷新后重试"),

    /**
     * 文件格式不在白名单（骨架接口清单 F01 定义的 1008–1011 段，
     * 与 1000 同属骨架补充码，待下次规范修订登记进 §4 表）。
     */
    FILE_TYPE_NOT_ALLOWED(1008, "不支持的文件格式"),

    /** 文件超过大小上限（业务校验与 Spring multipart 硬限制统一收敛到此码）。 */
    FILE_TOO_LARGE(1009, "文件大小超出限制"),

    /** 病毒检测命中：文件已落库标记 INFECTED 并审计，拒绝返回给前端。 */
    FILE_INFECTED(1010, "文件未通过病毒检测"),

    /**
     * 病毒扫描服务不可用。组长 2026-10-10 拍板：不可用时**不拦用户**，文件标记 UNAVAILABLE 放行入库，
     * 本码保留给后续管理端"重扫 / 巡检告警"功能使用，上传链路不再抛出。
     */
    SCAN_UNAVAILABLE(1011, "病毒扫描服务不可用"),

    /**
     * 学期切换维护锁定（S08 执行引擎窗口期）：JwtAuthFilter 对非超管返回 HTTP 503 + 此码。
     * 刻意与 401/1002 区分——不触发前端"跳登录"，前端可按 503 展示维护提示页。
     */
    SYSTEM_MAINTENANCE(1012, "学期切换维护锁定中，请稍后再试");

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
