package com.viomanager.person.support;

import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.MemberErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.datascop.OrgScopeSupport;
import com.viomanager.person.entity.Person;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 人员档案的"谁能看到/改到谁"判定（A 线契约 M02 四档视角 + 写接口的越权封堵）。
 * 数据来源只认 JWT 解析出的 UserHolder（伪造请求参数拿不到这里），跨包查 sys_org/sys_user
 * 走 jdbcTemplate——与审批模块判定部门子树的先例一致。
 */
@Component
public class PersonViewerSupport {

    /** M02 出参裁剪档位：全量 / 队长大部分 / 受限 */
    public enum View {
        FULL, CAPTAIN, LIMITED
    }

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private PersonTeamSupport personTeamSupport;

    @Resource
    private OrgScopeSupport orgScopeSupport;

    /**
     * M02 详情视角判定（契约：超管/本部门管理员/本人全量，本队队长大部分，其他受限）。
     */
    public View decideView(Person person) {
        CurrentUser user = UserHolder.getUser();
        if (user == null) {
            return View.LIMITED;
        }
        //1.超管全量
        if ("SUPER_ADMIN".equals(user.getRole())) {
            return View.FULL;
        }
        //2.本部门管理员（人员组织在自己子树内）全量
        if ("DEPT_ADMIN".equals(user.getRole()) && orgInMySubtree(user.getOrgId(), person.getOrgId())) {
            return View.FULL;
        }
        //3.本人全量
        Long memberId = currentMemberId(user.getUserId());
        if (memberId != null && memberId.equals(person.getId())) {
            return View.FULL;
        }
        //4.本队队长大部分（完整手机号但无身份证明文）
        if (memberId != null && personTeamSupport.isCaptainOf(memberId, person.getId())) {
            return View.CAPTAIN;
        }
        //5.其余一律受限
        return View.LIMITED;
    }

    /**
     * M10 身份证 / M11 证件照签名的查看判定：视角达到 CAPTAIN 及以上才放行，否则 3010。
     */
    public void assertCanViewSensitive(Person person) {
        if (decideView(person) == View.LIMITED) {
            throw new BizException(MemberErrorCode.MEMBER_SENSITIVE_DENIED);
        }
    }

    /**
     * M12 技能编辑判定：本人或管理员（部门管理员限本人子树），其他 1003。
     */
    public void assertCanEditSkills(Person person) {
        CurrentUser user = UserHolder.getUser();
        if (user == null) {
            throw new BizException(CommonErrorCode.FORBIDDEN);
        }
        if ("SUPER_ADMIN".equals(user.getRole()) || "DEPT_ADMIN".equals(user.getRole())) {
            assertAdminCanManage(person.getOrgId());
            return;
        }
        Long memberId = currentMemberId(user.getUserId());
        if (memberId == null || !memberId.equals(person.getId())) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "仅本人或管理员可修改技能标签");
        }
    }

    /**
     * 写接口（M03/M04/M05/M08/M09/M13）的管理员越权封堵：
     * 超管放行；部门管理员限目标组织在自己子树内；其他角色拒（controller 注解已挡，防御性兜底）。
     */
    public void assertAdminCanManage(Long targetOrgId) {
        CurrentUser user = UserHolder.getUser();
        if (user == null || !"SUPER_ADMIN".equals(user.getRole()) && !"DEPT_ADMIN".equals(user.getRole())) {
            throw new BizException(CommonErrorCode.FORBIDDEN);
        }
        if ("SUPER_ADMIN".equals(user.getRole())) {
            return;
        }
        if (!orgInMySubtree(user.getOrgId(), targetOrgId)) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "无权管理该组织的人员");
        }
    }

    /**
     * 当前登录账号绑定的档案 ID（sys_user.member_id）：本人判定用，没绑档案返回 null。
     */
    public Long currentMemberId(Long userId) {
        if (userId == null) {
            return null;
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT member_id FROM sys_user WHERE id = ? AND deleted = 0 AND member_id IS NOT NULL",
                Long.class, userId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * 部门管理员的组织子树 ID 列表（含自己）——M01 列表的服务端过滤。
     * 判定收口到 OrgScopeSupport（插入点①）：宽松口子保住现行为（没配组织/组织不存在返回空列表），
     * 开 vio.cache.org-enabled 后自动带 Redis 缓存与降级直查库。
     */
    public List<Long> querySubtreeOrgIds(Long rootOrgId) {
        return orgScopeSupport.subtreeIdsOrEmpty(rootOrgId);
    }

    /**
     * 目标组织是否在 rootOrgId 的子树内（含 root 自身）——部门管理员的归属判定。
     * 收口到 OrgScopeSupport 后查询失败统一按"不在子树"处理（原实现异常直冒 500，
     * 现 fail-closed 走受限档，更安全）；任一端为空仍判不在。
     */
    private boolean orgInMySubtree(Long rootOrgId, Long targetOrgId) {
        return orgScopeSupport.inSubtree(rootOrgId, targetOrgId);
    }
}
