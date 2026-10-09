package com.viomanager.common.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等声明注解：标在"创建、审批、提交"类写接口上（规范 §6 写操作防护）。
 * 标了本注解的方法，调用时必须带请求头 X-Idempotency-Key：
 *  - 同 key 未过期 → 1005 重复提交（K3 拍板：拦截而非回放首次结果）
 *  - 同 key 不同请求体 → 仍拦截，但后端日志告警（前端键生成有问题的信号）
 *  - 业务执行失败 → key 释放，允许换新键或同键重试
 * 用法：@Idempotent(bizType = "TEAM_CREATE")
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * 业务类型：写进 idempotent_record.biz_type，与审批中心的 bizType 共用一套词表
     * （TEAM_CREATE、ACHIEVEMENT_SUBMIT 等，全组统一在规范登记，禁止各线自造同义词）
     */
    String bizType();
}
