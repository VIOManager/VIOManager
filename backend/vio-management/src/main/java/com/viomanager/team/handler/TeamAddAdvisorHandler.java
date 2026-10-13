package com.viomanager.team.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.approval.handler.ApprovalHandler;
import com.viomanager.common.enums.TeamErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.team.entity.TeamAdvisor;
import com.viomanager.team.mapper.TeamAdvisorMapper;
import jakarta.annotation.Resource;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 加指导教师审批回调（TEAM_ADD_ADVISOR）：批准后插入 team_advisor（ACTIVE）。
 * onApproved 内复查人员仍为 TEACHER 类型，封堵提交到审批期间状态变化的时序漏洞。
 * 回调在审批处理同一事务内，数据库操作失败会连同审批结果一起回滚。
 */
@Slf4j
@Component
public class TeamAddAdvisorHandler implements ApprovalHandler {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private TeamAdvisorMapper teamAdvisorMapper;
    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public String bizType() {
        return "TEAM_ADD_ADVISOR";
    }

    @Override
    public Long onApproved(Long approvalId, Long bizId, String payloadJson) {
        Map<String, Object> p = parsePayload(payloadJson);
        Long teamId = toLong(p.get("teamId"));
        Long memberId = toLong(p.get("memberId"));
        String memberName = (String) p.get("memberName");
        // 1. 复查人员仍为 TEACHER 类型
        String type;
        try {
            type = jdbcTemplate.queryForObject(
                    "SELECT member_type FROM member WHERE id = ? AND deleted = 0", String.class, memberId);
        } catch (EmptyResultDataAccessException e) {
            throw new BizException(TeamErrorCode.TARGET_NOT_TEACHER);
        }
        if (!"TEACHER".equals(type)) {
            throw new BizException(TeamErrorCode.TARGET_NOT_TEACHER);
        }
        // 2. 入队为指导教师（ACTIVE）
        TeamAdvisor a = new TeamAdvisor();
        a.setTeamId(teamId);
        a.setMemberId(memberId);
        a.setAdvisorName(memberName);
        a.setStatus("ACTIVE");
        teamAdvisorMapper.insert(a);
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

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
