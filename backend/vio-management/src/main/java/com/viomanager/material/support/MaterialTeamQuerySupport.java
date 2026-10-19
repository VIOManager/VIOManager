package com.viomanager.material.support;

import com.viomanager.team.service.TeamMaterialStatusService;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 材料模块的 team / team_member 表跨线访问支撑（查询侧为过渡方案）。
 * ⚠️ 规矩：本模块打向 team / team_member 两表的全部 SQL 集中在本类，各 Service Impl 不得散写；
 * 查询方法按逐条 TODO(联调) 注释在联调时替换成 B 线服务调用。
 * 写侧（队伍材料两列）按 B/C 清单 §6 契约走 TeamMaterialStatusService 口子，禁止直写 team 表。
 * SQL 用 JdbcTemplate 位置参数（对齐 B 线 TeamQuerySupport 写法），不用命名参数。
 */
@Component
public class MaterialTeamQuerySupport {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private TeamMaterialStatusService teamMaterialStatusService;

    // TODO(联调) B 线提供 ITeamService 后，改成调它的 getById(teamId)
    public TeamRow loadTeam(Long teamId) {
        String sql = "SELECT id, name, contest_id, semester_id, org_id, leader_member_id, status, "
                + "material_status, material_complete_rate FROM team WHERE id = ? AND deleted = 0";
        return jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return null;
            }
            return new TeamRow(rs.getObject("id", Long.class), rs.getString("name"),
                    rs.getObject("contest_id", Long.class), rs.getObject("semester_id", Long.class),
                    rs.getObject("org_id", Long.class), rs.getObject("leader_member_id", Long.class),
                    rs.getString("status"), rs.getString("material_status"),
                    rs.getBigDecimal("material_complete_rate"));
        }, teamId);
    }

    // TODO(联调) B 线提供队伍查询服务后，改成调它的"按成员查在册队伍"
    public List<MyTeamRow> loadMyTeams(Long memberId) {
        String sql = "SELECT id, name, contest_id, material_complete_rate FROM team WHERE id IN "
                + "(SELECT team_id FROM team_member WHERE member_id = ? "
                + "AND status IN ('CONFIRMED', 'UNCONFIRMED') AND deleted = 0) "
                + "AND status = 'ACTIVE' AND deleted = 0";
        return jdbcTemplate.query(sql, rs -> {
            List<MyTeamRow> list = new ArrayList<>();
            while (rs.next()) {
                list.add(new MyTeamRow(rs.getObject("id", Long.class), rs.getString("name"),
                        rs.getObject("contest_id", Long.class),
                        rs.getBigDecimal("material_complete_rate")));
            }
            return list;
        }, memberId);
    }

    /** 是否该队在册队长（team_member 有 is_leader=1 且状态在册的记录）；CONFIRMED / UNCONFIRMED 都算在队 */
    // TODO(联调) B 线提供队伍查询服务后，改成调它的队长判定
    public boolean isTeamLeader(Long teamId, Long memberId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_member WHERE team_id = ? AND member_id = ? AND is_leader = 1 "
                        + "AND status IN ('CONFIRMED', 'UNCONFIRMED') AND deleted = 0",
                Long.class, teamId, memberId);
        return count != null && count > 0;
    }

    /** 我的在册队伍行（含队长标识与队伍状态；teamStatus 供调用方自行筛 ACTIVE） */
    // TODO(联调) B 线提供队伍查询服务后，改成调它的"按成员查在册队伍（含队长标识）"
    public List<TeamMembership> loadMyTeamMemberships(Long memberId) {
        String sql = "SELECT t.id, t.name, t.status, tm.is_leader FROM team t "
                + "INNER JOIN team_member tm ON tm.team_id = t.id "
                + "WHERE tm.member_id = ? AND tm.status IN ('CONFIRMED', 'UNCONFIRMED') "
                + "AND tm.deleted = 0 AND t.deleted = 0";
        return jdbcTemplate.query(sql, rs -> {
            List<TeamMembership> list = new ArrayList<>();
            while (rs.next()) {
                list.add(new TeamMembership(rs.getObject("id", Long.class), rs.getString("name"),
                        rs.getString("status"), rs.getInt("is_leader") == 1));
            }
            return list;
        }, memberId);
    }

    /** 批量查队名（列表渲染免 N+1）：teamId → name；空集直接返回空 Map，已删的队伍查不出来 */
    // TODO(联调) B 线提供 ITeamService 后，改成批量调它的 getById
    public Map<Long, String> loadTeamNames(Collection<Long> teamIds) {
        Map<Long, String> empty = new HashMap<>();
        if (teamIds == null || teamIds.isEmpty()) {
            return empty;
        }
        String sql = "SELECT id, name FROM team WHERE id IN (" + placeholders(teamIds.size())
                + ") AND deleted = 0";
        return jdbcTemplate.query(sql, rs -> {
            Map<Long, String> map = new HashMap<>();
            while (rs.next()) {
                map.put(rs.getObject("id", Long.class), rs.getString("name"));
            }
            return map;
        }, teamIds.toArray());
    }

    // TODO(联调) B 线提供成员查询服务后替换
    public boolean isTeamMember(Long teamId, Long memberId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_member WHERE team_id = ? AND member_id = ? "
                        + "AND status IN ('CONFIRMED', 'UNCONFIRMED') AND deleted = 0",
                Long.class, teamId, memberId);
        return count != null && count > 0;
    }

    // TODO(联调) B 线提供 ITeamService 后，队长名随 getById 一并带出（现为按 leader_member_id 查 team_member 冗余名）
    public String loadLeaderName(Long teamId, Long leaderMemberId) {
        if (leaderMemberId == null) {
            return null;
        }
        List<String> names = jdbcTemplate.queryForList(
                "SELECT member_name FROM team_member WHERE team_id = ? AND member_id = ? AND deleted = 0",
                String.class, teamId, leaderMemberId);
        return names.isEmpty() ? null : names.get(0);
    }

    /**
     * 写队伍材料两列：B/C 清单 §6 契约——走 B 线 TeamMaterialStatusService 口子，禁止直写 team 表。
     * 本类入参 completeRate 是 int 百分比，口子契约收 BigDecimal（0-100），此处换算。
     */
    public void updateTeamMaterial(Long teamId, String materialStatus, int completeRate) {
        teamMaterialStatusService.updateMaterialStatus(teamId, materialStatus,
                BigDecimal.valueOf(completeRate));
    }

    /**
     * 按比赛查全部未删队伍 ID（只读）：材料要求按比赛共享（material_requirement 无 team_id 列），
     * 要求集变动会影响该比赛下所有队伍的齐全率分母，重算必须整场收敛。
     */
    // TODO(联调) B 线提供按比赛查队伍的查询口后改调它，当前保留自研只读 SQL
    public List<Long> loadTeamIdsByContest(Long contestId) {
        if (contestId == null) {
            return new ArrayList<>();
        }
        return jdbcTemplate.queryForList(
                "SELECT id FROM team WHERE contest_id = ? AND deleted = 0",
                Long.class, contestId);
    }

    /** IN 占位符：n 个问号逗号相连（位置参数风格的批量查询辅助） */
    private String placeholders(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("?");
        }
        return sb.toString();
    }

    /** team 行只读快照（B 线实体未建前的过渡载体） */
    @Data
    @AllArgsConstructor
    public static class TeamRow {
        private Long id;
        private String name;
        private Long contestId;
        private Long semesterId;
        private Long orgId;
        private Long leaderMemberId;
        private String status;
        private String materialStatus;
        private BigDecimal completeRate;
    }

    /** 我在册且 ACTIVE 的队伍行（B 线实体未建前的过渡载体） */
    @Data
    @AllArgsConstructor
    public static class MyTeamRow {
        private Long id;
        private String name;
        private Long contestId;
        private BigDecimal completeRate;
    }

    /** 我的在册队伍行（含队长标识与队伍状态；teamStatus 供调用方自行筛 ACTIVE） */
    @Data
    @AllArgsConstructor
    public static class TeamMembership {
        private Long teamId;
        private String teamName;
        private String teamStatus;
        private boolean isLeader;
    }
}
