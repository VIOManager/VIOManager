package com.viomanager.project.service;

import com.viomanager.common.result.Result;
import com.viomanager.project.dto.TeamProjectAttachDTO;
import com.viomanager.project.vo.ProjectTeamVO;
import com.viomanager.project.vo.TeamProjectVO;
import java.util.List;

/**
 * 赛队挂载（PR11–PR15）：表在项目域，接口由项目线出，B 线前端在赛队详情页调用。
 * PR11/PR12 互为镜像（项目详情页与赛队详情页各展示一侧）。
 */
public interface TeamProjectService {

    /** PR11 使用本项目的赛队列表：项目可见者可看 */
    Result<List<ProjectTeamVO>> listTeamsOfProject(Long projectId);

    /** PR12 本队挂载的项目列表：本队成员与管理员可看 */
    Result<List<TeamProjectVO>> listProjectsOfTeam(Long teamId);

    /** PR13 队长挂载项目：队长校验、项目可见（§2）、同学期 7008、同队同项目同赛道不重复 7007 */
    Result<Void> attach(Long teamId, TeamProjectAttachDTO dto);

    /** PR14 解绑（置 RELEASED）：队长；已生成的任务不回收 */
    Result<Void> detach(Long teamId, Long projectId);

    /** PR15 可挂载项目池：PUBLIC 或 ORG 同部门的、加自己参与的；可选筛选 contestId */
    Result<List<com.viomanager.project.vo.ProjectVO>> attachable(Long contestId);
}
