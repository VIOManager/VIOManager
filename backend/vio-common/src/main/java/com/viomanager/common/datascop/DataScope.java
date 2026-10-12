package com.viomanager.common.datascop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据范围声明（规范 §5.2：数据范围后端强制）。
 * 标在 mapper 方法上（优先）或 mapper 接口上（整个 mapper 兜底），由 DataScopeInterceptor 执行。
 * 三种取值：
 *  - ORG（默认）：超管放行；部门管理员注入 orgColumn IN (本部门子树)；学生拒绝
 *  - SELF：业务 SQL 自带"本人参与"条件（如 user_id = 当前人），各角色放行不注入
 *  - ALL：公共数据（字典、版块），各角色放行不注入
 * 没有任何声明时：超管/部门管理员放行不注入；学生默认拒绝——漏写"本人"条件不会泄露全量数据
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface DataScope {

    /**
     * 范围类型，默认 ORG
     */
    Scope scope() default Scope.ORG;

    /**
     * ORG 模式下注入过滤的列名，默认 org_id（表里组织列不叫这个就在注解里改）
     */
    String orgColumn() default "org_id";

    enum Scope {
        /** 按组织子树过滤 */
        ORG,
        /** 业务 SQL 自带本人条件 */
        SELF,
        /** 公共数据，不过滤 */
        ALL
    }
}
