package com.viomanager.team.service;

import com.viomanager.team.dto.TeamAdvisorAddDTO;
import com.viomanager.team.dto.TeamCreateDTO;
import com.viomanager.team.dto.TeamDissolveDTO;
import com.viomanager.team.dto.TeamMemberAddDTO;
import com.viomanager.team.dto.TeamMemberConfirmDTO;
import com.viomanager.team.dto.TeamUpdateDTO;

/**
 * 赛队写服务（TE03–TE13）。
 * 审批类操作（TE03/04/07/09）只生成审批单不直接生效；直接类操作（TE06/08/10/12/13）即时变更。
 */
public interface ITeamCommandService {

    /** TE03 组队申请：校验后生成 TEAM_CREATE 审批单，返回 approvalId */
    Long createTeam(TeamCreateDTO dto);

    /** TE04 加人：校验后生成 TEAM_ADD_MEMBER 审批单 */
    void addMember(Long teamId, TeamMemberAddDTO dto);

    /** TE06 队员确认/拒绝：直接更新 team_member.status */
    void confirmMember(Long teamId, Long memberId, TeamMemberConfirmDTO dto);

    /** TE07 移除成员：生成 TEAM_REMOVE_MEMBER 审批单（队长不可被移除 4010） */
    void removeMember(Long teamId, Long memberId);

    /** TE08 退出：直接置 EXITED（队长不可退 4010） */
    void exit(Long teamId);

    /** TE09 加指导教师：仅 TEACHER 类型，生成 TEAM_ADD_ADVISOR 审批单 */
    void addAdvisor(Long teamId, TeamAdvisorAddDTO dto);

    /** TE10 移除指导教师：直接置 REMOVED */
    void removeAdvisor(Long teamId, Long advisorMemberId);

    /** TE12 解散：有材料/成果/待审批拒绝（4009），否则置 DISSOLVED + 成员 EXITED */
    void dissolve(Long teamId, TeamDissolveDTO dto);

    /** TE13 编辑队名/项目名（同比赛队名唯一 4002） */
    void updateTeam(Long teamId, TeamUpdateDTO dto);
}
