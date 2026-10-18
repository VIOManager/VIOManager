package com.viomanager.semester.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.Result;
import com.viomanager.semester.dto.SwitchTaskCreateDTO;
import com.viomanager.semester.service.SwitchTaskService;
import com.viomanager.semester.vo.SwitchTaskVO;
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
 * 学期切换任务接口（A 线契约 S05-S08）：路径 /api/semesters/switch-tasks。
 * 全部仅超管。S08 执行是全平台最重的写操作：幂等键防连点、乐观锁防并发、维护锁防窗口期写入。
 */
@RestController
@RequestMapping("/api/semesters/switch-tasks")
public class SwitchTaskController {

    @Resource
    private SwitchTaskService switchTaskService;

    /** S06 任务列表（含快照与执行结果） */
    @RequireRole({"SUPER_ADMIN"})
    @GetMapping
    public Result<List<SwitchTaskVO>> list() {
        return Result.ok(switchTaskService.list());
    }

    /** S05 发起切换（超管密码二次确认，30 分钟待执行窗口） */
    @RequireRole({"SUPER_ADMIN"})
    @Idempotent(bizType = "SEMESTER_SWITCH_CREATE")
    @AuditLog(bizType = "SEMESTER_SWITCH", label = "发起学期切换")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody SwitchTaskCreateDTO dto) {
        return Result.ok(switchTaskService.create(dto));
    }

    /** S07 窗口内取消 */
    @RequireRole({"SUPER_ADMIN"})
    @Idempotent(bizType = "SEMESTER_SWITCH_CANCEL")
    @AuditLog(bizType = "SEMESTER_SWITCH", label = "取消切换任务")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable("id") Long id) {
        switchTaskService.cancel(id);
        return Result.ok();
    }

    /** S08 执行（乐观锁占位 → 维护锁 → 引擎搬运 → 收尾；清单 §5 归骨架的执行引擎） */
    @RequireRole({"SUPER_ADMIN"})
    @Idempotent(bizType = "SEMESTER_SWITCH_EXECUTE")
    @AuditLog(bizType = "SEMESTER_SWITCH", label = "执行学期切换")
    @PostMapping("/{id}/execute")
    public Result<Void> execute(@PathVariable("id") Long id) {
        switchTaskService.execute(id);
        return Result.ok();
    }
}
