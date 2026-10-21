package com.viomanager.semester.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.Result;
import com.viomanager.semester.dto.SemesterSaveDTO;
import com.viomanager.semester.service.SemesterService;
import com.viomanager.semester.vo.SemesterVO;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 学期管理接口（A 线契约 S01-S04）：路径 /api/semesters，与前端 semester.ts 对应。
 * 全部仅超管（清单 §4：学期是全平台维度，部门管理员与学生不碰）。
 * 切换任务 S05-S08 在 SwitchTaskController（Step 3-4 交付）。
 */
@RestController
@RequestMapping("/api/semesters")
public class SemesterController {

    @Resource
    private SemesterService semesterService;

    /** S01 学期列表 */
    @RequireRole({"SUPER_ADMIN"})
    @GetMapping
    public Result<List<SemesterVO>> list() {
        return Result.ok(semesterService.list());
    }

    /** S02 创建学期（名称重复 3008） */
    @RequireRole({"SUPER_ADMIN"})
    @Idempotent(bizType = "SEMESTER_CREATE")
    @AuditLog(bizType = "SEMESTER", label = "创建学期")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody SemesterSaveDTO dto) {
        return Result.ok(semesterService.create(dto));
    }

    /** S03 编辑基础信息（status/isCurrent 不收，只归切换引擎） */
    @RequireRole({"SUPER_ADMIN"})
    @Idempotent(bizType = "SEMESTER_UPDATE")
    @AuditLog(bizType = "SEMESTER", label = "编辑学期")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id, @Valid @RequestBody SemesterSaveDTO dto) {
        semesterService.update(id, dto);
        return Result.ok();
    }

    /** S04 删除（关联比赛/赛队/成果/切换任务 3009） */
    @RequireRole({"SUPER_ADMIN"})
    @Idempotent(bizType = "SEMESTER_DELETE")
    @AuditLog(bizType = "SEMESTER", label = "删除学期")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        semesterService.delete(id);
        return Result.ok();
    }
}
