package com.viomanager.material.controller;

import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.result.Result;
import com.viomanager.material.dto.MaterialTaskCreateDTO;
import com.viomanager.material.dto.MaterialTaskUpdateDTO;
import com.viomanager.material.service.MaterialTaskService;
import com.viomanager.material.vo.MaterialTaskVO;
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
 * 材料任务接口（MT01–MT04），管理端配置入口，口径以 C 线接口清单 §2 为准。
 * 四个接口全部仅限管理员（@RequireRole 挡学生，规范 §5.4 权限第二道关口）；
 * @Idempotent / @AuditLog 待 bizType 词表在规范登记后补挂（如 MATERIAL_TASK_CREATE）。
 */
@RestController
@RequestMapping("/api/materials/tasks")
public class MaterialTaskController {

    @Resource
    private MaterialTaskService taskService;

    /** MT01 任务列表：contestId 可选筛选；出参含要求条数与队伍两数等聚合列 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping
    public Result<List<MaterialTaskVO>> list(
            @RequestParam(value = "contestId", required = false) Long contestId) {
        return taskService.list(contestId);
    }

    /** MT02 创建任务：同一比赛仅一个（重复 1001）；semesterId 随比赛带出，status 默认 ACTIVE */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @PostMapping
    public Result<MaterialTaskVO> create(@Valid @RequestBody MaterialTaskCreateDTO dto) {
        return taskService.create(dto);
    }

    /** MT03 编辑任务：只改 name / description / deadline 三字段 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody MaterialTaskUpdateDTO dto) {
        taskService.update(id, dto);
        return Result.ok();
    }

    /** MT04 删除任务：任一要求有提交记录 5006 拒绝；要求随任务级联逻辑删除 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        taskService.delete(id);
        return Result.ok();
    }
}
