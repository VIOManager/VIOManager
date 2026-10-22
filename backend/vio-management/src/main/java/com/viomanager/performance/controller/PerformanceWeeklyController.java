package com.viomanager.performance.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.Result;
import com.viomanager.performance.dto.WeeklyResubmitDTO;
import com.viomanager.performance.dto.WeeklyScoreDTO;
import com.viomanager.performance.dto.WeeklySubmitDTO;
import com.viomanager.performance.service.PerformanceScoreService;
import com.viomanager.performance.service.PerformanceWeeklyService;
import com.viomanager.performance.vo.WeeklyDetailVO;
import com.viomanager.performance.vo.WeeklyItemVO;
import jakarta.annotation.Resource;
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

/**
 * 绩效周报接口（PF01–PF08，接口清单 §3）。
 * 学生侧 PF01–PF05 数据范围 = 本人（service 层 member_id 强校验，学生调用不挂 @RequireRole）；
 * 管理侧 PF06/PF07/PF08 仅管理员（部门管理员本子树）。
 * PF02/PF07 带幂等头；**不走审批中心**——PRD §3.13 管理员直审，打分动作即审核。
 */
@RestController
@RequestMapping("/api/performance")
public class PerformanceWeeklyController {

    @Resource
    private PerformanceWeeklyService weeklyService;

    @Resource
    private PerformanceScoreService scoreService;

    /** PF01 我的周报列表（semesterId 默认当前学期，status 可选） */
    @GetMapping("/my/weeklies")
    public Result<List<WeeklyItemVO>> myWeeklies(
            @RequestParam(value = "semesterId", required = false) Long semesterId,
            @RequestParam(value = "status", required = false) String status) {
        return Result.ok(weeklyService.myWeeklies(semesterId, status));
    }

    /** PF02 提交周报（subOrgId 服务端取本人所属组织） */
    @Idempotent(bizType = "PERFORMANCE_SUBMIT")
    @AuditLog(bizType = "PERFORMANCE_SUBMIT", label = "提交周报",
            summary = "'提交周报：第 ' + #dto.weekNo + ' 周'")
    @PostMapping("/weeklies")
    public Result<java.util.Map<String, Long>> submit(@Valid @RequestBody WeeklySubmitDTO dto) {
        Long id = weeklyService.submit(dto);
        return Result.ok(java.util.Map.of("id", id));
    }

    /** PF03 驳回后重新提交（仅本人且 REJECTED） */
    @AuditLog(bizType = "PERFORMANCE_RESUBMIT", label = "重新提交周报", summary = "'重提周报：' + #id")
    @PutMapping("/weeklies/{id}")
    public Result<Void> resubmit(@PathVariable("id") Long id,
                                 @Valid @RequestBody WeeklyResubmitDTO dto) {
        weeklyService.resubmit(id, dto);
        return Result.ok();
    }

    /** PF04 周报详情（本人或管理员；SCORED 后含维度明细） */
    @GetMapping("/weeklies/{id}")
    public Result<WeeklyDetailVO> detail(@PathVariable("id") Long id) {
        return Result.ok(weeklyService.detail(id));
    }

    /** PF06 周报列表（审核工作台，仅管理员） */
    @com.viomanager.common.auth.RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping("/weeklies")
    public Result<List<WeeklyItemVO>> adminList(
            @RequestParam(value = "semesterId", required = false) Long semesterId,
            @RequestParam(value = "weekNo", required = false) Integer weekNo,
            @RequestParam(value = "subOrgId", required = false) Long subOrgId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "memberKeyword", required = false) String memberKeyword) {
        return Result.ok(weeklyService.adminList(semesterId, weekNo, subOrgId, status, memberKeyword));
    }

    /** PF07 打分（维度快照 + 事务内重算汇总排名） */
    @com.viomanager.common.auth.RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "PERFORMANCE_SCORE")
    @AuditLog(bizType = "PERFORMANCE_SCORE", label = "绩效打分", summary = "'打分周报：' + #id")
    @PostMapping("/weeklies/{id}/score")
    public Result<Void> score(@PathVariable("id") Long id,
                              @Valid @RequestBody WeeklyScoreDTO dto) {
        scoreService.score(id, dto);
        return Result.ok();
    }

    /** PF08 驳回（reason 必填，不删记录） */
    @com.viomanager.common.auth.RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @AuditLog(bizType = "PERFORMANCE_REJECT", label = "驳回周报", summary = "'驳回周报：' + #id")
    @PostMapping("/weeklies/{id}/reject")
    public Result<Void> reject(@PathVariable("id") Long id,
                               @Valid @RequestBody com.viomanager.performance.dto.WeeklyRejectDTO dto) {
        scoreService.reject(id, dto.getReason());
        return Result.ok();
    }
}
