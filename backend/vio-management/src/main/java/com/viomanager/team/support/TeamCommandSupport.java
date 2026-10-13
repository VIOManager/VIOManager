package com.viomanager.team.support;

import com.viomanager.approval.dto.ApprovalCreateDTO;
import com.viomanager.approval.service.ApprovalClient;
import com.viomanager.common.enums.TeamErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.util.ParamsJsonUtil;
import com.viomanager.common.enums.CommonErrorCode;
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

    public TeamCommandSupport(JdbcTemplate jdbcTemplate, ApprovalClient approvalClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.approvalClient = approvalClient;
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
}
