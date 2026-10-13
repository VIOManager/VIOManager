package com.viomanager.contest.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.Result;
import com.viomanager.contest.dto.TemplateCreateDTO;
import com.viomanager.contest.service.IContestTemplateService;
import com.viomanager.contest.vo.TemplateVO;
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
 * 比赛模板管理接口（T01–T05）。
 * 模板为全平台公共数据，管理端可 CRUD；被比赛引用时禁止删除（4012）。
 * 全部方法限管理端：@RequireRole SUPER_ADMIN / DEPT_ADMIN。
 * service 层返回 Result，控制器直接透传。
 */
@RestController
@RequestMapping("/api/contest-templates")
public class ContestTemplateController {

    @Resource
    private IContestTemplateService templateService;

    /** T01 列表（可按 category 筛选） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping
    public Result<List<TemplateVO>> list(
            @RequestParam(value = "category", required = false) String category) {
        return templateService.listTemplates(category);
    }

    /** T02 创建（默认规则变更不影响已创建比赛） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "CONTEST_TEMPLATE_CREATE")
    @AuditLog(bizType = "CONTEST_TEMPLATE_CREATE", label = "创建模板",
            summary = "'创建模板：' + #dto.name")
    @PostMapping
    public Result<TemplateVO> create(@Valid @RequestBody TemplateCreateDTO dto) {
        return templateService.createTemplate(dto);
    }

    /** T03 详情 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping("/{id}")
    public Result<TemplateVO> detail(@PathVariable("id") Long id) {
        return templateService.getTemplate(id);
    }

    /** T04 编辑（默认规则变更不影响已创建比赛） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "CONTEST_TEMPLATE_UPDATE")
    @AuditLog(bizType = "CONTEST_TEMPLATE_UPDATE", label = "编辑模板",
            summary = "'编辑模板：' + #id")
    @PutMapping("/{id}")
    public Result<TemplateVO> update(@PathVariable("id") Long id,
                                     @Valid @RequestBody TemplateCreateDTO dto) {
        return templateService.updateTemplate(id, dto);
    }

    /** T05 删除（被比赛引用拒绝 4012） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "CONTEST_TEMPLATE_DELETE")
    @AuditLog(bizType = "CONTEST_TEMPLATE_DELETE", label = "删除模板",
            summary = "'删除模板：' + #id")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        return templateService.deleteTemplate(id);
    }
}
