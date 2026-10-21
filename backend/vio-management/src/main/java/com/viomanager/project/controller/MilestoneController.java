package com.viomanager.project.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.result.Result;
import com.viomanager.project.dto.MilestoneCreateDTO;
import com.viomanager.project.dto.MilestoneUpdateDTO;
import com.viomanager.project.service.MilestoneService;
import com.viomanager.project.vo.MilestoneVO;
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
 * 比赛时间节点接口（PR16–PR20），口径以 docs/api/项目管理-接口清单.md §3.4 为准。
 * PR16 列表登录即可（管理员看全部/学生仅 APPROVED，数据范围在 service）；
 * PR17-20 维护口仅部门管理员与超管；PR20 提审挂 @AuditLog。
 * @Idempotent 暂不挂（同全站口径：前端未带 X-Idempotency-Key 头）。
 */
@RestController
public class MilestoneController {

    @Resource
    private MilestoneService milestoneService;

    /** PR16 节点列表：管理员看全部（部门管理员限本子树），普通用户仅 APPROVED */
    @GetMapping("/api/contests/{id}/milestones")
    public Result<List<MilestoneVO>> list(@PathVariable("id") Long id,
                                          @RequestParam(value = "status", required = false) String status,
                                          @RequestParam(value = "nodeType", required = false) String nodeType) {
        return milestoneService.list(id, status, nodeType);
    }

    /** PR17 创建节点（DRAFT）：仅部门管理员与超管 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @AuditLog(bizType = "MILESTONE_CREATE", label = "创建时间节点",
            summary = "'比赛' + #id + ' 创建节点：' + #dto.name")
    @PostMapping("/api/contests/{id}/milestones")
    public Result<MilestoneVO> create(@PathVariable("id") Long id,
                                      @Valid @RequestBody MilestoneCreateDTO dto) {
        return milestoneService.create(id, dto);
    }

    /** PR18 编辑节点：仅 DRAFT 与 REJECTED 可编辑（7009） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @AuditLog(bizType = "MILESTONE_UPDATE", label = "编辑时间节点",
            summary = "'编辑节点：' + #id")
    @PutMapping("/api/milestones/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody MilestoneUpdateDTO dto) {
        return milestoneService.update(id, dto);
    }

    /** PR19 删除节点：仅 DRAFT（7009） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @AuditLog(bizType = "MILESTONE_DELETE", label = "删除时间节点",
            summary = "'删除节点：' + #id")
    @DeleteMapping("/api/milestones/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        return milestoneService.delete(id);
    }

    /** PR20 提交审核：转 PENDING 并开 MILESTONE_APPROVE 审批单 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @AuditLog(bizType = "MILESTONE_SUBMIT", label = "提交节点审核",
            summary = "'提交节点审核：' + #id")
    @PostMapping("/api/milestones/{id}/submit")
    public Result<Void> submit(@PathVariable("id") Long id) {
        return milestoneService.submit(id);
    }
}
