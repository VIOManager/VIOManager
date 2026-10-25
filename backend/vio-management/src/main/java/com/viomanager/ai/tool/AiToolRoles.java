package com.viomanager.ai.tool;

import com.viomanager.common.auth.CurrentUser;

/**
 * 工具层角色口径（接口清单 §2 权限列）：
 * 学生调管理域工具 → 返回无权限 JSON 给模型（模型转告用户），不炸流；
 * 数据范围本身仍由各线 service/拦截器兜底——工具层是第一道，不是唯一一道。
 */
public final class AiToolRoles {

    public static final String DENIED_JSON = "{\"error\":\"该查询仅管理角色可用，当前用户无权限\"}";

    private AiToolRoles() {
    }

    /** 管理角色 = 超级管理员或部门管理员 */
    public static boolean isAdmin(CurrentUser user) {
        return "SUPER_ADMIN".equals(user.getRole()) || "DEPT_ADMIN".equals(user.getRole());
    }
}
