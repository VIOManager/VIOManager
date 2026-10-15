package com.viomanager.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.ProjectErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.material.support.MemberIdSupport;
import com.viomanager.project.dto.TeamProjectAttachDTO;
import com.viomanager.project.entity.Project;
import com.viomanager.project.entity.TeamProjectRef;
import com.viomanager.project.mapper.ProjectMapper;
import com.viomanager.project.mapper.TeamProjectRefMapper;
import com.viomanager.project.service.TeamProjectService;
import com.viomanager.project.support.MilestoneSyncSupport;
import com.viomanager.project.support.ProjectQuerySupport;
import com.viomanager.project.support.ProjectVisibilitySupport;
import com.viomanager.project.vo.ProjectTeamVO;
import com.viomanager.project.vo.ProjectVO;
import com.viomanager.project.vo.TeamProjectVO;
import jakarta.annotation.Resource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 赛队挂载实现（PR11–PR15）。
 * 队长身份读 B 线 team.leader_member_id 冗余列（快照权威，顺带拿学期/组织/比赛零额外查询）；
 * 赛道校验读 contest_track（须属该比赛且启用）。
 * 防重口径：7007 先查后插给业务码——uk_team_project 含可空 track_id，
 * MySQL 唯一键对 NULL 不生效，纯靠索引兜不住，必须查 ACTIVE 挂载（&lt;=&gt; NULL 安全比较）。
 * 重挂口径：解绑不删行（保留 RELEASED 历史），同键再挂载复用该行置回 ACTIVE——
 * uk(team_id, project_id, track_id, deleted) 下同键活行只允许一行，直接 INSERT 必撞键。
 */
@Slf4j
@Service
public class TeamProjectServiceImpl implements TeamProjectService {

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private TeamProjectRefMapper refMapper;

    @Resource
    private ProjectVisibilitySupport visibilitySupport;

    @Resource
    private ProjectQuerySupport querySupport;

    @Resource
    private MilestoneSyncSupport syncSupport;

    @Resource
    private MemberIdSupport memberIdSupport;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public Result<List<ProjectTeamVO>> listTeamsOfProject(Long projectId) {
        CurrentUser user = UserHolder.getUser();
        Long viewerId = memberIdSupport.currentMemberId(user);
        //项目可见者可看（7001/7003 收口）
        visibilitySupport.requireVisibleProject(projectId, user, viewerId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT team_id, track_id, attached_by, attached_at, status "
                        + "FROM team_project_ref WHERE project_id = ? AND deleted = 0 "
                        + "ORDER BY attached_at DESC, id DESC", projectId);
        List<ProjectTeamVO> vos = rows.stream().map(row -> {
            ProjectTeamVO vo = new ProjectTeamVO();
            vo.setTeamId(longOf(row.get("team_id")));
            vo.setTrackName(null);
            vo.setAttachedByName(null);
            vo.setAttachedAt(toLocalDateTime(row.get("attached_at")));
            vo.setStatus((String) row.get("status"));
            return vo;
        }).toList();
        if (vos.isEmpty()) {
            return Result.ok(vos);
        }
        //批量装配：队名+比赛名（team IN）、赛道名（contest_track IN）、挂载人姓名（member IN）
        Map<Long, Map<String, Object>> teams = queryRows("SELECT id, name, contest_id FROM team "
                + "WHERE id IN (" + idsOf(vos.stream().map(ProjectTeamVO::getTeamId)) + ") "
                + "AND deleted = 0");
        Map<Long, String> trackNames = namesById("contest_track",
                rows.stream().map(r -> longOf(r.get("track_id"))).collect(Collectors.toSet()));
        Map<Long, String> memberNames = namesById("member",
                rows.stream().map(r -> longOf(r.get("attached_by"))).collect(Collectors.toSet()));
        Map<Long, String> contestNames = namesById("contest",
                teams.values().stream().map(t -> longOf(t.get("contest_id")))
                        .collect(Collectors.toSet()));
        for (int i = 0; i < rows.size(); i++) {
            ProjectTeamVO vo = vos.get(i);
            Map<String, Object> team = teams.get(vo.getTeamId());
            if (team != null) {
                vo.setTeamName((String) team.get("name"));
                vo.setContestName(contestNames.get(longOf(team.get("contest_id"))));
            }
            vo.setTrackName(trackNames.get(longOf(rows.get(i).get("track_id"))));
            vo.setAttachedByName(memberNames.get(longOf(rows.get(i).get("attached_by"))));
        }
        return Result.ok(vos);
    }

