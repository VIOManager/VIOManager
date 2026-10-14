package com.viomanager.schedule.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.Result;
import com.viomanager.schedule.dto.ScheduleBatchCreateDTO;
import com.viomanager.schedule.dto.ScheduleUpdateDTO;
import com.viomanager.schedule.service.ScheduleExportService;
import com.viomanager.schedule.service.ScheduleService;
import com.viomanager.schedule.vo.MyScheduleVO;
import com.viomanager.schedule.vo.ScheduleCellVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
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
 * 排班接口（SC01–SC05 + SC08/SC09，接口清单 §4/§6）。
 * SC01–SC04 与导出仅管理员（部门管理员本子树）；SC05 我的排班学生视角（管理端隐藏菜单，但管理员调用按本人数据返回）。
 */
@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    @Resource
    private ScheduleService scheduleService;

    @Resource
    private ScheduleExportService exportService;

    /** SC01 周排班表（7×5 格子视图） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping
    public Result<List<ScheduleCellVO>> weekTable(@RequestParam("semesterId") Long semesterId,
                                                  @RequestParam("weekNo") Integer weekNo,
                                                  @RequestParam("subOrgId") Long subOrgId) {
        return Result.ok(scheduleService.weekTable(semesterId, weekNo, subOrgId));
    }

    /** SC02 批量排班：校验链 8009→8007→8006→8005→8008（force 强制保存打冲突标记） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "SCHEDULE_CREATE")
    @AuditLog(bizType = "SCHEDULE_CREATE", label = "批量排班",
            summary = "'排班：学期' + #dto.semesterId + ' 第' + #dto.weekNo + '周 ' + #dto.assignments.size() + ' 条'")
    @PostMapping
    public Result<Void> createBatch(@Valid @RequestBody ScheduleBatchCreateDTO dto) {
        scheduleService.createBatch(dto);
        return Result.ok();
    }

    /** SC03 编辑单条（改时段重新过校验链，无 force——冲突即拒） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @AuditLog(bizType = "SCHEDULE_UPDATE", label = "编辑排班", summary = "'编辑排班：' + #id")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody ScheduleUpdateDTO dto) {
        scheduleService.update(id, dto);
        return Result.ok();
    }

    /** SC04 取消排班（置 CANCELLED，幂等） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @AuditLog(bizType = "SCHEDULE_CANCEL", label = "取消排班", summary = "'取消排班：' + #id")
    @DeleteMapping("/{id}")
    public Result<Void> cancel(@PathVariable("id") Long id) {
        scheduleService.cancel(id);
        return Result.ok();
    }

    /** SC05 我的排班（学生视角；semesterId/weekNo 均可选） */
    @GetMapping("/my")
    public Result<List<MyScheduleVO>> my(
            @RequestParam(value = "semesterId", required = false) Long semesterId,
            @RequestParam(value = "weekNo", required = false) Integer weekNo) {
        return Result.ok(scheduleService.my(semesterId, weekNo));
    }

    /** SC08 周排班表导出（xlsx 流） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @AuditLog(bizType = "SCHEDULE_EXPORT", label = "导出周排班表",
            summary = "'导出周排班：学期' + #semesterId + ' 第' + #weekNo + '周'")
    @GetMapping("/export")
    public void exportWeek(@RequestParam("semesterId") Long semesterId,
                           @RequestParam("weekNo") Integer weekNo,
                           @RequestParam("subOrgId") Long subOrgId,
                           HttpServletResponse response) {
        exportService.exportWeek(semesterId, weekNo, subOrgId, response);
    }

    /** SC09 学期排班统计导出（每人次数与明细，xlsx 流） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @AuditLog(bizType = "SCHEDULE_EXPORT", label = "导出学期排班统计",
            summary = "'导出学期排班统计：学期' + #semesterId")
    @GetMapping("/export-stats")
    public void exportStats(@RequestParam("semesterId") Long semesterId,
                            @RequestParam(value = "subOrgId", required = false) Long subOrgId,
                            HttpServletResponse response) {
        exportService.exportStats(semesterId, subOrgId, response);
    }
}
