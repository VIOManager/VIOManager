package com.viomanager.team.support;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.viomanager.approval.dto.ApprovalCreateDTO;
import com.viomanager.approval.service.ApprovalClient;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.enums.TeamErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.util.ParamsJsonUtil;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.team.entity.Team;
import com.viomanager.team.entity.TeamMember;
import com.viomanager.team.mapper.TeamMemberMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 赛队写操作数据访问辅助：校验类 COUNT 查询 + 人员信息查询 + 审批单提交。
 * 所有 SQL 显式列名，禁用 SELECT *。审批提交封装收口 ApprovalCreateDTO 构造。
 */
@Slf4j
@Component
public class TeamCommandSupport {

    private final JdbcTemplate jdbcTemplate;
    private final ApprovalClient approvalClient;
    private final TeamQuerySupport querySupport;
    private final TeamMemberMapper teamMemberMapper;

    public TeamCommandSupport(JdbcTemplate jdbcTemplate, ApprovalClient approvalClient,
            TeamQuerySupport querySupport, TeamMemberMapper teamMemberMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.approvalClient = approvalClient;
        this.querySupport = querySupport;
        this.teamMemberMapper = teamMemberMapper;
    }

    /** 提交审批单（封装 DTO 构造） */
    public Long submitApproval(String bizType, Long bizId, String title,
            Map<String, Object> payload, Long applicantId, String applicantName,
            Long targetId, String targetName, Long approverOrgId,
            Long teamId, Long contestId, Long semesterId) {
        ApprovalCreateDTO dto = new ApprovalCreateDTO();
        dto.setBizType(bizType);
        dto.setBizId(bizId);
        dto.setBizTitle(title);
        dto.setPayloadJson(ParamsJsonUtil.toJson(payload));
        dto.setApplicantMemberId(applicantId);
        dto.setApplicantName(applicantName);
        dto.setTargetMemberId(targetId);
        dto.setTargetMemberName(targetName);
        dto.setApproverOrgId(approverOrgId);
        dto.setTeamId(teamId);
        dto.setContestId(contestId);
        dto.setSemesterId(semesterId);
        return approvalClient.createApproval(dto);
    }

