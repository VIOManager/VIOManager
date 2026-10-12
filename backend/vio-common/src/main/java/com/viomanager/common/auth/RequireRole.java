package com.viomanager.common.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色门槛声明：标在 Controller 方法上，声明"本接口只允许哪些角色调用"。
 * 组长 2026-10-09 拍板补 Step 4.5：此前角色限制只写在接口清单文档里，靠各线手写判断，
 * 7 个人 7 种写法容易漏——漏一个就是一个学生能调的管理接口。
 * 统一为注解 + 切面强制校验（规范 §5.4 第二道关口"权限校验"的落地）。
 * 用法：
 *   @RequireRole("SUPER_ADMIN")                     仅超管
 *   @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})     管理员（两种都行）
 * 校验失败返回 1003 / HTTP 403；未登录到不了这里（JWT 过滤器先拦，1002 / 401）。
 * 刻意只支持方法级标注：类级继承容易搞出"以为限了其实没限"的歧义，逐方法写最直白。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许调用的角色名，与 sys_user.role 字段取值一致：SUPER_ADMIN / DEPT_ADMIN / STUDENT */
    String[] value();
}
