package com.viomanager.project.support;

import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.enums.ProjectErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.project.entity.Project;
import com.viomanager.project.mapper.ProjectMapper;
import jakarta.annotation.Resource;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 项目可见性判定（清单 §2 八条顺序规则，全模块最复杂规则）——所有读接口的唯一收口，
 * 任何地方不得自行实现第二份判定逻辑。
 *
 * 判定顺序（命中任一即可见）：
 *  1 所有人  2 成员/指导教师  3 超管  4 部门管理员（项目组织在其子树内）
 *  5 PUBLIC  6 ORG（用户组织在项目组织子树内）  7 ACTIVE 授权  8 拒绝（7003）
 *
 * 列表口径与单对象不同（清单 PR01）：学生列表 = 参与的（所有人/成员/教师）∪ 授权可见的，
 * 不含 PUBLIC/ORG 全量（防止列表被无关项目淹没）；PUBLIC/ORG 只影响单项目详情可达性。
 * 写接口一律仅所有人（7002），不走本判定。
 */
@Slf4j
@Component
public class ProjectVisibilitySupport {

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 单项目可见性判定（详情/编辑/删除等单对象入口） */
    public boolean canView(Project project, CurrentUser user, Long memberId) {
        //1-6 条：身份与可见范围（不含授权）
        if (matchesWithoutGrant(project, user, memberId)) {
            return true;
        }
        //7 生效中的查看授权（ONCE 未消耗 / LIMITED 未到期）
        //8 都不满足
        return memberId != null && hasActiveGrant(project.getId(), memberId);
    }

    /** 第 1-6 条（所有人/超管/成员教师/部门管理员/PUBLIC/ORG），不含第 7 条授权 */
    private boolean matchesWithoutGrant(Project project, CurrentUser user, Long memberId) {
        //1 所有人
        if (memberId != null && memberId.equals(project.getOwnerMemberId())) {
            return true;
        }
        //2 超管（清单待确认①：按全系统"超管看全部"惯例，非 PRD 字面的"本部门"）
        if ("SUPER_ADMIN".equals(user.getRole())) {
            return true;
        }
        //3 成员 / 指导教师（指导教师只读，写权限在 service 另行判 7002）
        if (memberId != null && isParticipant(project.getId(), memberId)) {
            return true;
        }
        //4 部门管理员：项目所属组织在其管辖子树内（orgId 为空时从严不可见）
        if ("DEPT_ADMIN".equals(user.getRole())
                && project.getOrgId() != null && user.getOrgId() != null
                && orgInSubtree(project.getOrgId(), orgPath(user.getOrgId()))) {
            return true;
        }
        //5 全平台可见
        if ("PUBLIC".equals(project.getVisibility())) {
            return true;
        }
        //6 本部门可见：用户组织在项目组织子树内（项目挂子组织时，该子树内用户可见）
        return "ORG".equals(project.getVisibility())
                && project.getOrgId() != null && user.getOrgId() != null
                && orgInSubtree(user.getOrgId(), orgPath(project.getOrgId()));
    }

