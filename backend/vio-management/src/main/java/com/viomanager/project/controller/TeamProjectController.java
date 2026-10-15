package com.viomanager.project.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.result.Result;
import com.viomanager.project.dto.TeamProjectAttachDTO;
import com.viomanager.project.service.TeamProjectService;
import com.viomanager.project.vo.ProjectTeamVO;
import com.viomanager.project.vo.ProjectVO;
import com.viomanager.project.vo.TeamProjectVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 赛队挂载接口（PR11–PR15），口径以 docs/api/项目管理-接口清单.md §3.3 为准。
 * 表在项目域、接口由项目线出，B 线前端在赛队详情页调用——路径跨 /api/projects 与
 * /api/teams 两段，方法级全路径声明。PR11/PR15 登录即可（项目可见性/池口径在 service）；
 * PR13/PR14 挂载解绑是队长操作（service 校验 B 线队长身份），挂 @AuditLog 留痕。
 */
@RestController
public class TeamProjectController {

    @Resource
    private TeamProjectService teamProjectService;

    /** PR11 使用本项目的赛队列表：项目可见者可看 */
    @GetMapping("/api/projects/{id}/teams")
    public Result<List<ProjectTeamVO>> listTeamsOfProject(@PathVariable("id") Long id) {
        return teamProjectService.listTeamsOfProject(id);
    }

    /** PR12 本队挂载的项目列表：本队成员与管理员可看 */
    @GetMapping("/api/teams/{id}/projects")
    public Result<List<TeamProjectVO>> listProjectsOfTeam(@PathVariable("id") Long id) {
        return teamProjectService.listProjectsOfTeam(id);
    }

    /** PR13 队长挂载项目：同学期 7008、同队同项目同赛道不重复 7007 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @AuditLog(bizType = "TEAM_PROJECT_ATTACH", label = "赛队挂载项目",
            summary = "'赛队' + #id + ' 挂载项目 ' + #dto.projectId")
    @PostMapping("/api/teams/{id}/projects")
    public Result<Void> attach(@PathVariable("id") Long id,
                               @Valid @RequestBody TeamProjectAttachDTO dto) {
        return teamProjectService.attach(id, dto);
    }

    /** PR14 解绑（置 RELEASED）：队长；已生成的任务不回收 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @AuditLog(bizType = "TEAM_PROJECT_DETACH", label = "赛队解绑项目",
            summary = "'赛队' + #id + ' 解绑项目 ' + #projectId")
    @DeleteMapping("/api/teams/{id}/projects/{projectId}")
    public Result<Void> detach(@PathVariable("id") Long id,
                               @PathVariable("projectId") Long projectId) {
        return teamProjectService.detach(id, projectId);
    }

    /** PR15 可挂载项目池（挂载选择器用）：PUBLIC 或 ORG 同部门的、加自己参与的 */
    @GetMapping("/api/projects/attachable")
    public Result<List<ProjectVO>> attachable(
            @RequestParam(value = "contestId", required = false) Long contestId) {
        return teamProjectService.attachable(contestId);
    }
}
