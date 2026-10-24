package com.viomanager.audit.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * AQ01 审计日志列表项。
 * 刻意不带 requestParams / userAgent 等宽列（清单：省流量，进详情看）；
 * success 用 Integer 直出 1/0（前端按数字判断）；id/costMillis 为 Long，
 * 出参按字符串序列化（JacksonConfig 雪花精度保护）。
 */
@Data
public class AuditLogVO {

    private Long id;

    /** 操作人姓名 */
    private String realName;

    /** 操作人登录名 */
    private String loginName;

    /** 操作人角色 */
    private String role;

    /** 业务类型代码 */
    private String bizType;

    /** 业务类型中文名（写入侧随 @AuditLog label 落库） */
    private String bizTypeLabel;

    /** 操作摘要 */
    private String summary;

    /** 客户端 IP */
    private String ip;

    /** 是否成功：1 成功 0 失败 */
    private Integer success;

    /** 失败原因 */
    private String errorMessage;

    /** 耗时毫秒 */
    private Long costMillis;

    /** 发生时间 */
    private LocalDateTime createdAt;
}