    /** 读接口统一入口：存在（7001）+ 可见（7003）一步到位 */
    public Project requireVisibleProject(Long projectId, CurrentUser user, Long memberId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(ProjectErrorCode.NOT_FOUND);
        }
        if (!canView(project, user, memberId)) {
            throw new BizException(ProjectErrorCode.VIEW_DENIED);
        }
        return project;
    }

    /**
     * PR03 详情专用入口：仅当靠第 7 条授权才可见时消耗 ONCE（清单 PR03：ONCE 授权
     * 在此消耗，used_count 加一、用完置 EXPIRED）；靠 1-6 条可见的人不碰授权。
     * 并发双耗防多扣：条件 UPDATE（WHERE status='ACTIVE'）只有一个线程扣到，
     * 没扣到的线程按 7003 拒绝——一次性授权语义下并发两次浏览只放行一次。
     */
    public Project requireVisibleProjectConsumingOnce(Long projectId, CurrentUser user,
                                                       Long memberId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(ProjectErrorCode.NOT_FOUND);
        }
        //1-6 条可见：与授权无关，不消耗
        if (matchesWithoutGrant(project, user, memberId)) {
            return project;
        }
        //第 7 条：找到 ACTIVE 授权（ONCE/LIMITED 都行——LIMITED 浏览不消耗）
        if (memberId == null) {
            throw new BizException(ProjectErrorCode.VIEW_DENIED);
        }
        List<Long> grantIds = jdbcTemplate.queryForList(
                "SELECT id FROM project_view_grant WHERE project_id = ? AND applicant_id = ? "
                        + "AND status = 'ACTIVE' AND deleted = 0 "
                        + "AND (expire_at IS NULL OR expire_at > NOW()) ORDER BY id LIMIT 1",
                Long.class, projectId, memberId);
        if (grantIds.isEmpty()) {
            throw new BizException(ProjectErrorCode.VIEW_DENIED);
        }
        Long grantId = grantIds.get(0);
        //ONCE 才消耗：用一次即 EXPIRED；并发下只有一个线程扣到（0 行=已被并发消耗→拒绝）
        var grantType = jdbcTemplate.queryForObject(
                "SELECT grant_type FROM project_view_grant WHERE id = ?", String.class, grantId);
        if ("ONCE".equals(grantType)) {
            int rows = jdbcTemplate.update(
                    "UPDATE project_view_grant SET used_count = used_count + 1, status = 'EXPIRED', "
                            + "updated_at = updated_at "
                            + "WHERE id = ? AND grant_type = 'ONCE' AND status = 'ACTIVE' AND deleted = 0",
                    grantId);
            if (rows == 0) {
                throw new BizException(ProjectErrorCode.VIEW_DENIED);
            }
        }
        return project;
    }

    /**
     * 学生列表口径（PR01）：参与的（所有人/成员/指导教师）∪ 授权可见的。
     * 一次 UNION 集合查询（方案 §7.8 集合化判定），不做逐项目判定。
     */
    public List<Long> studentVisibleIds(Long memberId) {
        if (memberId == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
                "SELECT id FROM project WHERE owner_member_id = ? AND deleted = 0 "
                        + "UNION SELECT project_id FROM project_member WHERE member_id = ? AND deleted = 0 "
                        + "UNION SELECT project_id FROM project_advisor WHERE member_id = ? AND deleted = 0 "
                        + "UNION SELECT project_id FROM project_view_grant "
                        + "WHERE applicant_id = ? AND status = 'ACTIVE' AND deleted = 0",
                Long.class, memberId, memberId, memberId, memberId);
    }

    /** 是否项目成员或指导教师（可见性第 3 条） */
    private boolean isParticipant(Long projectId, Long memberId) {
        Integer memberCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project_member WHERE project_id = ? AND member_id = ? AND deleted = 0",
                Integer.class, projectId, memberId);
        if (memberCount != null && memberCount > 0) {
            return true;
        }
        Integer advisorCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project_advisor WHERE project_id = ? AND member_id = ? AND deleted = 0 "
                        + "AND status = 'ACTIVE'",
                Integer.class, projectId, memberId);
        return advisorCount != null && advisorCount > 0;
    }

    /** 是否有生效中的查看授权（可见性第 7 条；LIMITED 到期视失效） */
    private boolean hasActiveGrant(Long projectId, Long memberId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project_view_grant WHERE project_id = ? AND applicant_id = ? "
                        + "AND status = 'ACTIVE' AND deleted = 0 "
                        + "AND (expire_at IS NULL OR expire_at > NOW())",
                Integer.class, projectId, memberId);
        return count != null && count > 0;
    }

    /** 组织的 sys_org.path（如 /0/9301/），不存在返回 null */
    private String orgPath(Long orgId) {
        List<String> paths = jdbcTemplate.queryForList(
                "SELECT path FROM sys_org WHERE id = ? AND deleted = 0", String.class, orgId);
        return paths.isEmpty() ? null : paths.get(0);
    }

    /** targetOrgId 是否在 path 前缀的子树内（含自身；同骨架文件服务口径） */
    private boolean orgInSubtree(Long targetOrgId, String rootPath) {
        if (rootPath == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_org WHERE id = ? AND deleted = 0 AND path LIKE CONCAT(?, '%')",
                Integer.class, targetOrgId, rootPath);
        return count != null && count > 0;
    }
}
