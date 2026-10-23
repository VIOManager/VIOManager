package com.viomanager.team.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.viomanager.approval.handler.ApprovalHandler;
import com.viomanager.common.enums.TeamErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.team.entity.TeamMember;
import com.viomanager.team.mapper.TeamMemberMapper;
import com.viomanager.team.support.ContestContext;
import com.viomanager.team.support.ContestQueryService;
import com.viomanager.team.support.TeamCommandSupport;
import com.viomanager.team.support.TeamLockSupport;
import jakarta.annotation.Resource;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 加队员审批回调（TEAM_ADD_MEMBER）：批准后插入 team_member（UNCONFIRMED）。
 * 超卖修复（方案 §12.5）——check-then-act 窗口双层闭合：
 * 外层 Redisson 锁 teamId 粒度串行化同队并发加人（拿不到锁按 4013 拒绝，宁拒不放行）；
 * 内层 team 行 FOR UPDATE 持有到事务提交——分布式锁在 onApproved 返回即释放、早于 commit，
 * 行锁是数据库层最后防线。锁内复查三道：人数上限（超卖主闸）、跨队已确认、未入本队。
 * 回调在审批处理同一事务内，任何一道不过都连同审批结果一起回滚。
 */
@Slf4j
@Component
public class TeamAddMemberHandler implements ApprovalHandler {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private TeamMemberMapper teamMemberMapper;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private TeamCommandSupport cmdSupport;
    @Resource
    private ContestQueryService contestQueryService;
    @Resource
    private TeamLockSupport lockSupport;

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
        RLock lock = lockSupport.lockForAdd(teamId);
        try {
            // team 行 FOR UPDATE：行锁随本事务持有到 commit，兜住"锁释放早于提交"的窗口
            Map<String, Object> team = lockTeamRow(teamId);
            Long contestId = toLong(team.get("contest_id"));
            Long trackId = toLong(team.get("track_id"));
            ContestContext ctx = contestQueryService.loadContext(contestId, trackId);
            // 锁内复查人数上限——超卖主闸：当前读计数（FOR UPDATE），不受事务旧快照影响
            if (cmdSupport.countActiveMembersForUpdate(teamId) >= ctx.ruleInt("memberLimit", 5)) {
                throw new BizException(TeamErrorCode.MEMBER_LIMIT_EXCEEDED);
            }
            // 锁内复查跨队：提交到批准期间目标人可能已确认进同比赛他队
            if (!ctx.ruleBool("allowRepeatJoin", false)
                    && cmdSupport.isConfirmedInContest(memberId, contestId)) {
                throw new BizException(TeamErrorCode.REPEAT_JOIN_LIMITED);
            }
            // 复查未入本队（原有防线保留；FOR UPDATE 当前读，并发同人双发给干净的 4005 而非撞唯一键）
            Integer c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM team_member WHERE team_id = ? AND member_id = ? AND deleted = 0 "
                            + "AND status IN ('UNCONFIRMED','CONFIRMED') FOR UPDATE",
                    Integer.class, teamId, memberId);
            if (c != null && c > 0) {
                throw new BizException(TeamErrorCode.TARGET_IN_TEAM_OR_PENDING);
            }
            // 入队（UNCONFIRMED，待本人确认）。重入队走 upsert：终态旧行复位，避免撞 uk_team_member
            TeamMember m = new TeamMember();
            m.setId(IdWorker.getId()); // upsertRejoin 自定义 SQL 不走 ASSIGN_ID，必须显式给
            m.setTeamId(teamId);
            m.setMemberId(memberId);
            m.setMemberName(memberName);
            m.setMemberOrgId(queryOrgId(memberId));
            m.setIsLeader(0);
            m.setStatus("UNCONFIRMED");
            teamMemberMapper.upsertRejoin(m);
        } finally {
            lockSupport.release(lock);
        }
        return null;
    }

    @Override
    public void onRejected(Long approvalId, Long bizId, String payloadJson) {
        // 驳回无需动作（正式数据从未创建）
    }

    private Map<String, Object> lockTeamRow(Long teamId) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT id, contest_id, track_id FROM team WHERE id = ? AND deleted = 0 "
                            + "AND status = 'ACTIVE' FOR UPDATE", teamId);
        } catch (EmptyResultDataAccessException e) {
            throw new BizException(TeamErrorCode.CONTEST_STATE_NOT_ALLOWED, "队伍不存在或已解散");
        }
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