    /** 队名同比赛唯一校验：含待审批 TEAM_CREATE（excludeTeamId 非空时跳过审批检查，用于编辑） */
    public void ensureNameUnique(Long contestId, String name, Long excludeTeamId) {
        String sql = "SELECT COUNT(*) FROM team WHERE contest_id = ? AND name = ? AND deleted = 0"
                + (excludeTeamId != null ? " AND id <> ?" : "");
        Integer tc = excludeTeamId != null
                ? jdbcTemplate.queryForObject(sql, Integer.class, contestId, name, excludeTeamId)
                : jdbcTemplate.queryForObject(sql, Integer.class, contestId, name);
        if (tc != null && tc > 0) throw new BizException(TeamErrorCode.TEAM_NAME_EXISTS);
        if (excludeTeamId != null) return;
        Integer pc = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval WHERE biz_type = 'TEAM_CREATE' AND contest_id = ? "
                        + "AND status = 'PENDING' AND deleted = 0 "
                        + "AND JSON_UNQUOTE(JSON_EXTRACT(biz_payload, '$.name')) = ?",
                Integer.class, contestId, name);
        if (pc != null && pc > 0) throw new BizException(TeamErrorCode.TEAM_NAME_EXISTS);
    }

    public boolean isConfirmedInContest(Long memberId, Long contestId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_member tm JOIN team t ON tm.team_id = t.id "
                        + "AND t.deleted = 0 AND t.status = 'ACTIVE' "
                        + "WHERE tm.member_id = ? AND tm.deleted = 0 AND tm.status = 'CONFIRMED' "
                        + "AND t.contest_id = ?",
                Integer.class, memberId, contestId);
        return c != null && c > 0;
    }

    /** 确认路径用（超卖修复 §12.5 伴生竞态）：同比赛他队已确认——排除当前队，本队 CONFIRMED 是目标状态不算违规 */
    public boolean isConfirmedInContestExcluding(Long memberId, Long contestId, Long excludeTeamId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_member tm JOIN team t ON tm.team_id = t.id "
                        + "AND t.deleted = 0 AND t.status = 'ACTIVE' "
                        + "WHERE tm.member_id = ? AND tm.deleted = 0 AND tm.status = 'CONFIRMED' "
                        + "AND t.contest_id = ? AND tm.team_id <> ?",
                Integer.class, memberId, contestId, excludeTeamId);
        return c != null && c > 0;
    }

    /** 确认/拒绝翻转 UNCONFIRMED（超卖修复 §12.5）：状态条件更新，0 行即状态已被并发变更 */
    public void flipUnconfirmed(Long teamId, Long memberId, String targetStatus) {
        UpdateWrapper<TeamMember> uw = new UpdateWrapper<>();
        uw.eq("team_id", teamId).eq("member_id", memberId).eq("status", "UNCONFIRMED").eq("deleted", 0)
                .set("status", targetStatus)
                .set("joined_at", "CONFIRMED".equals(targetStatus) ? LocalDateTime.now() : null);
        if (teamMemberMapper.update(null, uw) == 0) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "当前状态不可变更");
        }
    }

    /** member 行 FOR UPDATE：跨队并发确认的数据库层防线，行锁随事务持有到提交 */
    public void lockMemberRow(Long memberId) {
        try {
            jdbcTemplate.queryForObject("SELECT id FROM member WHERE id = ? AND deleted = 0 FOR UPDATE",
                    Long.class, memberId);
        } catch (EmptyResultDataAccessException e) {
            // 档案已删则无从行锁，交给状态条件更新自然失败
        }
    }

    /** 构建加人/移除/加指导教师共用的 payload（teamId/memberId/memberName/contestId/semesterId） */
    public Map<String, Object> payloadOf(Team team, Long teamId, Long memberId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("teamId", teamId);
        payload.put("memberId", memberId);
        payload.put("memberName", memberName(memberId));
        payload.put("contestId", team.getContestId());
        payload.put("semesterId", team.getSemesterId());
        return payload;
    }

    public boolean isLeaderInContest(Long leaderMemberId, Long contestId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team WHERE leader_member_id = ? AND contest_id = ? "
                        + "AND deleted = 0 AND status = 'ACTIVE'",
                Integer.class, leaderMemberId, contestId);
        return c != null && c > 0;
    }

    public boolean isInTeam(Long teamId, Long memberId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_member WHERE team_id = ? AND member_id = ? AND deleted = 0 "
                        + "AND status IN ('UNCONFIRMED','CONFIRMED')",
                Integer.class, teamId, memberId);
        return c != null && c > 0;
    }

    public int countActiveMembers(Long teamId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_member WHERE team_id = ? AND deleted = 0 "
                        + "AND status IN ('UNCONFIRMED','CONFIRMED')",
                Integer.class, teamId);
        return c == null ? 0 : c;
    }

    /**
     * 超卖主闸的当前读（超卖修复 §12.5）：FOR UPDATE 计数读到最新已提交状态，
     * 并对并发未提交的插入阻塞等待——不受本事务先前一致性读建立的 REPEATABLE READ 旧快照影响
     * （审批 handle() 先 SELECT approval 已把快照建旧，普通 COUNT 会看不见并行事务刚插的人）。
     */
    public int countActiveMembersForUpdate(Long teamId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_member WHERE team_id = ? AND deleted = 0 "
                        + "AND status IN ('UNCONFIRMED','CONFIRMED') FOR UPDATE",
                Integer.class, teamId);
        return c == null ? 0 : c;
    }

    /** 审批发起人归位：管理员代发记队长，学生本人发记本人 */
    public Long approvalApplicantId(CurrentUser user, Team team) {
        if ("SUPER_ADMIN".equals(user.getRole()) || "DEPT_ADMIN".equals(user.getRole())) {
            return team.getLeaderMemberId();
        }
        Long mid = querySupport.currentMemberId(user);
        if (mid == null) throw new BizException(CommonErrorCode.FORBIDDEN, "当前账号未绑定人员档案");
        return mid;
    }

    public int countActiveAdvisors(Long teamId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_advisor WHERE team_id = ? AND deleted = 0 AND status = 'ACTIVE'",
                Integer.class, teamId);
        return c == null ? 0 : c;
    }

    public int countPendingApproval(String bizType, Long teamId, Long targetMemberId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval WHERE biz_type = ? AND team_id = ? "
                        + "AND target_member_id = ? AND status = 'PENDING' AND deleted = 0",
                Integer.class, bizType, teamId, targetMemberId);
        return c == null ? 0 : c;
    }

    public int countPendingTeamApprovals(Long teamId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval WHERE team_id = ? AND status = 'PENDING' AND deleted = 0",
                Integer.class, teamId);
        return c == null ? 0 : c;
    }

    public int countMaterials(Long teamId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM material_submission WHERE team_id = ? AND deleted = 0",
                Integer.class, teamId);
        return c == null ? 0 : c;
    }

    public int countAchievements(Long teamId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM achievement WHERE team_id = ? AND deleted = 0",
                Integer.class, teamId);
        return c == null ? 0 : c;
    }

    public String memberName(Long memberId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT name FROM member WHERE id = ? AND deleted = 0", String.class, memberId);
        } catch (EmptyResultDataAccessException e) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "人员档案不存在：" + memberId);
        }
    }

    public String memberType(Long memberId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT member_type FROM member WHERE id = ? AND deleted = 0", String.class, memberId);
        } catch (EmptyResultDataAccessException e) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "人员档案不存在：" + memberId);
        }
    }

    /** 写操作权限判定：超管放行，部门管理员限本组织子树，学生仅队长本人 */
    public void ensureLeaderOrAdmin(Team team, CurrentUser user) {
        String role = user.getRole();
        if ("SUPER_ADMIN".equals(role)) return;
        if ("DEPT_ADMIN".equals(role)) {
            if (querySupport.isOrgInSubtree(user.getOrgId(), team.getOrgId())) return;
            throw new BizException(CommonErrorCode.FORBIDDEN);
        }
        Long mid = querySupport.currentMemberId(user);
        if (mid == null || !mid.equals(team.getLeaderMemberId())) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "仅队长可操作");
        }
    }
}
