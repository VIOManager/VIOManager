package com.viomanager.team.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.TeamErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.team.dto.TeamAdvisorAddDTO;
import com.viomanager.team.dto.TeamCreateDTO;
import com.viomanager.team.dto.TeamDissolveDTO;
import com.viomanager.team.dto.TeamMemberAddDTO;
import com.viomanager.team.dto.TeamMemberConfirmDTO;
import com.viomanager.team.dto.TeamUpdateDTO;
import com.viomanager.team.entity.Team;
import com.viomanager.team.entity.TeamAdvisor;
import com.viomanager.team.entity.TeamMember;
import com.viomanager.team.mapper.TeamAdvisorMapper;
import com.viomanager.team.mapper.TeamMapper;
import com.viomanager.team.mapper.TeamMemberMapper;
import com.viomanager.team.service.ITeamCommandService;
import com.viomanager.team.support.ContestContext;
import com.viomanager.team.support.ContestQueryService;
import com.viomanager.team.support.TeamCommandSupport;
import com.viomanager.team.support.TeamQuerySupport;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 赛队写服务实现。审批类（TE03/04/07/09）校验通过后生成审批单不直接生效；
 * 直接类（TE06/08/10/12/13）即时变更状态。校验与 COUNT 查询委托 {@link TeamCommandSupport}。
 */
@Slf4j
@Service
public class TeamCommandServiceImpl implements ITeamCommandService {

    @Resource
    private TeamMapper teamMapper;
    @Resource
    private TeamMemberMapper teamMemberMapper;
    @Resource
    private TeamAdvisorMapper teamAdvisorMapper;
    @Resource
    private TeamQuerySupport support;
    @Resource
    private TeamCommandSupport cmdSupport;
    @Resource
    private ContestQueryService contestQueryService;

