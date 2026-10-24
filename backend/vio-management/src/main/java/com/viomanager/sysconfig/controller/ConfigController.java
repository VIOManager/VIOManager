package com.viomanager.sysconfig.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.result.Result;
import com.viomanager.sysconfig.dto.ConfigUpdateDTO;
import com.viomanager.sysconfig.service.ISysConfigService;
import com.viomanager.sysconfig.vo.SysConfigVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统参数接口（清单 CF01-02）：超管独占（PRD §2.1）。
 * 参数由建库种子维护，不提供新增/删除；改值挂审计（AQ01 按 bizType=CONFIG 可筛）。
 */
@RestController
@RequestMapping("/api/configs")
public class ConfigController {

    @Resource
    private ISysConfigService sysConfigService;

    /** CF01 参数列表：configKey configValue configName editable remark */
    @RequireRole({"SUPER_ADMIN"})
    @GetMapping
    public Result<List<SysConfigVO>> list() {
        return sysConfigService.list();
    }

    /** CF02 修改参数值。editable=0 → 1006；key 不存在 → 1004 */
    @RequireRole({"SUPER_ADMIN"})
    @AuditLog(bizType = "CONFIG", label = "修改系统参数")
    @PutMapping("/{key}")
    public Result<Void> update(@PathVariable("key") String key, @Valid @RequestBody ConfigUpdateDTO dto) {
        sysConfigService.update(key, dto);
        return Result.ok();
    }
}
