package com.viomanager.team.support;

import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.team.mapper.AdvisorRow;
import com.viomanager.team.mapper.MemberRow;
import com.viomanager.team.mapper.TeamListRow;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/**
 * 赛队查询数据访问辅助：JdbcTemplate 显式列名查询 + 组织子树/人员 ID 解析。
 * 所有 SQL 禁用 SELECT *，显式列举列名。可見性判定（resolveViewerRole）由 Service 层调用。
 */
@Slf4j
@Component
public class TeamQuerySupport {

    /** team 关联 contest/track/队长名 + 成员/指导教师计数（列表与详情共用 SELECT 基线） */
    public static final String TEAM_SELECT =
            "SELECT t.id, t.name, t.project_name, t.contest_id, c.name AS contest_name, "
                    + "t.track_id, ct.name AS track_name, t.leader_member_id, m.name AS leader_name, "
                    + "t.status, t.material_status, t.material_complete_rate, t.org_id, t.semester_id, "
                    + "t.created_at, "
                    + "(SELECT COUNT(*) FROM team_member tm WHERE tm.team_id = t.id AND tm.deleted = 0 "
                    + "  AND tm.status IN ('UNCONFIRMED','CONFIRMED')) AS member_count, "
                    + "(SELECT COUNT(*) FROM team_advisor ta WHERE ta.team_id = t.id AND ta.deleted = 0 "
                    + "  AND ta.status = 'ACTIVE') AS advisor_count "
                    + "FROM team t "
                    + "LEFT JOIN contest c ON t.contest_id = c.id AND c.deleted = 0 "
                    + "LEFT JOIN contest_track ct ON t.track_id = ct.id AND ct.deleted = 0 "
                    + "LEFT JOIN member m ON t.leader_member_id = m.id AND m.deleted = 0 ";

    private static final RowMapper<TeamListRow> ROW_MAPPER = (rs, n) -> {
        TeamListRow row = new TeamListRow();
        row.setId(rs.getLong("id"));
        row.setName(rs.getString("name"));
        row.setProjectName(rs.getString("project_name"));
        row.setContestId(rs.getLong("contest_id"));
        row.setContestName(rs.getString("contest_name"));
        row.setTrackId((Long) rs.getObject("track_id"));
        row.setTrackName(rs.getString("track_name"));
        row.setLeaderMemberId((Long) rs.getObject("leader_member_id"));
        row.setLeaderName(rs.getString("leader_name"));
        row.setMemberCount(rs.getInt("member_count"));
        row.setAdvisorCount(rs.getInt("advisor_count"));
        row.setStatus(rs.getString("status"));
        row.setMaterialStatus(rs.getString("material_status"));
        row.setMaterialCompleteRate(rs.getBigDecimal("material_complete_rate"));
        row.setOrgId(rs.getLong("org_id"));
        row.setSemesterId(rs.getLong("semester_id"));
        row.setCreatedAt(rs.getObject("created_at", java.time.LocalDateTime.class));
        return row;
    };

    private final JdbcTemplate jdbcTemplate;

