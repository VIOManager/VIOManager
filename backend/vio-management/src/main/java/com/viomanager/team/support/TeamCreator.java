package com.viomanager.team.support;

import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.TeamErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.team.dto.TeamCreateDTO;
import com.viomanager.team.entity.Team;
import com.viomanager.team.entity.TeamMember;
import com.viomanager.team.mapper.TeamMapper;
import com.viomanager.team.mapper.TeamMemberMapper;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 队长直建赛队（2026-10-24 lzh 拍板：建队不走管理员审批，提交即生效）。
 * 原 TEAM_CREATE 审批回调 TeamCreateHandler 的"锁比赛 + 复查 + 建队"整体平移至此
 * （PR#51 加固形态：锁内当前读查重 + 四道规则复查），校验口径与原提交侧完全同源：
 * 4001 报名期 / 4002 队名同比赛唯一 / 4003 预填上限 / 4004 重复参赛。
 * 申请人即队长（LEADER / CONFIRMED）；预填成员 UNCONFIRMED 入队，由本人两段式确认。
 */
@Component
public class TeamCreator {

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

    /**
     * 直建入口：调用方保证 leaderId 已绑定人员档案且外层已开事务（行锁要撑到提交）。
     * applicantOrgId 为null时回落比赛所属组织。返回新队 ID。
     */
    public Long create(TeamCreateDTO dto, Long leaderId, String leaderName, Long applicantOrgId) {
        // 1. 锁比赛行（K4）：串行化同比赛并发建队，查重与规则复查都在锁内
        cmdSupport.lockContest(dto.getContestId());
        // 2. 复查比赛仍报名中（loadContext 同时兜住比赛/赛道不存在）
        ContestContext ctx = contestQueryService.loadContext(dto.getContestId(), dto.getTrackId());
        if (!"REGISTERING".equals(ctx.getStatus())) {
            throw new BizException(TeamErrorCode.CONTEST_STATE_NOT_ALLOWED);
        }
        // 3. 队名同比赛唯一（锁内当前读）
        cmdSupport.ensureNameUnique(dto.getContestId(), dto.getName(), null);
        // 4. 预填名单解析（空值与队长本人不算，与原提交侧口径一致）
        List<Long> prefill = new ArrayList<>();
        if (dto.getPrefillMemberIds() != null) {
            for (Long id : dto.getPrefillMemberIds()) {
                if (id != null && !id.equals(leaderId)) {
                    prefill.add(id);
                }
            }
        }
        // 5. 四道规则复查
        if (prefill.size() > ctx.ruleInt("prefillLimit", 2)) {
            throw new BizException(TeamErrorCode.MEMBER_LIMIT_EXCEEDED);
        }
        if (!ctx.ruleBool("allowRepeatJoin", false)) {
            for (Long mid : prefill) {
                if (cmdSupport.isConfirmedInContest(mid, dto.getContestId())) {
                    throw new BizException(TeamErrorCode.REPEAT_JOIN_LIMITED);
                }
            }
        }
        if (!ctx.ruleBool("crossTeamLeader", false) && cmdSupport.isLeaderInContest(leaderId, dto.getContestId())) {
            throw new BizException(TeamErrorCode.REPEAT_JOIN_LIMITED);
        }
        // 6. 队伍所属组织：申请人组织优先，缺省回落比赛组织（组织为空后续数据范围/审批路由都会失焦）
        Long orgId = applicantOrgId != null ? applicantOrgId : ctx.getOrgId();
        if (orgId == null) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "无法确定队伍所属组织");
        }
        // 7. 建队 + 队长入队（立即 CONFIRMED）+ 预填成员入队（UNCONFIRMED 待本人确认）
        Team team = new Team();
        team.setName(dto.getName());
        team.setProjectName(dto.getProjectName());
        team.setContestId(dto.getContestId());
        team.setTrackId(dto.getTrackId());
        team.setSemesterId(ctx.getSemesterId());
        team.setOrgId(orgId);
        team.setLeaderMemberId(leaderId);
        team.setStatus("ACTIVE");
        team.setMaterialStatus("NOT_STARTED");
        teamMapper.insert(team);
        insertMember(team.getId(), leaderId, leaderName, orgId, 1, "CONFIRMED");
        for (Long mid : prefill) {
            insertMember(team.getId(), mid, cmdSupport.memberName(mid), memberOrgId(mid), 0, "UNCONFIRMED");
        }
        return team.getId();
    }

    private void insertMember(Long teamId, Long memberId, String name, Long orgId, int isLeader, String status) {
        TeamMember m = new TeamMember();
        m.setTeamId(teamId);
        m.setMemberId(memberId);
        m.setMemberName(name);
        m.setMemberOrgId(orgId);
        m.setIsLeader(isLeader);
        m.setStatus(status);
        if ("CONFIRMED".equals(status)) {
            m.setJoinedAt(LocalDateTime.now());
        }
        teamMemberMapper.insert(m);
    }

    private Long memberOrgId(Long memberId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT org_id FROM member WHERE id = ? AND deleted = 0", Long.class, memberId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
