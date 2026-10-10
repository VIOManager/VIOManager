package com.viomanager.dev;

import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.result.Result;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Step 4.5 验证探针：两个真实标注 @RequireRole 的接口，三种角色各打一遍验证拦截。
 * @Profile("dev") 只在本地开发环境注册，生产不暴露（上线检查清单核对项）。
 */
@RestController
@RequestMapping("/api/dev/role-probe")
@Profile("dev")
public class RequireRoleProbeController {

    /** 仅超管可调 */
    @RequireRole("SUPER_ADMIN")
    @GetMapping("/super-only")
    public Result<Map<String, Object>> superOnly() {
        return currentRole();
    }

    /** 两种管理员都可调 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping("/admin-both")
    public Result<Map<String, Object>> adminBoth() {
        return currentRole();
    }

    /** 回显当前登录人角色，联调时直观确认"谁进来了" */
    private Result<Map<String, Object>> currentRole() {
        CurrentUser user = UserHolder.getUser();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("role", user.getRole());
        data.put("userId", user.getUserId());
        return Result.ok(data);
    }
}
