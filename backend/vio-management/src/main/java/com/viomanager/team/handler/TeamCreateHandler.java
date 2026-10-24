package com.viomanager.team.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.approval.handler.ApprovalHandler;
import com.viomanager.common.enums.TeamErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.team.entity.Team;
import com.viomanager.team.entity.TeamMember;
import com.viomanager.team.mapper.TeamMapper;
import com.viomanager.team.mapper.TeamMemberMapper;
import com.viomanager.team.support.ContestContext;
import com.viomanager.team.support.ContestQueryService;
import com.viomanager.team.support.TeamCommandSupport;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 建队审批回调（TEAM_CREATE）：批准后建队 + 队长 + 预填成员。
 * onApproved 内复查比赛状态、队名唯一与参赛规则，封堵提交到审批期间状态变化的时序漏洞
 * （2026-10-23 修复：原实现漏了 prefillLimit / allowRepeatJoin / crossTeamLeader 三道规则复查，
 *  且未锁比赛行——并发同名/超限审批可双双通过）。
 * 回调在审批处理的同一事务内，数据库操作失败会连同审批结果一起回滚。
 * ⚠️ 测试用 RecordingTeamHandler 顶位：通过 vio.team.create-handler.enabled=false 禁用本 Handler。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "vio.team.create-handler.enabled", havingValue = "true", matchIfMissing = true)
public class TeamCreateHandler implements ApprovalHandler {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private TeamMapper teamMapper;
    @Resource
    private TeamMemberMapper teamMemberMapper;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private ContestQueryService contestQueryService;
    @Resource
    private TeamCommandSupport cmdSupport;

    @Override
    public String bizType() {
        return "TEAM_CREATE";
    }

    @Override
    public Long onApproved(Long approvalId, Long bizId, String payloadJson) {
        Map<String, Object> p = parsePayload(payloadJson);
        Long contestId = toLong(p.get("contestId"));
        Long trackId = toLong(p.get("trackId"));
        Long leaderId = toLong(p.get("leaderMemberId"));
        String leaderName = (String) p.get("leaderName");
        Long semesterId = toLong(p.get("semesterId"));
        Long orgId = toLong(p.get("orgId"));
        String name = (String) p.get("name");
        String projectName = (String) p.get("projectName");
        // 预填名单解析一次（空值与队长本人不算，与提交侧 createTeam 口径一致）
        List<Long> prefill = new ArrayList<>();
        Object rawPrefill = p.get("prefillMemberIds");
        if (rawPrefill instanceof List<?> list) {
            for (Object item : list) {
                Long mid = toLong(item);
                if (mid != null && !mid.equals(leaderId)) {
                    prefill.add(mid);
                }
            }
        }
        // 1. 锁比赛行（与提交侧同粒度，K4）：串行化同比赛并发建队审批，查重/规则复查都在锁内
        cmdSupport.lockContest(contestId);
        // 2. 复查比赛仍 REGISTERING（loadContext 同时兜住比赛/赛道不存在）
        ContestContext ctx = contestQueryService.loadContext(contestId, trackId);
        if (!"REGISTERING".equals(ctx.getStatus())) {
            throw new BizException(TeamErrorCode.CONTEST_STATE_NOT_ALLOWED);
        }
        // 3. 复查队名仍唯一（锁内当前读）
        cmdSupport.ensureNameUnique(contestId, name, null);
        // 4. 规则复查——与提交侧 createTeam 同源（2026-10-23 补：原实现漏检）
        if (prefill.size() > ctx.ruleInt("prefillLimit", 2)) {
            throw new BizException(TeamErrorCode.MEMBER_LIMIT_EXCEEDED);
        }
        if (!ctx.ruleBool("allowRepeatJoin", false)) {
            for (Long mid : prefill) {
                if (cmdSupport.isConfirmedInContest(mid, contestId)) {
                    throw new BizException(TeamErrorCode.REPEAT_JOIN_LIMITED);
                }
            }
        }
        if (!ctx.ruleBool("crossTeamLeader", false) && cmdSupport.isLeaderInContest(leaderId, contestId)) {
            throw new BizException(TeamErrorCode.REPEAT_JOIN_LIMITED);
        }
        // 5. 建队
        Team team = new Team();
        team.setName(name);
        team.setProjectName(projectName);
        team.setContestId(contestId);
        team.setTrackId(trackId);
        team.setSemesterId(semesterId);
        team.setOrgId(orgId);
        team.setLeaderMemberId(leaderId);
        team.setStatus("ACTIVE");
        team.setMaterialStatus("NOT_STARTED");
        teamMapper.insert(team);
        // 6. 队长入队（CONFIRMED）
        TeamMember leader = new TeamMember();
        leader.setTeamId(team.getId());
        leader.setMemberId(leaderId);
        leader.setMemberName(leaderName);
        leader.setMemberOrgId(orgId);
        leader.setIsLeader(1);
        leader.setStatus("CONFIRMED");
        leader.setJoinedAt(LocalDateTime.now());
        teamMemberMapper.insert(leader);
        // 7. 预填成员入队（UNCONFIRMED）
        for (Long mid : prefill) {
            TeamMember m = new TeamMember();
            m.setTeamId(team.getId());
            m.setMemberId(mid);
            m.setMemberName(queryName(mid));
            m.setMemberOrgId(queryOrgId(mid));
            m.setIsLeader(0);
            m.setStatus("UNCONFIRMED");
            teamMemberMapper.insert(m);
        }
        return team.getId();
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

    private String queryName(Long memberId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT name FROM member WHERE id = ? AND deleted = 0", String.class, memberId);
        } catch (EmptyResultDataAccessException e) {
            throw new BizException(com.viomanager.common.enums.CommonErrorCode.NOT_FOUND, "人员档案不存在");
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
