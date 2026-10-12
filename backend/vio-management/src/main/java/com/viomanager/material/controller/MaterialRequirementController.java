package com.viomanager.material.controller;

import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.result.Result;
import com.viomanager.material.dto.MaterialRequirementCreateDTO;
import com.viomanager.material.dto.MaterialRequirementUpdateDTO;
import com.viomanager.material.service.MaterialRequirementService;
import com.viomanager.material.vo.MaterialRequirementVO;
import jakarta.annotation.Resource;
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
 * 材料要求接口：任务下要求列表（清单 §2 补录项）+ MR01–MR03（增改删），
 * 管理端配置入口，口径以 C 线接口清单 §2 为准。
 * 三个接口全部仅限管理员（@RequireRole 挡学生，规范 §5.4 权限第二道关口）；
 * 入参校验文案由 service 层手写（见 MaterialRequirementServiceImpl），
 * 故本层不挂 @Valid；@Idempotent / @AuditLog 待 bizType 词表登记后再补。
 */
@RestController
@RequestMapping("/api/materials")
public class MaterialRequirementController {

    @Resource
    private MaterialRequirementService requirementService;

    /** 任务下要求列表（清单补录项）：查该任务所有未删要求，sort_no / id 升序保证展示顺序稳定 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping("/tasks/{taskId}/requirements")
    public Result<List<MaterialRequirementVO>> listByTask(@PathVariable("taskId") Long taskId) {
        return requirementService.listByTask(taskId);
    }

    /** MR01 添加要求：taskId 走路径；contestId 随任务带出，systemGenerated 恒 0 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @PostMapping("/tasks/{taskId}/requirements")
    public Result<MaterialRequirementVO> create(@PathVariable("taskId") Long taskId,
                                                @RequestBody MaterialRequirementCreateDTO dto) {
        return requirementService.create(taskId, dto);
    }

    /** MR02 编辑要求：系统生成的佐证要求 403 拒绝；taskId / contestId 不参与修改 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @PutMapping("/requirements/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @RequestBody MaterialRequirementUpdateDTO dto) {
        requirementService.update(id, dto);
        return Result.ok();
    }

    /** MR03 删除要求：已有提交记录 5005 拒绝；系统生成项 403 拒绝；逻辑删除 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @DeleteMapping("/requirements/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        requirementService.delete(id);
        return Result.ok();
    }
}
