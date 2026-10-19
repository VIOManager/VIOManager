package com.viomanager.project.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.result.Result;
import com.viomanager.project.dto.TaskCreateDTO;
import com.viomanager.project.dto.TaskUpdateDTO;
import com.viomanager.project.service.ProjectTaskService;
import com.viomanager.project.vo.TaskVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目任务接口（PR21–PR24），口径以 docs/api/项目管理-接口清单.md §3.5 为准。
 * PR21 列表登录即可（项目可见性在 service 判定）；写口仅所有人（负责人本人
 * 置 DONE 的窄口也在 service），挂 @AuditLog 留痕。
 */
@RestController
@RequestMapping("/api/projects/{id}/tasks")
public class ProjectTaskController {

    @Resource
    private ProjectTaskService taskService;

    /** PR21 任务列表：项目可见者可看；筛选 status / assigneeMemberId，按 sort_no 排序 */
    @GetMapping
    public Result<List<TaskVO>> list(@PathVariable("id") Long id,
                                     @RequestParam(value = "status", required = false) String status,
                                     @RequestParam(value = "assigneeMemberId", required = false) Long assigneeMemberId) {
        return taskService.list(id, status, assigneeMemberId);
    }

    /** PR22 创建手工任务（milestone_node_id 恒空）：仅所有人 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @AuditLog(bizType = "PROJECT_TASK_CREATE", label = "创建任务",
            summary = "'项目' + #id + ' 创建任务：' + #dto.title")
    @PostMapping
    public Result<TaskVO> create(@PathVariable("id") Long id,
                                 @Valid @RequestBody TaskCreateDTO dto) {
        return taskService.create(id, dto);
    }

    /** PR23 编辑或更新状态：所有人全字段；负责人本人仅可置 DONE */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @AuditLog(bizType = "PROJECT_TASK_UPDATE", label = "更新任务",
            summary = "'项目' + #id + ' 更新任务 ' + #taskId")
    @PutMapping("/{taskId}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @PathVariable("taskId") Long taskId,
                               @Valid @RequestBody TaskUpdateDTO dto) {
        return taskService.update(id, taskId, dto);
    }

    /** PR24 删除任务：仅所有人；同步生成的任务也可删（删后不自动重建） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @AuditLog(bizType = "PROJECT_TASK_DELETE", label = "删除任务",
            summary = "'项目' + #id + ' 删除任务 ' + #taskId")
    @DeleteMapping("/{taskId}")
    public Result<Void> delete(@PathVariable("id") Long id,
                               @PathVariable("taskId") Long taskId) {
        return taskService.delete(id, taskId);
    }
}
