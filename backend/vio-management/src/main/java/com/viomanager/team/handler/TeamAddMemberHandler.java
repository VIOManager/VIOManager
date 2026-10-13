package com.viomanager.team.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.approval.handler.ApprovalHandler;
import com.viomanager.common.enums.TeamErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.team.entity.TeamMember;
import com.viomanager.team.mapper.TeamMemberMapper;
import jakarta.annotation.Resource;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 加队员审批回调（TEAM_ADD_MEMBER）：批准后插入 team_member（UNCONFIRMED）。
 * onApproved 内复查目标未入他队，封堵提交到审批期间状态变化的时序漏洞。
 * 回调在审批处理同一事务内，数据库操作失败会连同审批结果一起回滚。
 */
@Slf4j
@Component
public class TeamAddMemberHandler implements ApprovalHandler {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private TeamMemberMapper teamMemberMapper;
    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public String bizType() {
        return "TEAM_ADD_MEMBER";
    }

    @Override
    public Long onApproved(Long approvalId, Long bizId, String payloadJson) {
        Map<String, Object> p = parsePayload(payloadJson);
        Long teamId = toLong(p.get("teamId"));
        Long memberId = toLong(p.get("memberId"));
        String memberName = (String) p.get("memberName");
        // 1. 复查目标未入队（封堵审批期间被加进他队的时序漏洞）
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_member WHERE team_id = ? AND member_id = ? AND deleted = 0 "
                        + "AND status IN ('UNCONFIRMED','CONFIRMED')",
                Integer.class, teamId, memberId);
        if (c != null && c > 0) {
            throw new BizException(TeamErrorCode.TARGET_IN_TEAM_OR_PENDING);
        }
        // 2. 入队（UNCONFIRMED，待本人确认）
        TeamMember m = new TeamMember();
        m.setTeamId(teamId);
        m.setMemberId(memberId);
        m.setMemberName(memberName);
        m.setMemberOrgId(queryOrgId(memberId));
        m.setIsLeader(0);
        m.setStatus("UNCONFIRMED");
        teamMemberMapper.insert(m);
        return null;
    }

    @Override
    public void onRejected(Long approvalId, Long bizId, String payloadJson) {
        // 驳回无需动作（正式数据从未创建）
    }

    private Map<String, Object> parsePayload(String json) {
        try {
            return JSON.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new BizException(TeamErrorCode.CONTEST_STATE_NOT_ALLOWED, "审批快照解析失败");
        }
    }

    private Long queryOrgId(Long memberId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT org_id FROM member WHERE id = ? AND deleted = 0", Long.class, memberId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
