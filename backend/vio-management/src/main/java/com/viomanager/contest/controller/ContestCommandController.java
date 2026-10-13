package com.viomanager.contest.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.Result;
import com.viomanager.contest.dto.ContestCreateDTO;
import com.viomanager.contest.dto.ContestStatusDTO;
import com.viomanager.contest.dto.TrackCreateDTO;
import com.viomanager.contest.service.IContestCommandService;
import com.viomanager.contest.vo.ContestDetailVO;
import com.viomanager.contest.vo.TrackVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 比赛写接口（C03–C09）。
 * 比赛管理限管理端：@RequireRole SUPER_ADMIN / DEPT_ADMIN。
 * 比赛写操作不产生审批单（无回调 Handler），即时生效。
 * service 层返回领域对象（非 Result），控制器用 Result.ok() 包装。
 * 状态机：DRAFT → PUBLISHED → REGISTERING → RUNNING → FINISHED → ARCHIVED。
 */
@RestController
@RequestMapping("/api/contests")
public class ContestCommandController {

    @Resource
    private IContestCommandService commandService;

    /** C03 创建（sourceType=MANUAL，初始 status=DRAFT） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "CONTEST_CREATE")
    @AuditLog(bizType = "CONTEST_CREATE", label = "创建比赛",
            summary = "'创建比赛：' + #dto.name")
    @PostMapping
    public Result<ContestDetailVO> create(@Valid @RequestBody ContestCreateDTO dto) {
        return Result.ok(commandService.createContest(dto));
    }

    /** C04 编辑（非 ARCHIVED 才可编辑） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "CONTEST_UPDATE")
    @AuditLog(bizType = "CONTEST_UPDATE", label = "编辑比赛",
            summary = "'编辑比赛：' + #id")
    @PutMapping("/{id}")
    public Result<ContestDetailVO> update(@PathVariable("id") Long id,
                                          @Valid @RequestBody ContestCreateDTO dto) {
        return Result.ok(commandService.updateContest(id, dto));
    }

    /** C05 删除（仅 DRAFT 状态可删） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "CONTEST_DELETE")
    @AuditLog(bizType = "CONTEST_DELETE", label = "删除比赛",
            summary = "'删除比赛：' + #id")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        commandService.deleteContest(id);
        return Result.ok();
    }

    /** C06 状态流转（4011 不可跳转；DRAFT→PUBLISHED 发布前检查 4001 逐项指出缺项） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "CONTEST_STATUS")
    @AuditLog(bizType = "CONTEST_STATUS", label = "状态流转",
            summary = "'比赛状态流转：' + #id + '→' + #dto.to")
    @PostMapping("/{id}/status")
    public Result<Void> transitionStatus(@PathVariable("id") Long id,
                                         @Valid @RequestBody ContestStatusDTO dto) {
        commandService.transitionStatus(id, dto.getTo());
        return Result.ok();
    }

    /** C07 添加赛道 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "CONTEST_TRACK_CREATE")
    @AuditLog(bizType = "CONTEST_TRACK_CREATE", label = "添加赛道",
            summary = "'添加赛道：比赛' + #id")
    @PostMapping("/{id}/tracks")
    public Result<TrackVO> createTrack(@PathVariable("id") Long id,
                                       @Valid @RequestBody TrackCreateDTO dto) {
        return Result.ok(commandService.createTrack(id, dto));
    }

    /** C08 编辑赛道（含赛道级规则覆盖） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "CONTEST_TRACK_UPDATE")
    @AuditLog(bizType = "CONTEST_TRACK_UPDATE", label = "编辑赛道",
            summary = "'编辑赛道：' + #trackId")
    @PutMapping("/{id}/tracks/{trackId}")
    public Result<TrackVO> updateTrack(@PathVariable("id") Long id,
                                       @PathVariable("trackId") Long trackId,
                                       @Valid @RequestBody TrackCreateDTO dto) {
        return Result.ok(commandService.updateTrack(id, trackId, dto));
    }

    /** C09 删除赛道（有赛队挂载该赛道时拒绝） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @Idempotent(bizType = "CONTEST_TRACK_DELETE")
    @AuditLog(bizType = "CONTEST_TRACK_DELETE", label = "删除赛道",
            summary = "'删除赛道：' + #trackId")
    @DeleteMapping("/{id}/tracks/{trackId}")
    public Result<Void> deleteTrack(@PathVariable("id") Long id,
                                    @PathVariable("trackId") Long trackId) {
        commandService.deleteTrack(id, trackId);
        return Result.ok();
    }
}
