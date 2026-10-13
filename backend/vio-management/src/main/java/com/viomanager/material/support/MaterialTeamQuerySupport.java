package com.viomanager.material.support;

import com.viomanager.team.service.TeamMaterialStatusService;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 材料模块的 team / team_member 表跨线访问支撑（查询侧为过渡方案）。
 * ⚠️ 规矩：本模块打向 team / team_member 两表的全部 SQL 集中在本类，各 Service Impl 不得散写；
 * 查询方法按逐条 TODO(联调) 注释在联调时替换成 B 线服务调用。
 * 写侧（队伍材料两列）按 B/C 清单 §6 契约走 TeamMaterialStatusService 口子，禁止直写 team 表。
 * （sys_user 的 member_id 反查属骨架认证域，不归本类，见各 Impl 的 currentMemberId。）
 */
@Component
public class MaterialTeamQuerySupport {

    @Resource
    private NamedParameterJdbcTemplate namedTemplate;

    @Resource
    private TeamMaterialStatusService teamMaterialStatusService;

    // TODO(联调) B 线提供 ITeamService 后，改成调它的 getById(teamId)
    public TeamRow loadTeam(Long teamId) {
        String sql = "SELECT id, name, contest_id, semester_id, org_id, leader_member_id, status, "
                + "material_status, material_complete_rate FROM team WHERE id = :teamId AND deleted = 0";
        return namedTemplate.query(sql, Map.of("teamId", teamId), rs -> {
            if (!rs.next()) {
                return null;
            }
            return new TeamRow(rs.getObject("id", Long.class), rs.getString("name"),
                    rs.getObject("contest_id", Long.class), rs.getObject("semester_id", Long.class),
                    rs.getObject("org_id", Long.class), rs.getObject("leader_member_id", Long.class),
                    rs.getString("status"), rs.getString("material_status"),
                    rs.getBigDecimal("material_complete_rate"));
        });
    }

    // TODO(联调) B 线提供队伍查询服务后，改成调它的"按成员查在册队伍"
    public List<MyTeamRow> loadMyTeams(Long memberId) {
        String sql = "SELECT id, name, contest_id, material_complete_rate FROM team WHERE id IN "
                + "(SELECT team_id FROM team_member WHERE member_id = :memberId "
                + "AND status IN ('CONFIRMED', 'UNCONFIRMED') AND deleted = 0) AND status = 'ACTIVE' AND deleted = 0";
        return namedTemplate.query(sql, Map.of("memberId", memberId), rs -> {
            List<MyTeamRow> list = new ArrayList<>();
            while (rs.next()) {
                list.add(new MyTeamRow(rs.getObject("id", Long.class), rs.getString("name"),
                        rs.getObject("contest_id", Long.class),
                        rs.getBigDecimal("material_complete_rate")));
            }
            return list;
        });
    }

    /** 在册成员判定：team_member.status CONFIRMED / UNCONFIRMED 都算在队（MS02 第 6 步口径） */
    /** 是否该队在册队长（team_member 有 is_leader=1 且状态在册的记录） */
    // TODO(联调) B 线提供队伍查询服务后，改成调它的队长判定
    public boolean isTeamLeader(Long teamId, Long memberId) {
        String sql = "SELECT COUNT(*) FROM team_member "
                + "WHERE team_id = :teamId AND member_id = :memberId AND is_leader = 1 "
                + "AND status IN ('CONFIRMED', 'UNCONFIRMED') AND deleted = 0";
        Long count = namedTemplate.queryForObject(
                sql, Map.of("teamId", teamId, "memberId", memberId), Long.class);
        return count != null && count > 0;
    }

    /** 我的在册队伍行（含队长标识与队伍状态；teamStatus 供调用方自行筛 ACTIVE） */
    // TODO(联调) B 线提供队伍查询服务后，改成调它的"按成员查在册队伍（含队长标识）"
    public List<TeamMembership> loadMyTeamMemberships(Long memberId) {
        String sql = "SELECT t.id, t.name, t.status, tm.is_leader FROM team t "
                + "INNER JOIN team_member tm ON tm.team_id = t.id "
                + "WHERE tm.member_id = :memberId AND tm.status IN ('CONFIRMED', 'UNCONFIRMED') "
                + "AND tm.deleted = 0 AND t.deleted = 0";
        return namedTemplate.query(sql, Map.of("memberId", memberId), rs -> {
            List<TeamMembership> list = new ArrayList<>();
            while (rs.next()) {
                list.add(new TeamMembership(rs.getObject("id", Long.class), rs.getString("name"),
                        rs.getString("status"), rs.getInt("is_leader") == 1));
            }
            return list;
        });
    }

    /** 批量查队名（列表渲染免 N+1）：teamId → name；空集直接返回空 Map，已删的队伍查不出来 */
    // TODO(联调) B 线提供 ITeamService 后，改成批量调它的 getById
    public Map<Long, String> loadTeamNames(Collection<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return Map.of();
        }
        String sql = "SELECT id, name FROM team WHERE id IN (:ids) AND deleted = 0";
        return namedTemplate.query(sql, Map.of("ids", teamIds), rs -> {
            Map<Long, String> map = new HashMap<>();
            while (rs.next()) {
                map.put(rs.getObject("id", Long.class), rs.getString("name"));
            }
            return map;
        });
    }

    // TODO(联调) B 线提供成员查询服务后替换
    public boolean isTeamMember(Long teamId, Long memberId) {
        String sql = "SELECT COUNT(*) FROM team_member "
                + "WHERE team_id = :teamId AND member_id = :memberId "
                + "AND status IN ('CONFIRMED', 'UNCONFIRMED') AND deleted = 0";
        Long count = namedTemplate.queryForObject(
                sql, Map.of("teamId", teamId, "memberId", memberId), Long.class);
        return count != null && count > 0;
    }

    // TODO(联调) B 线提供 ITeamService 后，队长名随 getById 一并带出（现为按 leader_member_id 查 team_member 冗余名）
    public String loadLeaderName(Long teamId, Long leaderMemberId) {
        if (leaderMemberId == null) {
            return null;
        }
        List<String> names = namedTemplate.queryForList(
                "SELECT member_name FROM team_member WHERE team_id = :teamId AND member_id = :memberId AND deleted = 0",
                Map.of("teamId", teamId, "memberId", leaderMemberId), String.class);
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

    /** team 行只读快照（B 线实体未建前的过渡载体） */
    public record TeamRow(Long id, String name, Long contestId, Long semesterId, Long orgId,
                          Long leaderMemberId, String status, String materialStatus,
                          BigDecimal completeRate) {
    }

    /** 我在册且 ACTIVE 的队伍行（B 线实体未建前的过渡载体） */
    public record MyTeamRow(Long id, String name, Long contestId, BigDecimal completeRate) {
    }

    /** 我的在册队伍行（含队长标识与队伍状态；teamStatus 供调用方自行筛 ACTIVE） */
    public record TeamMembership(Long teamId, String teamName, String teamStatus, boolean isLeader) {
    }
}
