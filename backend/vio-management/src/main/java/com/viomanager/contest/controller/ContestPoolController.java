package com.viomanager.contest.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.Result;
import com.viomanager.contest.dto.ContestCreateDTO;
import com.viomanager.contest.service.IContestPoolService;
import com.viomanager.contest.vo.ContestDetailVO;
import com.viomanager.contest.vo.PoolItemVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 外部比赛待处理池接口（P01–P03）。
 * 仅管理端可见：@RequireRole SUPER_ADMIN / DEPT_ADMIN。
 * PENDING 状态可确认（预填创建 contest + 回填 confirmed_contest_id）或忽略。
 * 独立 @RequestMapping("/api/contests/pool")，字面路径优先于 ContestQueryController 的 {id}。
 * service 层返回 Result，控制器直接透传。
 */
@RestController
@RequestMapping("/api/contests/pool")
public class ContestPoolController {

    @Resource
    private IContestPoolService poolService;

    /** P01 待处理列表（筛选 status） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping
    public Result<List<PoolItemVO>> list(
            @RequestParam(value = "status", required = false) String status) {
        return poolService.listPool(status);
    }

    /** P02 确认为正式比赛（按解析内容预填创建 contest，confirmed_contest_id 回填） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "CONTEST_POOL_CONFIRM")
    @AuditLog(bizType = "CONTEST_POOL_CONFIRM", label = "确认比赛",
            summary = "'确认待处理池：' + #id")
    @PostMapping("/{id}/confirm")
    public Result<ContestDetailVO> confirm(@PathVariable("id") Long id,
                                           @Valid @RequestBody ContestCreateDTO dto) {
        return poolService.confirmPool(id, dto);
    }

    /** P03 忽略 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "CONTEST_POOL_IGNORE")
    @AuditLog(bizType = "CONTEST_POOL_IGNORE", label = "忽略比赛",
            summary = "'忽略待处理池：' + #id")
    @PostMapping("/{id}/ignore")
    public Result<Void> ignore(@PathVariable("id") Long id) {
        return poolService.ignorePool(id);
    }
}
