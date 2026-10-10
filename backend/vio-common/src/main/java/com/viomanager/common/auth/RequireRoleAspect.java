package com.viomanager.common.auth;

import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 角色校验切面：@RequireRole 声明的接口，当前用户角色不在允许列表 → 1003（HTTP 403）。
 * 运行顺序刻意排在幂等切面之前（@Order 值更小 = 更外层）：
 * 没权限的请求不该烧掉一个幂等键。未登录理论上到不了这里（JWT 过滤器先拦 1002），
 * 双保险仍按未登录处理。
 * ⚠️ 阶段2 论坛服务复用同一 jar 时，各服务自己的接口同样可用本注解。
 */
@Slf4j
@Aspect
@Component
@Order(1)
public class RequireRoleAspect {

    @Before("@annotation(requireRole)")
    public void check(RequireRole requireRole) {
        //1.取当前登录人（双保险：正常情况下 JWT 过滤器已保证有人）
        CurrentUser user = UserHolder.getUser();
        if (user == null) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED);
        }
        //2.角色在允许列表里就放行
        String role = user.getRole();
        String[] allowed = requireRole.value();
        for (int i = 0; i < allowed.length; i++) {
            if (allowed[i].equals(role)) {
                return;
            }
        }
        //3.不在列表：拒绝 + 留痕（谁在什么时候试图调不属于自己的接口，日志可查）
        log.warn("角色校验拒绝：userId={}, role={}, 接口要求={}", user.getUserId(), role, String.join("/", allowed));
        throw new BizException(CommonErrorCode.FORBIDDEN, "该操作仅限：" + String.join(" / ", allowed));
    }
}