    @Override
    @Transactional
    public Long createTeam(TeamCreateDTO dto) {
        CurrentUser user = UserHolder.getUser();
        Long leaderId = support.currentMemberId(user);
        if (leaderId == null) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "当前账号未绑定人员档案");
        }
        ContestContext ctx = contestQueryService.loadContext(dto.getContestId(), dto.getTrackId());
        if (!"REGISTERING".equals(ctx.getStatus())) {
            throw new BizException(TeamErrorCode.CONTEST_STATE_NOT_ALLOWED);
        }
        cmdSupport.lockContest(dto.getContestId()); // 先锁比赛行再做查重：并发同名申请串行化（K4）
        cmdSupport.ensureNameUnique(dto.getContestId(), dto.getName(), null);
        List<Long> prefill = new ArrayList<>();
        if (dto.getPrefillMemberIds() != null) {
            //1.空值与队长本人不算预填名单
            for (Long id : dto.getPrefillMemberIds()) {
                if (id != null && !id.equals(leaderId)) {
                    prefill.add(id);
                }
            }
        }
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
        Long orgId = user.getOrgId() != null ? user.getOrgId() : ctx.getOrgId();
        if (orgId == null) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "无法确定审批责任组织");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("contestId", dto.getContestId());
        payload.put("contestName", ctx.getContestName());
        payload.put("trackId", dto.getTrackId());
        payload.put("trackName", ctx.getTrackName());
        payload.put("name", dto.getName());
        payload.put("projectName", dto.getProjectName());
        payload.put("leaderMemberId", leaderId);
        payload.put("leaderName", cmdSupport.memberName(leaderId));
        payload.put("prefillMemberIds", prefill);
        payload.put("semesterId", ctx.getSemesterId());
        payload.put("orgId", orgId);
        return cmdSupport.submitApproval("TEAM_CREATE", null, dto.getName() + "·" + ctx.getContestName(),
                payload, leaderId, cmdSupport.memberName(leaderId), null, null, orgId,
                null, dto.getContestId(), ctx.getSemesterId());
    }

    @Override
    @Transactional
    public void addMember(Long teamId, TeamMemberAddDTO dto) {
        Team team = loadActiveTeam(teamId);
        CurrentUser user = UserHolder.getUser();
        cmdSupport.ensureLeaderOrAdmin(team, user);
        Long targetId = dto.getMemberId();
        ContestContext ctx = contestQueryService.loadContext(team.getContestId(), team.getTrackId());
        if (cmdSupport.countActiveMembers(teamId) >= ctx.ruleInt("memberLimit", 5)) {
            throw new BizException(TeamErrorCode.MEMBER_LIMIT_EXCEEDED);
        }
        if (!ctx.ruleBool("allowRepeatJoin", false) && cmdSupport.isConfirmedInContest(targetId, team.getContestId())) {
            throw new BizException(TeamErrorCode.REPEAT_JOIN_LIMITED);
        }
        if (cmdSupport.isInTeam(teamId, targetId)) {
            throw new BizException(TeamErrorCode.TARGET_IN_TEAM_OR_PENDING);
        }
        if (cmdSupport.countPendingApproval("TEAM_ADD_MEMBER", teamId, targetId) > 0) {
            throw new BizException(TeamErrorCode.SAME_TARGET_PENDING);
        }
        Long applicantId = approvalApplicantId(user, team);
        Map<String, Object> payload = payloadOf(team, teamId, targetId);
        cmdSupport.submitApproval("TEAM_ADD_MEMBER", teamId, "邀请 " + cmdSupport.memberName(targetId) + " 加入",
                payload, applicantId, cmdSupport.memberName(applicantId), targetId, cmdSupport.memberName(targetId),
                team.getOrgId(), teamId, team.getContestId(), team.getSemesterId());
    }

    @Override
    @Transactional
    public void confirmMember(Long teamId, Long memberId, TeamMemberConfirmDTO dto) {
        loadActiveTeam(teamId);
        String action = dto.getAction();
        if (!"CONFIRM".equals(action) && !"REJECT".equals(action)) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "action 须为 CONFIRM 或 REJECT");
        }
        Long currId = support.currentMemberId(UserHolder.getUser());
        if (currId == null || !currId.equals(memberId)) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "仅本人可确认/拒绝");
        }
        UpdateWrapper<TeamMember> uw = new UpdateWrapper<>();
        uw.eq("team_id", teamId).eq("member_id", memberId).eq("status", "UNCONFIRMED").eq("deleted", 0)
                .set("status", action.equals("CONFIRM") ? "CONFIRMED" : "REJECTED")
                .set("joined_at", action.equals("CONFIRM") ? LocalDateTime.now() : null);
        if (teamMemberMapper.update(null, uw) == 0) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "当前状态不可变更");
        }
    }

    @Override
    @Transactional
    public void removeMember(Long teamId, Long memberId) {
        Team team = loadActiveTeam(teamId);
        CurrentUser user = UserHolder.getUser();
        cmdSupport.ensureLeaderOrAdmin(team, user);
        if (memberId.equals(team.getLeaderMemberId())) {
            throw new BizException(TeamErrorCode.LEADER_NOT_REMOVABLE);
        }
        Long applicantId = approvalApplicantId(user, team);
        Map<String, Object> payload = payloadOf(team, teamId, memberId);
        cmdSupport.submitApproval("TEAM_REMOVE_MEMBER", teamId, "移除 " + cmdSupport.memberName(memberId),
                payload, applicantId, cmdSupport.memberName(applicantId), memberId, cmdSupport.memberName(memberId),
                team.getOrgId(), teamId, team.getContestId(), team.getSemesterId());
    }

    @Override
    @Transactional
    public void exit(Long teamId) {
        Team team = loadActiveTeam(teamId);
        Long memberId = support.currentMemberId(UserHolder.getUser());
        if (memberId == null) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "当前账号未绑定人员档案");
        }
        if (memberId.equals(team.getLeaderMemberId())) {
            throw new BizException(TeamErrorCode.LEADER_NOT_REMOVABLE);
        }
        UpdateWrapper<TeamMember> uw = new UpdateWrapper<>();
        uw.eq("team_id", teamId).eq("member_id", memberId).eq("status", "CONFIRMED").eq("deleted", 0)
                .set("status", "EXITED").set("left_at", LocalDateTime.now()).set("left_reason", "主动退出");
        if (teamMemberMapper.update(null, uw) == 0) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "仅 CONFIRMED 成员可退出");
        }
        // TODO 通知中心就绪后给队长发红点
    }

    @Override
    @Transactional
    public void addAdvisor(Long teamId, TeamAdvisorAddDTO dto) {
        Team team = loadActiveTeam(teamId);
        CurrentUser user = UserHolder.getUser();
        cmdSupport.ensureLeaderOrAdmin(team, user);
        Long targetId = dto.getMemberId();
        if (!"TEACHER".equals(cmdSupport.memberType(targetId))) {
            throw new BizException(TeamErrorCode.TARGET_NOT_TEACHER);
        }
        ContestContext ctx = contestQueryService.loadContext(team.getContestId(), team.getTrackId());
        if (cmdSupport.countActiveAdvisors(teamId) >= ctx.ruleInt("maxAdvisorCount", 2)) {
            throw new BizException(TeamErrorCode.MEMBER_LIMIT_EXCEEDED);
        }
        if (cmdSupport.countPendingApproval("TEAM_ADD_ADVISOR", teamId, targetId) > 0) {
            throw new BizException(TeamErrorCode.SAME_TARGET_PENDING);
        }
        Long applicantId = approvalApplicantId(user, team);
        Map<String, Object> payload = payloadOf(team, teamId, targetId);
        cmdSupport.submitApproval("TEAM_ADD_ADVISOR", teamId, "邀请 " + cmdSupport.memberName(targetId) + " 担任指导教师",
                payload, applicantId, cmdSupport.memberName(applicantId), targetId, cmdSupport.memberName(targetId),
                team.getOrgId(), teamId, team.getContestId(), team.getSemesterId());
    }

    @Override
    @Transactional
    public void removeAdvisor(Long teamId, Long advisorMemberId) {
        Team team = loadActiveTeam(teamId);
        cmdSupport.ensureLeaderOrAdmin(team, UserHolder.getUser());
        UpdateWrapper<TeamAdvisor> uw = new UpdateWrapper<>();
        uw.eq("team_id", teamId).eq("member_id", advisorMemberId).eq("status", "ACTIVE").eq("deleted", 0)
                .set("status", "REMOVED").set("removed_at", LocalDateTime.now());
        teamAdvisorMapper.update(null, uw);
    }

    @Override
    @Transactional
    public void dissolve(Long teamId, TeamDissolveDTO dto) {
        Team team = loadActiveTeam(teamId);
        cmdSupport.ensureLeaderOrAdmin(team, UserHolder.getUser());
        if (cmdSupport.countMaterials(teamId) > 0 || cmdSupport.countAchievements(teamId) > 0
                || cmdSupport.countPendingTeamApprovals(teamId) > 0) {
            throw new BizException(TeamErrorCode.DISSOLVE_BLOCKED);
        }
        UpdateWrapper<Team> tu = new UpdateWrapper<>();
        tu.eq("id", teamId).eq("status", "ACTIVE")
                .set("status", "DISSOLVED").set("dissolved_at", LocalDateTime.now())
                .set("dissolve_reason", dto.getReason());
        teamMapper.update(null, tu);
        UpdateWrapper<TeamMember> mu = new UpdateWrapper<>();
        mu.eq("team_id", teamId).in("status", "UNCONFIRMED", "CONFIRMED").eq("deleted", 0)
                .set("status", "EXITED").set("left_at", LocalDateTime.now()).set("left_reason", "队伍解散");
        teamMemberMapper.update(null, mu);
        // TODO 通知中心就绪后给成员发红点
    }

    @Override
    @Transactional
    public void updateTeam(Long teamId, TeamUpdateDTO dto) {
        Team team = loadActiveTeam(teamId);
        cmdSupport.ensureLeaderOrAdmin(team, UserHolder.getUser());
        cmdSupport.ensureNameUnique(team.getContestId(), dto.getName(), teamId);
        UpdateWrapper<Team> uw = new UpdateWrapper<>();
        uw.eq("id", teamId).eq("deleted", 0)
                .set("name", dto.getName()).set("project_name", dto.getProjectName());
        teamMapper.update(null, uw);
    }

    // 内部辅助

    private Team loadActiveTeam(Long teamId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null || !"ACTIVE".equals(team.getStatus())) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "赛队不存在或已解散");
        }
        return team;
    }

    private Long approvalApplicantId(CurrentUser user, Team team) {
        if ("SUPER_ADMIN".equals(user.getRole()) || "DEPT_ADMIN".equals(user.getRole())) {
            return team.getLeaderMemberId();
        }
        Long mid = support.currentMemberId(user);
        if (mid == null) throw new BizException(CommonErrorCode.FORBIDDEN, "当前账号未绑定人员档案");
        return mid;
    }

    /** 构建加人/移除/加指导教师共用的 payload（teamId/memberId/memberName/contestId/semesterId） */
    private Map<String, Object> payloadOf(Team team, Long teamId, Long memberId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("teamId", teamId);
        payload.put("memberId", memberId);
        payload.put("memberName", cmdSupport.memberName(memberId));
        payload.put("contestId", team.getContestId());
        payload.put("semesterId", team.getSemesterId());
        return payload;
    }
}
