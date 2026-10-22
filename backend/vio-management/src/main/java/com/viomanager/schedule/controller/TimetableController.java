package com.viomanager.schedule.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.Result;
import com.viomanager.schedule.dto.ClassMappingConfirmDTO;
import com.viomanager.schedule.dto.ImportSemesterAssignDTO;
import com.viomanager.schedule.service.ClassMappingService;
import com.viomanager.schedule.service.TimetableImportService;
import com.viomanager.schedule.vo.ClassMappingVO;
import com.viomanager.schedule.vo.CourseRecordVO;
import com.viomanager.schedule.vo.ImportBatchVO;
import com.viomanager.schedule.vo.ImportResultVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 课表导入接口（TT01–TT08，接口清单 §2），全部仅限管理员。
 * 课表与映射是全平台共享数据（学校课表不分部门），不再按组织子树裁剪（见 ClassMappingServiceImpl 说明）。
 * 写接口挂审计与幂等（TT02 幂等键前端生成；TT05/TT07 无幂等——重复指定/确认幂等无害）。
 */
@RestController
@RequestMapping("/api/timetables")
public class TimetableController {

    @Resource
    private TimetableImportService importService;

    @Resource
    private ClassMappingService classMappingService;

    /** TT01 模板下载（16 列 + 5 条示例行，程序化生成 xlsx 流） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping("/import-template")
    public void downloadTemplate(HttpServletResponse response) {
        importService.downloadTemplate(response);
    }

    /** TT02 上传导入：同步解析，学期归一化失败批次留 PENDING 等 TT05 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "TIMETABLE_IMPORT")
    @AuditLog(bizType = "TIMETABLE_IMPORT", label = "上传课表",
            summary = "'上传课表：' + #file.originalFilename")
    @PostMapping("/imports")
    public Result<ImportResultVO> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(importService.upload(file));
    }

    /** TT03 批次列表 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping("/imports")
    public Result<List<ImportBatchVO>> list(
            @RequestParam(value = "semesterId", required = false) Long semesterId) {
        return Result.ok(importService.list(semesterId));
    }

    /** TT04 批次详情（含每行错误明细） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping("/imports/{id}")
    public Result<ImportBatchVO> detail(@PathVariable("id") Long id) {
        return Result.ok(importService.detail(id));
    }

    /** TT05 归一化失败时人工指定学期（仅 PENDING，指定后续跑解析） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @AuditLog(bizType = "TIMETABLE_SEMESTER_ASSIGN", label = "指定课表学期",
            summary = "'批次 ' + #id + ' 指定学期 ' + #dto.semesterId")
    @PutMapping("/imports/{id}/semester")
    public Result<Void> assignSemester(@PathVariable("id") Long id,
                                       @Valid @RequestBody ImportSemesterAssignDTO dto) {
        importService.assignSemester(id, dto.getSemesterId());
        return Result.ok();
    }

    /** TT06 映射列表（按学期 / 确认状态 / 是否有在册成员筛选；档案班级名实时匹配） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping("/class-mappings")
    public Result<List<ClassMappingVO>> classMappings(
            @RequestParam(value = "semesterId", required = false) Long semesterId,
            @RequestParam(value = "confirmed", required = false) Boolean confirmed,
            @RequestParam(value = "hasMember", required = false) Boolean hasMember) {
        return Result.ok(classMappingService.list(semesterId, confirmed, hasMember));
    }

    /** TT07 批量确认映射（确认后参与空课统计，PRD 铁规则） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @AuditLog(bizType = "CLASS_MAPPING_CONFIRM", label = "确认班级映射",
            summary = "'确认班级映射：' + #dto.ids")
    @PutMapping("/class-mappings/confirm")
    public Result<Void> confirm(@Valid @RequestBody ClassMappingConfirmDTO dto) {
        classMappingService.confirm(dto.getIds());
        return Result.ok();
    }

    /** TT08 课表记录查询（管理端核对） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping("/courses")
    public Result<List<CourseRecordVO>> courses(
            @RequestParam("semesterId") Long semesterId,
            @RequestParam(value = "weekNo", required = false) Integer weekNo,
            @RequestParam(value = "classKey", required = false) String classKey) {
        return Result.ok(classMappingService.courses(semesterId, weekNo, classKey));
    }
}