    @Override
    public Result<List<TeamProjectVO>> listProjectsOfTeam(Long teamId) {
        CurrentUser user = UserHolder.getUser();
        Long viewerId = memberIdSupport.currentMemberId(user);
        requireTeamViewable(teamId, user, viewerId);
        //挂载行 JOIN 项目行（项目逻辑删后不回显）
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT r.project_id, r.track_id, r.attached_at, r.status AS mount_status, "
                        + "p.name, p.owner_name, p.status AS project_status, p.visibility "
                        + "FROM team_project_ref r JOIN project p ON p.id = r.project_id AND p.deleted = 0 "
                        + "WHERE r.team_id = ? AND r.deleted = 0 "
                        + "ORDER BY r.attached_at DESC, r.id DESC", teamId);
        Map<Long, Map<String, Object>> teamRow = queryRows(
                "SELECT id, contest_id FROM team WHERE id = ? AND deleted = 0", teamId);
        Long contestId = teamRow.isEmpty() ? null : longOf(teamRow.values().iterator().next().get("contest_id"));
        Map<Long, String> trackNames = namesById("contest_track",
                rows.stream().map(r -> longOf(r.get("track_id"))).collect(Collectors.toSet()));
        String contestName = contestId == null ? null
                : namesById("contest", java.util.Set.of(contestId)).get(contestId);
        List<TeamProjectVO> vos = rows.stream().map(row -> {
            TeamProjectVO vo = new TeamProjectVO();
            vo.setProjectId(longOf(row.get("project_id")));
            vo.setName((String) row.get("name"));
            vo.setOwnerName((String) row.get("owner_name"));
            vo.setProjectStatus((String) row.get("project_status"));
            vo.setVisibility((String) row.get("visibility"));
            vo.setContestName(contestName);
            vo.setTrackName(trackNames.get(longOf(row.get("track_id"))));
            vo.setAttachedAt(toLocalDateTime(row.get("attached_at")));
            vo.setMountStatus((String) row.get("mount_status"));
            return vo;
        }).toList();
        return Result.ok(vos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> attach(Long teamId, TeamProjectAttachDTO dto) {
        CurrentUser user = UserHolder.getUser();
        Long operatorId = memberIdSupport.currentMemberId(user);
        //1.赛队存在且未解散（1004/1006）；队长校验（leader_member_id 冗余快照，非队长 1003）
        Map<String, Object> team = requireTeam(teamId);
        if (!"ACTIVE".equals(team.get("status"))) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED);
        }
        if (operatorId == null || !operatorId.equals(longOf(team.get("leader_member_id")))) {
            throw new BizException(CommonErrorCode.FORBIDDEN);
        }
        //2.项目可见（7001/7003 走八条规则——队长本人必须看得到才能挂）
        Project project = visibilitySupport.requireVisibleProject(dto.getProjectId(), user, operatorId);
        //3.同学期（7008）：项目与赛队必须同一学期
        if (!project.getSemesterId().equals(longOf(team.get("semester_id")))) {
            throw new BizException(ProjectErrorCode.SEMESTER_MISMATCH);
        }
        //4.赛道可选校验：传了必须属于该比赛且启用（防挂错比赛的赛道）
        Long contestId = longOf(team.get("contest_id"));
        if (dto.getTrackId() != null) {
            Integer trackCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM contest_track WHERE id = ? AND contest_id = ? "
                            + "AND status = 1 AND deleted = 0",
                    Integer.class, dto.getTrackId(), contestId);
            if (trackCount == null || trackCount == 0) {
                throw new BizException(CommonErrorCode.PARAM_INVALID);
            }
        }
        //5.防重（7007）：同队同项目同赛道仍有 ACTIVE 挂载则拒。
        //   track_id 可空用 <=> NULL 安全比较；uk 对 NULL 轨道不生效，必须先查
        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_project_ref WHERE team_id = ? AND project_id = ? "
                        + "AND track_id <=> ? AND status = 'ACTIVE' AND deleted = 0",
                Integer.class, teamId, dto.getProjectId(), dto.getTrackId());
        if (activeCount != null && activeCount > 0) {
            throw new BizException(ProjectErrorCode.ALREADY_MOUNTED);
        }
        //6.落挂载行：contest/semester 冗余自赛队（校验已保证一致）。
        //   同键已解绑（RELEASED）的行复用置回 ACTIVE——uk 下同键活行仅一行，直接 INSERT 必撞键
        TeamProjectRef released = refMapper.selectOne(
                new QueryWrapper<TeamProjectRef>().eq("team_id", teamId)
                        .eq("project_id", dto.getProjectId())
                        .apply("track_id <=> {0}", dto.getTrackId())
                        .eq("status", "RELEASED"));
        if (released != null) {
            //复用置回 ACTIVE：released_at 须显式置 NULL——updateById 的 NOT_NULL 策略跳过 null 字段清不掉
            jdbcTemplate.update(
                    "UPDATE team_project_ref SET status = 'ACTIVE', attached_by = ?, "
                            + "attached_at = NOW(), released_at = NULL, updated_by = ? WHERE id = ?",
                    operatorId, user.getUserId(), released.getId());
        } else {
            TeamProjectRef ref = new TeamProjectRef();
            ref.setTeamId(teamId);
            ref.setProjectId(dto.getProjectId());
            ref.setContestId(contestId);
            ref.setTrackId(dto.getTrackId());
            ref.setSemesterId(longOf(team.get("semester_id")));
            ref.setStatus("ACTIVE");
            ref.setAttachedBy(operatorId);
            refMapper.insert(ref);
        }
        //挂载回溯：补建该项目在该比赛下缺的已审核节点任务（行锁内存在性检查防重）
        syncSupport.syncOnAttach(dto.getProjectId(), contestId);
        return Result.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> detach(Long teamId, Long projectId) {
        CurrentUser user = UserHolder.getUser();
        Long operatorId = memberIdSupport.currentMemberId(user);
        Map<String, Object> team = requireTeam(teamId);
        if (operatorId == null || !operatorId.equals(longOf(team.get("leader_member_id")))) {
            throw new BizException(CommonErrorCode.FORBIDDEN);
        }
        //只解 ACTIVE 行（已解绑的再解=不存在，1004）；已生成的任务不回收（清单 PR14）
        TeamProjectRef ref = refMapper.selectOne(
                new QueryWrapper<TeamProjectRef>().eq("team_id", teamId)
                        .eq("project_id", projectId).eq("status", "ACTIVE"));
        if (ref == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND);
        }
        TeamProjectRef patch = new TeamProjectRef();
        patch.setId(ref.getId());
        patch.setStatus("RELEASED");
        patch.setReleasedAt(LocalDateTime.now());
        refMapper.updateById(patch);
        return Result.ok();
    }

    @Override
    public Result<List<ProjectVO>> attachable(Long contestId) {
        CurrentUser user = UserHolder.getUser();
        Long memberId = memberIdSupport.currentMemberId(user);
        //口径（清单 PR15）：PUBLIC 全部 + ORG 同部门的（用户组织在项目组织子树内，规则 6 同向）
        //+ 自己参与的（所有人/成员/指导教师；授权可见不算参与，不进池）
        QueryWrapper<Project> qw = new QueryWrapper<>();
        qw.and(w -> {
            w.eq("visibility", "PUBLIC");
            List<Long> ancestorOrgs = ancestorOrgIds(user.getOrgId());
            if (!ancestorOrgs.isEmpty()) {
                //项目组织在用户的祖先链上 = 用户在项目组织子树内（含同组织）
                w.or(w2 -> w2.eq("visibility", "ORG").in("org_id", ancestorOrgs));
            }
            List<Long> participated = participatedIds(memberId);
            if (!participated.isEmpty()) {
                w.or(w3 -> w3.in("id", participated));
            }
        });
        qw.eq(contestId != null, "contest_id", contestId).orderByDesc("updated_at");
        return Result.ok(querySupport.toCards(projectMapper.selectList(qw)));
    }

    /** PR12 权限：本队成员（在队未退出）或超管或部门管理员（赛队组织在其子树内） */
    private void requireTeamViewable(Long teamId, CurrentUser user, Long memberId) {
        if ("SUPER_ADMIN".equals(user.getRole())) {
            return;
        }
        if ("DEPT_ADMIN".equals(user.getRole()) && user.getOrgId() != null) {
            Map<String, Object> team = requireTeam(teamId);
            String adminPath = orgPath(user.getOrgId());
            if (adminPath != null && longOf(team.get("org_id")) != null
                    && orgInSubtree(longOf(team.get("org_id")), adminPath)) {
                return;
            }
            throw new BizException(CommonErrorCode.FORBIDDEN);
        }
        if (memberId != null) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM team_member WHERE team_id = ? AND member_id = ? "
                            + "AND status IN ('UNCONFIRMED', 'CONFIRMED') AND deleted = 0",
                    Integer.class, teamId, memberId);
            if (count != null && count > 0) {
                return;
            }
        }
        throw new BizException(CommonErrorCode.FORBIDDEN);
    }

    /** 加载赛队（1004 不存在），返回 id/semester_id/org_id/contest_id/leader_member_id/status */
    private Map<String, Object> requireTeam(Long teamId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, name, contest_id, semester_id, org_id, leader_member_id, status "
                        + "FROM team WHERE id = ? AND deleted = 0", teamId);
        if (rows.isEmpty()) {
            throw new BizException(CommonErrorCode.NOT_FOUND);
        }
        return rows.get(0);
    }

    /** 参与的项目 ID 集（所有人/成员/指导教师，不含授权——清单 PR15 口径） */
    private List<Long> participatedIds(Long memberId) {
        if (memberId == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
                "SELECT id FROM project WHERE owner_member_id = ? AND deleted = 0 "
                        + "UNION SELECT project_id FROM project_member WHERE member_id = ? AND deleted = 0 "
                        + "UNION SELECT project_id FROM project_advisor WHERE member_id = ? AND deleted = 0",
                Long.class, memberId, memberId, memberId);
    }

    /** 用户的祖先组织 ID 集（含自身组织）——项目组织在其中=用户在项目组织子树内 */
    private List<Long> ancestorOrgIds(Long orgId) {
        if (orgId == null) {
            return List.of();
        }
        String path = orgPath(orgId);
        if (path == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
                "SELECT id FROM sys_org WHERE ? LIKE CONCAT(path, '%') AND deleted = 0",
                Long.class, path);
    }

    private String orgPath(Long orgId) {
        List<String> paths = jdbcTemplate.queryForList(
                "SELECT path FROM sys_org WHERE id = ? AND deleted = 0", String.class, orgId);
        return paths.isEmpty() ? null : paths.get(0);
    }

    private boolean orgInSubtree(Long targetOrgId, String rootPath) {
        if (rootPath == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_org WHERE id = ? AND deleted = 0 AND path LIKE CONCAT(?, '%')",
                Integer.class, targetOrgId, rootPath);
        return count != null && count > 0;
    }

    /** 按 id 键化查询结果（含单参重载） */
    private Map<Long, Map<String, Object>> queryRows(String sql, Object... args) {
        Map<Long, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql, args)) {
            result.put(longOf(row.get("id")), row);
        }
        return result;
    }

    /** 批量取名字（一条 IN；null id 自动被过滤） */
    private Map<Long, String> namesById(String table, java.util.Set<Long> ids) {
        Map<Long, String> result = new HashMap<>();
        java.util.Set<Long> valid = ids.stream().filter(id -> id != null)
                .collect(Collectors.toSet());
        if (valid.isEmpty()) {
            return result;
        }
        jdbcTemplate.query(
                "SELECT id, name FROM " + table + " WHERE id IN ("
                        + valid.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")",
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        result.put(rs.getLong("id"), rs.getString("name")));
        return result;
    }

    /** ID 集拼接 IN 子句串（去 null 去重） */
    private String idsOf(java.util.stream.Stream<Long> ids) {
        return ids.filter(id -> id != null).distinct()
                .map(String::valueOf).collect(Collectors.joining(","));
    }

    private Long longOf(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    /** DATETIME 健壮转换：驱动版本差异下 queryForList 可能回 Timestamp 或 LocalDateTime */
    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return null;
    }
}
