package com.viomanager.project.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import com.viomanager.project.dto.ProjectAdvisorAddDTO;
import com.viomanager.project.dto.ProjectCreateDTO;
import com.viomanager.project.dto.ProjectMemberAddDTO;
import com.viomanager.project.dto.ProjectQueryDTO;
import com.viomanager.project.dto.ProjectUpdateDTO;
import com.viomanager.project.service.ProjectMemberService;
import com.viomanager.project.service.ProjectService;
import com.viomanager.project.vo.ProjectDetailVO;
import com.viomanager.project.vo.ProjectMemberVO;
import com.viomanager.project.vo.ProjectVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
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

    @Resource
    private ProjectMemberService memberService;

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

    // ------------------------------------------------------------ 成员与指导教师（PR06–PR10）

    /** PR06 成员列表：含角色与加入时间；项目可见者可看 */
    @GetMapping("/{id}/members")
    public Result<List<ProjectMemberVO>> listMembers(@PathVariable("id") Long id) {
        return memberService.listMembers(id);
    }

    /** PR07 添加成员：仅所有人；已在项目 7005；不走审批 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @AuditLog(bizType = "PROJECT_MEMBER_ADD", label = "添加项目成员",
            summary = "'项目' + #id + ' 添加成员 ' + #dto.memberId")
    @PostMapping("/{id}/members")
    public Result<Void> addMember(@PathVariable("id") Long id,
                                  @Valid @RequestBody ProjectMemberAddDTO dto) {
        return memberService.addMember(id, dto);
    }

    /** PR08 移除成员：仅所有人；OWNER 行不可移除（7002） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @AuditLog(bizType = "PROJECT_MEMBER_REMOVE", label = "移除项目成员",
            summary = "'项目' + #id + ' 移除成员 ' + #memberId")
    @DeleteMapping("/{id}/members/{memberId}")
    public Result<Void> removeMember(@PathVariable("id") Long id,
                                     @PathVariable("memberId") Long memberId) {
        return memberService.removeMember(id, memberId);
    }

    /** PR09 添加指导教师：仅所有人；目标须教师类型（7006） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @AuditLog(bizType = "PROJECT_ADVISOR_ADD", label = "添加指导教师",
            summary = "'项目' + #id + ' 添加教师 ' + #dto.memberId")
    @PostMapping("/{id}/advisors")
    public Result<Void> addAdvisor(@PathVariable("id") Long id,
                                   @Valid @RequestBody ProjectAdvisorAddDTO dto) {
        return memberService.addAdvisor(id, dto);
    }

    /** PR10 移除指导教师：仅所有人 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @AuditLog(bizType = "PROJECT_ADVISOR_REMOVE", label = "移除指导教师",
            summary = "'项目' + #id + ' 移除教师 ' + #memberId")
    @DeleteMapping("/{id}/advisors/{memberId}")
    public Result<Void> removeAdvisor(@PathVariable("id") Long id,
                                      @PathVariable("memberId") Long memberId) {
        return memberService.removeAdvisor(id, memberId);
    }
}
