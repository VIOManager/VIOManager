package com.viomanager.common.auth;

/**
 * JWT 附加校验的扩展点（按骨架方案 §2.1 预留）。
 * 管理系统装配实现：查库校验账号停用 + 改密时间比对（见 management 侧 ManagementTokenChecker）；
 * 阶段2 论坛服务不装配此 Bean，过滤器只做验签，不查管理库——这是微服务边界（架构调整方案 §4）。
 */
public interface AuthTokenChecker {

    /**
     * 对验签通过的用户做额外校验，不通过直接抛 BizException(1002)。
     */
    void check(CurrentUser user);
}
