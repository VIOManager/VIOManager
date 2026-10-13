package com.viomanager.team.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.Result;
import com.viomanager.team.dto.TeamAdvisorAddDTO;
import com.viomanager.team.dto.TeamCreateDTO;
import com.viomanager.team.dto.TeamDissolveDTO;
import com.viomanager.team.dto.TeamMemberAddDTO;
import com.viomanager.team.dto.TeamMemberConfirmDTO;
import com.viomanager.team.dto.TeamUpdateDTO;
import com.viomanager.team.service.ITeamCommandService;
import com.viomanager.team.vo.TeamCreateVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 赛队写接口（TE03–TE13）。
 * 审批类操作（TE03/04/07/09）只生成审批单不直接生效；直接类操作（TE06/08/10/12/13）即时变更。
 * 权限在 service 层按角色 + 人员档案 ID 判定（ensureLeaderOrAdmin），无 @RequireRole。
 * 写操作均加 @Idempotent 防重复提交 + @AuditLog 留痕。
 */
@RestController
@RequestMapping("/api/teams")
public class TeamCommandController {

    @Resource
    private ITeamCommandService commandService;

    /** TE03 组队申请：校验后生成 TEAM_CREATE 审批单，返回 approvalId */
    @Idempotent(bizType = "TEAM_CREATE")
    @AuditLog(bizType = "TEAM_CREATE", label = "组队申请", summary = "'组队申请：' + #dto.name")
    @PostMapping
    public Result<TeamCreateVO> create(@Valid @RequestBody TeamCreateDTO dto) {
        Long approvalId = commandService.createTeam(dto);
        //1.单字段返回走 VO 不用 Map.of，与全仓风格一致
        TeamCreateVO vo = new TeamCreateVO();
        vo.setApprovalId(approvalId);
        return Result.ok(vo);
    }

    /** TE04 队长加人：校验后生成 TEAM_ADD_MEMBER 审批单 */
    @Idempotent(bizType = "TEAM_ADD_MEMBER")
    @AuditLog(bizType = "TEAM_ADD_MEMBER", label = "加人申请", summary = "'加人：赛队' + #teamId")
    @PostMapping("/{teamId}/members")
    public Result<Void> addMember(@PathVariable("teamId") Long teamId,
                                  @Valid @RequestBody TeamMemberAddDTO dto) {
        commandService.addMember(teamId, dto);
        return Result.ok();
    }

    /** TE06 队员本人确认/拒绝：直接更新 team_member.status */
    @Idempotent(bizType = "TEAM_MEMBER_CONFIRM")
    @AuditLog(bizType = "TEAM_MEMBER_CONFIRM", label = "队员确认",
            summary = "'确认/拒绝队员：赛队' + #teamId + '/成员' + #memberId")
    @PostMapping("/{teamId}/members/{memberId}/confirm")
    public Result<Void> confirmMember(@PathVariable("teamId") Long teamId,
                                      @PathVariable("memberId") Long memberId,
                                      @Valid @RequestBody TeamMemberConfirmDTO dto) {
        commandService.confirmMember(teamId, memberId, dto);
        return Result.ok();
    }

    /** TE07 移除成员（队长或管理端；队长不可被移除 4010） */
    @Idempotent(bizType = "TEAM_REMOVE_MEMBER")
    @AuditLog(bizType = "TEAM_REMOVE_MEMBER", label = "移除成员",
            summary = "'移除成员：赛队' + #teamId + '/成员' + #memberId")
    @DeleteMapping("/{teamId}/members/{memberId}")
    public Result<Void> removeMember(@PathVariable("teamId") Long teamId,
                                     @PathVariable("memberId") Long memberId) {
        commandService.removeMember(teamId, memberId);
        return Result.ok();
    }

    /** TE08 非队长主动退出 */
    @Idempotent(bizType = "TEAM_EXIT")
    @AuditLog(bizType = "TEAM_EXIT", label = "退出赛队", summary = "'退出赛队：' + #teamId")
    @PostMapping("/{teamId}/exit")
    public Result<Void> exit(@PathVariable("teamId") Long teamId) {
        commandService.exit(teamId);
        return Result.ok();
    }

    /** TE09 加指导教师：仅 TEACHER 类型（4006） */
    @Idempotent(bizType = "TEAM_ADD_ADVISOR")
    @AuditLog(bizType = "TEAM_ADD_ADVISOR", label = "加指导教师",
            summary = "'加指导教师：赛队' + #teamId")
    @PostMapping("/{teamId}/advisors")
    public Result<Void> addAdvisor(@PathVariable("teamId") Long teamId,
                                   @Valid @RequestBody TeamAdvisorAddDTO dto) {
        commandService.addAdvisor(teamId, dto);
        return Result.ok();
    }

    /** TE10 移除指导教师 */
    @Idempotent(bizType = "TEAM_REMOVE_ADVISOR")
    @AuditLog(bizType = "TEAM_REMOVE_ADVISOR", label = "移除指导教师",
            summary = "'移除指导教师：赛队' + #teamId + '/教师' + #advisorId")
    @DeleteMapping("/{teamId}/advisors/{advisorId}")
    public Result<Void> removeAdvisor(@PathVariable("teamId") Long teamId,
                                       @PathVariable("advisorId") Long advisorId) {
        commandService.removeAdvisor(teamId, advisorId);
        return Result.ok();
    }

    /** TE12 解散：有材料/成果/待审批拒绝（4009） */
    @Idempotent(bizType = "TEAM_DISSOLVE")
    @AuditLog(bizType = "TEAM_DISSOLVE", label = "解散赛队", summary = "'解散赛队：' + #teamId")
    @PostMapping("/{teamId}/dissolve")
    public Result<Void> dissolve(@PathVariable("teamId") Long teamId,
                                 @Valid @RequestBody TeamDissolveDTO dto) {
        commandService.dissolve(teamId, dto);
        return Result.ok();
    }

    /** TE13 编辑队名/项目名（同比赛队名唯一 4002） */
    @Idempotent(bizType = "TEAM_UPDATE")
    @AuditLog(bizType = "TEAM_UPDATE", label = "编辑赛队",
            summary = "'编辑赛队：' + #teamId")
    @PutMapping("/{teamId}")
    public Result<Void> update(@PathVariable("teamId") Long teamId,
                              @Valid @RequestBody TeamUpdateDTO dto) {
        commandService.updateTeam(teamId, dto);
        return Result.ok();
    }
}
