package com.viomanager.dev;

import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开发期数据范围探针（仅 dev profile 生效，Step 10 上线检查清单确认关闭）。
 * 返回当前用户解析出的数据范围与命中条数，验证 全部 ⊃ 本部门 ⊃ 本人。
 */
@RestController
@RequestMapping("/api/dev")
@Profile("dev")
public class ScopeProbeController {

    @Resource
    private ScopeProbeMapper scopeProbeMapper;

    /**
     * 探测当前登录用户的数据范围。
     */
    @GetMapping("/scope-probe")
    public Result<Map<String, Object>> scopeProbe() {
        //1.能进来说明过了 JWT 过滤器，身份一定有
        CurrentUser user = UserHolder.getUser();
        if (user == null) {
            return Result.fail(CommonErrorCode.UNAUTHORIZED);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("role", user.getRole());
        data.put("orgId", user.getOrgId());
        //2.ORG 声明的查询：学生会触发默认拒绝，拦下来标记 denied 而不是让接口报错。
        //   拦截器抛的 BizException 会被 MyBatis 包成 MyBatisSystemException，解包判断
        boolean denied = false;
        Long orgScopedCount = null;
        try {
            orgScopedCount = scopeProbeMapper.countMembersInScope();
        } catch (MyBatisSystemException e) {
            BizException biz = BizException.unwrap(e);
            denied = biz != null && biz.getCode() == CommonErrorCode.FORBIDDEN.getCode();
        }
        data.put("orgScopeDenied", denied);
        data.put("orgScopedMemberCount", orgScopedCount);
        //3.SELF 声明的查询：各角色都能过，条数即"本人"范围
        data.put("myLoginLogCount", scopeProbeMapper.countMyLoginLogs(user.getUserId()));
        return Result.ok(data);
    }
}
