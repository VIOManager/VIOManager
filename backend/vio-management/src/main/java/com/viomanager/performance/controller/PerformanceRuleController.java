package com.viomanager.performance.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.result.Result;
import com.viomanager.performance.dto.PerfRuleSaveDTO;
import com.viomanager.performance.service.PerformanceRuleService;
import com.viomanager.performance.vo.PerfRuleVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 绩效规则接口（PF11/PF12，接口清单 §3.3），仅管理员。
 * 实施顺序规则先行：子组织未配置 enabled 规则时无法打分（8046）。
 */
@RestController
@RequestMapping("/api/performance/rules")
public class PerformanceRuleController {

    @Resource
    private PerformanceRuleService ruleService;

    /** PF11 规则列表（特定学期优先、回退默认） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping
    public Result<List<PerfRuleVO>> list(@RequestParam("subOrgId") Long subOrgId,
                                         @RequestParam(value = "semesterId", required = false) Long semesterId) {
        return Result.ok(ruleService.list(subOrgId, semesterId));
    }

    /** PF12 批量保存（upsert；semesterId 空 = 默认规则） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @AuditLog(bizType = "PERFORMANCE_RULE_SAVE", label = "保存绩效规则",
            summary = "'保存绩效规则：子组织' + #dto.subOrgId + ' ' + #dto.items.size() + ' 项'")
    @PutMapping
    public Result<Void> save(@Valid @RequestBody PerfRuleSaveDTO dto) {
        ruleService.save(dto);
        return Result.ok();
    }
}
