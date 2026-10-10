package com.viomanager.common.audit;

import lombok.Data;

/**
 * 一条审计记录的快照：切面在请求线程里一次性抓全（异步线程拿不到 ThreadLocal 和请求上下文），
 * 抓完交给后台线程写库。字段与 audit_log 表列一一对应。
 */
@Data
public class AuditRecord {

    /** 主键（雪花 ID） */
    private Long id;

    /** 操作人用户 ID，匿名场景为空 */
    private Long userId;

    /** 操作人登录名 */
    private String loginName;

    /** 操作人姓名 */
    private String realName;

    /** 操作人所属组织，部门管理员按此过滤审计 */
    private Long orgId;

    /** 操作人角色 */
    private String role;

    /** 客户端 IP（已解析代理头） */
    private String ip;

    /** 浏览器标识 */
    private String userAgent;

    /** 请求方法 GET/POST/PUT/DELETE */
    private String httpMethod;

    /** 请求路径 */
    private String uri;

    /** 业务类型代码 */
    private String bizType;

    /** 业务类型中文名 */
    private String bizTypeLabel;

    /** 操作摘要（SpEL 渲染结果） */
    private String summary;

    /** 请求参数摘要，敏感接口为空 */
    private String requestParams;

    /** 是否成功 */
    private Boolean success;

    /** 失败原因 */
    private String errorMessage;

    /** 耗时毫秒 */
    private Long costMillis;
}
