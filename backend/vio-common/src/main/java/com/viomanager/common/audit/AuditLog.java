package com.viomanager.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计声明注解：标在需要留痕的接口上，调用自动异步写 audit_log（谁、何时、哪个 IP、干了什么、成没成）。
 * 用法：
 *   @AuditLog(bizType = "TEAM_CREATE", label = "创建赛队")                       纯文本摘要
 *   @AuditLog(bizType = "TEAM_CREATE", label = "创建赛队",
 *             summary = "'创建赛队：' + #body.teamName")                          SpEL 拼接入参
 *   @AuditLog(bizType = "AUTH_PASSWORD_CHANGE", label = "修改密码", sensitive = true)  敏感接口参数不落库
 * summary 规则：不含 # 号按纯文本存；含 # 号整句按 SpEL 解析（#参数名 取方法入参），写错降级存原文不影响业务。
 * ⚠️ biz_type 与审批中心、幂等注解共用一套词表，全组在规范登记，禁止各线自造同义词。
 * 表列宽提醒：label 64、summary 500、request_params 2000，超长自动截断。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 业务类型代码（如 TEAM_CREATE），写 audit_log.biz_type */
    String bizType();

    /** 业务类型中文名，写 audit_log.biz_type_label；不填时降级存 bizType 本身 */
    String label() default "";

    /** 操作摘要：纯文本或 SpEL 模板（含 # 即按 SpEL 解析），不填时存空 */
    String summary() default "";

    /** 敏感接口（密码、身份证相关）：true 时 request_params 留空，不把敏感值写进审计 */
    boolean sensitive() default false;
}
