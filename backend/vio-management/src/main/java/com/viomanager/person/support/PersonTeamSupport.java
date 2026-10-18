package com.viomanager.person.support;

import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 人员模块对 team_member 的只读判定与删除清理（M05 队长拒删 / M02·M10·M11 队长视角）。
 * 跨包数据归属：team_member 归 B 线赛队模块，本类只做读取与档案删除时的关系清理，
 * 走 jdbcTemplate 直查不改 B 线代码——与仓库既有跨包先例一致
 * （审批模块直查 sys_org/sys_user、B 线 handler 反查 member 表）。
 */
@Component
public class PersonTeamSupport {

    /** 在队有效状态：待确认与已确认都算"还在队里"（与 TeamQuerySupport 口径一致） */
    private static final String ACTIVE_MEMBER_STATUS = "('UNCONFIRMED','CONFIRMED')";

    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 该人员是否为某支活跃队伍（未删除且状态 ACTIVE）的队长——M05 删除前的 3003 拦截。
     */
    public boolean isLeaderOfActiveTeam(Long memberId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_member tm "
                        + "JOIN team t ON tm.team_id = t.id "
                        + "WHERE tm.member_id = ? AND tm.is_leader = 1 AND tm.deleted = 0 "
                        + "AND tm.status IN " + ACTIVE_MEMBER_STATUS + " "
                        + "AND t.deleted = 0 AND t.status = 'ACTIVE'",
                Integer.class, memberId);
        return count != null && count > 0;
    }

    /**
     * captainMemberId 是否是 targetMemberId 所在活跃队伍的队长——
     * M02 队长档视角、M10/M11 敏感信息查看的判定之一。
     * 队长本人与目标成员都必须在队（UNCONFIRMED/CONFIRMED），队伍必须未删除且 ACTIVE。
     */
    public boolean isCaptainOf(Long captainMemberId, Long targetMemberId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_member cap "
                        + "JOIN team t ON cap.team_id = t.id "
                        + "JOIN team_member tgt ON tgt.team_id = t.id "
                        + "WHERE cap.member_id = ? AND cap.is_leader = 1 AND cap.deleted = 0 "
                        + "AND cap.status IN " + ACTIVE_MEMBER_STATUS + " "
                        + "AND t.deleted = 0 AND t.status = 'ACTIVE' "
                        + "AND tgt.member_id = ? AND tgt.deleted = 0 "
                        + "AND tgt.status IN " + ACTIVE_MEMBER_STATUS,
                Integer.class, captainMemberId, targetMemberId);
        return count != null && count > 0;
    }

    /**
     * 档案删除时清掉该人员在所有队伍里的有效成员关系（M05 契约：普通队员删除时清除队伍成员关系）。
     * 物理删除而非软删：表上有 team_id+member_id+deleted 唯一键，把多行都置 deleted=1 会撞键
     * （与 MemberSkillMapper 物理删同因）。只删有效行，历史离队记录不动。
     */
    public int removeActiveRelations(Long memberId) {
        return jdbcTemplate.update(
                "DELETE FROM team_member WHERE member_id = ? AND deleted = 0 "
                        + "AND status IN " + ACTIVE_MEMBER_STATUS,
                memberId);
    }
}
