package com.viomanager.dev;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Step 6 验证探针：给 @AuditLog 注解两个真实可打的接口。
 * 一个验证 SpEL 摘要与全字段落库；一个叠加 @RequireRole，验证"被拒的调用同样留痕"。
 * @Profile("dev") 只在本地开发环境注册，生产不暴露（上线检查清单核对项）。
 */
@RestController
@RequestMapping("/api/dev")
@Profile("dev")
public class AuditProbeController {

    /** 审计探针：SpEL 摘要拼接入参、参数摘要落库、fail 模拟业务失败 */
    @AuditLog(bizType = "DEV_AUDIT_PROBE", label = "审计探针", summary = "'操作内容：' + #body.name")
    @PostMapping("/audit-probe")
    public Result<Map<String, Object>> auditProbe(@RequestBody AuditProbeDTO body) {
        // fail=true 模拟业务失败：审计要记 success=0 和失败原因
        if (body.isFail()) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "探针模拟业务失败");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", body.getName());
        return Result.ok(data);
    }

    /** 审计 + 角色双注解探针：学生调这个接口被 403，但审计照样留一行失败记录（切面顺序验证） */
    @AuditLog(bizType = "DEV_AUDIT_PROBE_SUPER", label = "审计探针-仅超管", summary = "超管专属操作")
    @RequireRole("SUPER_ADMIN")
    @GetMapping("/audit-probe/super")
    public Result<Map<String, Object>> superAuditProbe() {
        CurrentUser user = UserHolder.getUser();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("role", user.getRole());
        return Result.ok(data);
    }

    /** 探针请求体：name 进 SpEL 摘要、fail 控制是否模拟业务失败 */
    @Data
    public static class AuditProbeDTO {
        private String name;
        private boolean fail;
    }
}
