package com.viomanager.project.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import com.viomanager.project.dto.ProjectCreateDTO;
import com.viomanager.project.dto.ProjectQueryDTO;
import com.viomanager.project.dto.ProjectUpdateDTO;
import com.viomanager.project.service.ProjectService;
import com.viomanager.project.vo.ProjectDetailVO;
import com.viomanager.project.vo.ProjectVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目主体接口（PR01–PR05），口径以 docs/api/项目管理-接口清单.md §3.1 为准。
 * PR01/PR03 登录即可（可见性八条规则在 service 层统一判定，超管/部门管理员/
 * 学生共用一份数据范围逻辑）；三个写接口按角色门槛收口并挂 @AuditLog 留痕。
 * @Idempotent 暂不挂（前端未带 X-Idempotency-Key 头，挂了必 1001，
 * 同 AchievementController 口径；清单 PR02 的幂等头待前端补齐后再加上）。
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Resource
    private ProjectService projectService;

    /** PR01 项目列表：数据范围按角色自动；筛选 keyword/status/contestId/semesterId/orgId */
    @GetMapping
    public Result<PageResult<ProjectVO>> list(ProjectQueryDTO dto) {
        return projectService.list(dto);
    }

    /** PR02 创建项目：所有人=当前用户；同学期同组织名称唯一（7004） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @AuditLog(bizType = "PROJECT_CREATE", label = "创建项目",
            summary = "'创建项目：' + #dto.name")
    @PostMapping
    public Result<ProjectDetailVO> create(@Valid @RequestBody ProjectCreateDTO dto) {
        return projectService.create(dto);
    }

    /** PR03 项目详情：基本信息 + 所有人 + visibility；不可见 7003 */
    @GetMapping("/{id}")
    public Result<ProjectDetailVO> detail(@PathVariable("id") Long id) {
        return projectService.detail(id);
    }

    /** PR04 编辑项目：仅所有人（7002）；状态限 ACTIVE→FINISHED→ARCHIVED 按序 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @AuditLog(bizType = "PROJECT_UPDATE", label = "编辑项目",
            summary = "'编辑项目：' + #id")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody ProjectUpdateDTO dto) {
        return projectService.update(id, dto);
    }

    /** PR05 删除项目：仅所有人；有 ACTIVE 挂载拒绝（7010，先解绑） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @AuditLog(bizType = "PROJECT_DELETE", label = "删除项目",
            summary = "'删除项目：' + #id")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        return projectService.delete(id);
    }
}