    public TeamQuerySupport(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 按 SQL 查赛队列表（列表接口动态拼条件后调） */
    public List<TeamListRow> queryTeams(String sql, Object... params) {
        return jdbcTemplate.query(sql, ROW_MAPPER, params);
    }

    /** 加载单支赛队；不存在抛 1004 */
    public TeamListRow loadTeam(Long teamId) {
        try {
            return jdbcTemplate.queryForObject(
                    TEAM_SELECT + " WHERE t.id = ? AND t.deleted = 0", ROW_MAPPER, teamId);
        } catch (EmptyResultDataAccessException e) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "赛队不存在");
        }
    }

    /** 查活跃成员（UNCONFIRMED + CONFIRMED），关联 member 取展示字段 */
    public List<MemberRow> queryMembers(Long teamId) {
        return jdbcTemplate.query(
                "SELECT tm.member_id, m.name, tm.is_leader, tm.status, tm.member_org_id, "
                        + "m.student_no, m.college, m.class_name, m.phone, m.id_card_last4 "
                        + "FROM team_member tm "
                        + "LEFT JOIN member m ON tm.member_id = m.id AND m.deleted = 0 "
                        + "WHERE tm.team_id = ? AND tm.deleted = 0 "
                        + "AND tm.status IN ('UNCONFIRMED','CONFIRMED') "
                        + "ORDER BY tm.is_leader DESC, tm.joined_at ASC",
                (rs, n) -> {
                    MemberRow r = new MemberRow();
                    r.setMemberId(rs.getLong("member_id"));
                    r.setName(rs.getString("name"));
                    r.setIsLeader(rs.getInt("is_leader"));
                    r.setStatus(rs.getString("status"));
                    r.setMemberOrgId((Long) rs.getObject("member_org_id"));
                    r.setStudentNo(rs.getString("student_no"));
                    r.setCollege(rs.getString("college"));
                    r.setClassName(rs.getString("class_name"));
                    r.setPhone(rs.getString("phone"));
                    r.setIdCardLast4(rs.getString("id_card_last4"));
                    return r;
                }, teamId);
    }

    /** 查在任指导教师（ACTIVE），关联 member 取展示字段 */
    public List<AdvisorRow> queryAdvisors(Long teamId) {
        return jdbcTemplate.query(
                "SELECT ta.member_id, m.name AS advisor_name, ta.title, m.college "
                        + "FROM team_advisor ta "
                        + "LEFT JOIN member m ON ta.member_id = m.id AND m.deleted = 0 "
                        + "WHERE ta.team_id = ? AND ta.deleted = 0 AND ta.status = 'ACTIVE' "
                        + "ORDER BY ta.created_at ASC",
                (rs, n) -> {
                    AdvisorRow r = new AdvisorRow();
                    r.setMemberId(rs.getLong("member_id"));
                    r.setAdvisorName(rs.getString("advisor_name"));
                    r.setTitle(rs.getString("title"));
                    r.setCollege(rs.getString("college"));
                    return r;
                }, teamId);
    }

    /** 返回 is_leader 字段；null 表示不是本队活跃成员 */
    public Integer queryMemberFlag(Long teamId, Long memberId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT is_leader FROM team_member WHERE team_id = ? AND member_id = ? AND deleted = 0 "
                            + "AND status IN ('UNCONFIRMED','CONFIRMED')",
                    Integer.class, teamId, memberId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public boolean isAdvisorOfTeam(Long teamId, Long memberId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_advisor WHERE team_id = ? AND member_id = ? "
                        + "AND deleted = 0 AND status = 'ACTIVE'",
                Integer.class, teamId, memberId);
        return count != null && count > 0;
    }

    /** 当前用户的人员档案 ID（没绑定返回 null） */
    public Long currentMemberId(CurrentUser user) {
        if (user == null || user.getUserId() == null) {
            return null;
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT member_id FROM sys_user WHERE id = ? AND deleted = 0",
                Long.class, user.getUserId());
        return (ids.isEmpty() || ids.get(0) == null) ? null : ids.get(0);
    }

    /** 管理员组织子树内的组织 ID 列表（sys_org.path 前缀匹配） */
    public List<Long> querySubtreeOrgIds(Long orgId) {
        if (orgId == null) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "部门管理员未配置所属组织");
        }
        try {
            String path = jdbcTemplate.queryForObject(
                    "SELECT path FROM sys_org WHERE id = ? AND deleted = 0", String.class, orgId);
            if (path == null) {
                throw new BizException(CommonErrorCode.FORBIDDEN, "组织不存在或已删除");
            }
            return jdbcTemplate.queryForList(
                    "SELECT id FROM sys_org WHERE path LIKE CONCAT(?, '%') AND deleted = 0",
                    Long.class, path);
        } catch (BizException e) {
            throw e;
        } catch (DataAccessException e) {
            log.warn("子树组织查询失败，按无权限处理：{}", e.getMessage());
            throw new BizException(CommonErrorCode.FORBIDDEN);
        }
    }

    /** 判断组织是否在管理员子树内（path 前缀匹配） */
    public boolean isOrgInSubtree(Long adminOrgId, Long orgId) {
        if (adminOrgId == null || orgId == null) {
            return false;
        }
        try {
            String adminPath = jdbcTemplate.queryForObject(
                    "SELECT path FROM sys_org WHERE id = ? AND deleted = 0", String.class, adminOrgId);
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_org WHERE id = ? AND deleted = 0 AND path LIKE CONCAT(?, '%')",
                    Integer.class, orgId, adminPath);
            return adminPath != null && count != null && count > 0;
        } catch (DataAccessException e) {
            log.warn("子树校验查询失败，按不在子树处理：{}", e.getMessage());
            return false;
        }
    }
}
