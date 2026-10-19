package com.viomanager.material.support;

import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.datascop.OrgScopeSupport;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * C 线队伍维度的组织范围校验（docs/README.md 第 28 条：部门管理员只看本部门组织子树，越权 1003）。
 *
 * 为什么不在 mapper 上用 @DataScope(ORG)：拦截器的 ORG 模式会拒绝学生，而 C 线学生同样要读写
 * 这些表（MS02/A06 按在册成员收敛）——所以数据范围在 service 层显式校验，本类只拦部门管理员：
 * 超管与学生都不经过这里（超管全量，学生由在册成员校验负责）。
 *
 * 子树判定复用骨架统一收口 com.viomanager.datascop.OrgScopeSupport（Redis cache-aside +
 * 熔断降级直查库），本类只补「队伍 → org_id」一步与角色分流，不重复造子树查询。
 * ⚠️ 类名刻意避开 OrgScopeSupport：与骨架类同名会撞 Spring 默认 bean 名
 * （实测 ConflictingBeanDefinitionException，编译期发现不了）。
 */
@Component
public class TeamOrgScopeSupport {

    @Resource
    private OrgScopeSupport orgScopeSupport;

    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 断言队伍在当前部门管理员的组织子树内（越权 1003「无权操作其他部门的队伍」）：
     * 仅部门管理员受检——超管（角色不同）与学生（由在册成员校验负责）直接放行；
     * fail-closed：队伍不存在 / 已删、管理员未配组织都判不在子树——宁可误拒不可误放，
     * 也不向外部门管理员泄露队伍是否存在。
     */
    public void assertTeamInScope(Long teamId) {
        CurrentUser user = UserHolder.getUser();
        if (user == null || !"DEPT_ADMIN".equals(user.getRole())) {
            return;
        }
        if (!orgScopeSupport.inSubtree(user.getOrgId(), loadTeamOrgId(teamId))) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "无权操作其他部门的队伍");
        }
    }

    /** 查队伍归属组织（不存在 / 已删 / teamId 为空返回 null，交 inSubtree 按 fail-closed 拒）；手写 SQL 显式带 deleted = 0 */
    private Long loadTeamOrgId(Long teamId) {
        if (teamId == null) {
            return null;
        }
        List<Long> orgIds = jdbcTemplate.queryForList(
                "SELECT org_id FROM team WHERE id = ? AND deleted = 0", Long.class, teamId);
        return orgIds.isEmpty() ? null : orgIds.get(0);
    }
}
