package com.viomanager.audit.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AQ03 审计日志详情：列表字段 + 排查失败所需的技术细节列。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuditLogDetailVO extends AuditLogVO {

    /** 请求方法 */
    private String httpMethod;

    /** 请求路径 */
    private String uri;

    /** 浏览器标识 */
    private String userAgent;

    /** 请求参数摘要原文（敏感接口写入侧已置空） */
    private String requestParams;
}
