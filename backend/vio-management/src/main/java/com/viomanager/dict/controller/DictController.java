package com.viomanager.dict.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.Result;
import com.viomanager.dict.dto.DictItemAuditDTO;
import com.viomanager.dict.dto.DictItemCreateDTO;
import com.viomanager.dict.service.IDictService;
import com.viomanager.dict.vo.DictItemVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典接口：通用字典读。接口口径以骨架接口清单 D01 为准。
 * 登录即可调（无角色门槛）；只读接口不挂幂等与审计。
 */
@RestController
@RequestMapping("/api/dicts")
public class DictController {

    @Resource
    private IDictService dictService;

    /**
     * 通用字典读：前端枚举下拉统一走它。
     * 例：GET /api/dicts/SKILL 返回技能字典（预设项 + 用户补充项，带审核状态）。
     */
    @GetMapping("/{code}")
    public Result<List<DictItemVO>> listItems(@PathVariable("code") String code) {
        return dictService.listItems(code);
    }

    /** D02 用户补充技能字典项：登录即可提交，审核通过才进下拉（A 线清单 §3） */
    @Idempotent(bizType = "DICT_SKILL_ITEM_CREATE")
    @AuditLog(bizType = "DICT", label = "补充技能字典项")
    @PostMapping("/SKILL/items")
    public Result<Long> createSkillItem(@Valid @RequestBody DictItemCreateDTO dto) {
        return Result.ok(dictService.createSkillItem("SKILL", dto));
    }

    /** D03 管理员审核字典项：通过入库 / 驳回（仅 PENDING 可审） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "DICT_ITEM_AUDIT")
    @AuditLog(bizType = "DICT", label = "审核字典项")
    @PostMapping("/items/{id}/audit")
    public Result<Void> auditItem(@PathVariable("id") Long id, @Valid @RequestBody DictItemAuditDTO dto) {
        dictService.auditItem(id, dto);
        return Result.ok();
    }
}
