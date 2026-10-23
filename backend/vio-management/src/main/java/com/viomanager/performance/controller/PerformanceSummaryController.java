package com.viomanager.performance.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.result.Result;
import com.viomanager.performance.service.PerformanceSummaryService;
import com.viomanager.performance.vo.MyPerfSummaryVO;
import com.viomanager.performance.vo.SummaryRowVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 绩效汇总接口（PF05/PF09/PF10，接口清单 §3.2）。
 * PF05 学生端"本周绩效"卡片同源；PF09/PF10 仅管理员（部门管理员本子树）。
 */
@RestController
@RequestMapping("/api/performance")
public class PerformanceSummaryController {

    @Resource
    private PerformanceSummaryService summaryService;

    /** PF05 我的绩效汇总（当前学期，服务端取 ACTIVE 行，免参） */
    @GetMapping("/my/summary")
    public Result<MyPerfSummaryVO> mySummary() {
        return Result.ok(summaryService.mySummary());
    }

    /** PF09 汇总列表（评优参考，按 totalScore 降序） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping("/summaries")
    public Result<List<SummaryRowVO>> summaries(
            @RequestParam(value = "semesterId", required = false) Long semesterId,
            @RequestParam(value = "subOrgId", required = false) Long subOrgId,
            @RequestParam(value = "memberKeyword", required = false) String memberKeyword) {
        return Result.ok(summaryService.summaries(semesterId, subOrgId, memberKeyword));
    }

    /** PF10 导出 Excel（groupBy = MEMBER 按人 / ORG 按子组织，xlsx 流；semesterId 免参回退当前激活学期，与 PF09 同口径） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @AuditLog(bizType = "PERFORMANCE_EXPORT", label = "导出绩效汇总",
            summary = "'导出绩效：学期' + #semesterId + ' 分组' + #groupBy")
    @GetMapping("/export")
    public void export(@RequestParam(value = "semesterId", required = false) Long semesterId,
                       @RequestParam(value = "subOrgId", required = false) Long subOrgId,
                       @RequestParam(value = "groupBy", defaultValue = "MEMBER") String groupBy,
                       HttpServletResponse response) {
        summaryService.export(semesterId, subOrgId, groupBy, response);
    }
}
