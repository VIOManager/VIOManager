package com.viomanager.dict.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.Result;
import com.viomanager.dict.dto.DictItemAuditAdminDTO;
import com.viomanager.dict.dto.DictItemAuditDTO;
import com.viomanager.dict.dto.DictItemCreateDTO;
import com.viomanager.dict.dto.DictItemStatusDTO;
import com.viomanager.dict.dto.DictItemUpdateDTO;
import com.viomanager.dict.dto.DictTypeCreateDTO;
import com.viomanager.dict.dto.DictTypeUpdateDTO;
import com.viomanager.dict.service.IDictService;
import com.viomanager.dict.vo.DictItemAdminVO;
import com.viomanager.dict.vo.DictItemVO;
import com.viomanager.dict.vo.DictTypeVO;
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
 * 字典接口：D01-D03（A 线读/补充/审核）+ SY01-10（系统配置字典管理，超管独占）。
 * 读接口不挂幂等与审计；写全挂审计；幂等只挂创建类（A 线前端裸 post 无幂等头，
 * 共用口子以值查重防重复提交，不强制幂等键）。
 */
@RestController
@RequestMapping("/api/dicts")
public class DictController {

    @Resource
    private IDictService dictService;

    /**
     * D01 通用字典读：前端枚举下拉统一走它，登录即可（无角色门槛）。
     * 例：GET /api/dicts/SKILL 返回技能字典（预设项 + 用户补充项，带审核状态）。
     */
    @GetMapping("/{code}")
    public Result<List<DictItemVO>> listItems(@PathVariable("code") String code) {
        return dictService.listItems(code);
    }

    /**
     * D02 用户补充 / SY06 超管预置（同口按角色分流）：
     * 超管建预置条目直接生效；其他角色仅可补充 SKILL、入库待审核。
     */
    @AuditLog(bizType = "DICT", label = "新增字典条目")
    @PostMapping("/{code}/items")
    public Result<Long> createItem(@PathVariable("code") String code,
                                   @RequestBody DictItemCreateDTO dto) {
        return Result.ok(dictService.createItem(code, dto));
    }

    /** D03 管理员审核字典项（POST 形态，A 线面板用）：approve/pass 都收，仅 PENDING 可审 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @AuditLog(bizType = "DICT", label = "审核字典项")
    @PostMapping("/items/{id}/audit")
    public Result<Void> auditItem(@PathVariable("id") Long id, @RequestBody DictItemAuditDTO dto) {
        dictService.auditItem(id, dto);
        return Result.ok();
    }

    // ── SY 系统配置（超管独占） ──

    /** SY01 类型列表：dictCode dictName remark 条目数 待审核数 preset */
    @RequireRole({"SUPER_ADMIN"})
    @GetMapping
    public Result<List<DictTypeVO>> listTypes() {
        return dictService.listTypes();
    }

    /** SY02 新建类型。body：dictCode dictName remark；编码重复 → 1001 */
    @RequireRole({"SUPER_ADMIN"})
    @Idempotent(bizType = "DICT_TYPE_CREATE")
    @AuditLog(bizType = "DICT", label = "新建字典类型")
    @PostMapping
    public Result<Long> createType(@Valid @RequestBody DictTypeCreateDTO dto) {
        return Result.ok(dictService.createType(dto));
    }

    /** SY03 编辑类型名称/说明（dictCode 创建后不可改） */
    @RequireRole({"SUPER_ADMIN"})
    @AuditLog(bizType = "DICT", label = "编辑字典类型")
    @PutMapping("/types/{id}")
    public Result<Void> updateType(@PathVariable("id") Long id, @Valid @RequestBody DictTypeUpdateDTO dto) {
        dictService.updateType(id, dto);
        return Result.ok();
    }

    /** SY04 删除类型：预设类型或仍有条目 → 1006 */
    @RequireRole({"SUPER_ADMIN"})
    @AuditLog(bizType = "DICT", label = "删除字典类型")
    @DeleteMapping("/types/{id}")
    public Result<Void> deleteType(@PathVariable("id") Long id) {
        dictService.deleteType(id);
        return Result.ok();
    }

    /** SY05 管理视角条目列表（含停用/待审核项）。筛选：source status auditStatus keyword */
    @RequireRole({"SUPER_ADMIN"})
    @GetMapping("/{code}/items")
    public Result<List<DictItemAdminVO>> adminItems(@PathVariable("code") String code,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String auditStatus,
            @RequestParam(required = false) String keyword) {
        return dictService.adminItems(code, source, status, auditStatus, keyword);
    }

    /** SY07 编辑条目显示名/排序 */
    @RequireRole({"SUPER_ADMIN"})
    @AuditLog(bizType = "DICT", label = "编辑字典条目")
    @PutMapping("/items/{id}")
    public Result<Void> updateItem(@PathVariable("id") Long id, @Valid @RequestBody DictItemUpdateDTO dto) {
        dictService.updateItem(id, dto);
        return Result.ok();
    }

    /** SY08 逻辑删除条目（历史数据 value 原文兜底，停用优先于删除） */
    @RequireRole({"SUPER_ADMIN"})
    @AuditLog(bizType = "DICT", label = "删除字典条目")
    @DeleteMapping("/items/{id}")
    public Result<Void> deleteItem(@PathVariable("id") Long id) {
        dictService.deleteItem(id);
        return Result.ok();
    }

    /** SY09 启用/停用。停用项不再出现在 D01，历史数据不受影响 */
    @RequireRole({"SUPER_ADMIN"})
    @AuditLog(bizType = "DICT", label = "启停字典条目")
    @PutMapping("/items/{id}/status")
    public Result<Void> toggleItem(@PathVariable("id") Long id, @Valid @RequestBody DictItemStatusDTO dto) {
        dictService.toggleItem(id, dto);
        return Result.ok();
    }

    /** SY10 审核用户补充词条（PUT 形态，系统配置页用）：非 USER/PENDING → 1006 */
    @RequireRole({"SUPER_ADMIN"})
    @AuditLog(bizType = "DICT", label = "审核字典词条")
    @PutMapping("/items/{id}/audit")
    public Result<Void> auditItemAdmin(@PathVariable("id") Long id, @Valid @RequestBody DictItemAuditAdminDTO dto) {
        dictService.auditItemAdmin(id, dto);
        return Result.ok();
    }
}
