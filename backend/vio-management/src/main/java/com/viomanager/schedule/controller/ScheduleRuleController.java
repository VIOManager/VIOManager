package com.viomanager.schedule.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.result.Result;
import com.viomanager.schedule.dto.ScheduleRuleSaveDTO;
import com.viomanager.schedule.service.ScheduleRuleService;
import com.viomanager.schedule.vo.ScheduleRuleVO;
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
 * 排班规则接口（SC10/SC11，接口清单 §5），仅管理员。
 * 子组织维度 7×5 时段开关 + 人数上下限；学期特定规则优先、回退默认（semesterId 空）。
 */
@RestController
@RequestMapping("/api/schedules/rules")
public class ScheduleRuleController {

    @Resource
    private ScheduleRuleService ruleService;

    /** SC10 规则列表（7×5 全时段视图，未配置时段按未启用返回） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping
    public Result<List<ScheduleRuleVO>> list(@RequestParam("subOrgId") Long subOrgId,
                                             @RequestParam(value = "semesterId", required = false) Long semesterId) {
        return Result.ok(ruleService.list(subOrgId, semesterId));
    }

    /** SC11 批量保存（upsert；semesterId 空 = 子组织默认规则） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @AuditLog(bizType = "SCHEDULE_RULE_SAVE", label = "保存排班规则",
            summary = "'保存排班规则：子组织' + #dto.subOrgId + ' ' + #dto.items.size() + ' 项'")
    @PutMapping
    public Result<Void> save(@Valid @RequestBody ScheduleRuleSaveDTO dto) {
        ruleService.save(dto);
        return Result.ok();
    }
}
