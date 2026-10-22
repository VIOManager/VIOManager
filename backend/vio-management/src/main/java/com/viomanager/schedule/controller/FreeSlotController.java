package com.viomanager.schedule.controller;

import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.result.Result;
import com.viomanager.schedule.service.FreeSlotService;
import com.viomanager.schedule.vo.FreeMemberVO;
import com.viomanager.schedule.vo.FreeSlotCellVO;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 空课查询接口（SC06/SC07，接口清单 §3）。PRD 明确：仅超管与部门管理员可用。
 * AI 助手的"空课信息"工具调用走本组接口同源逻辑，权限随当前用户（§3.15）。
 */
@RestController
@RequestMapping("/api/schedules/free-slots")
public class FreeSlotController {

    @Resource
    private FreeSlotService freeSlotService;

    /** SC06 整周 7×5 概览矩阵（每格 freeCount + 年级分布） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping("/matrix")
    public Result<List<FreeSlotCellVO>> matrix(@RequestParam("semesterId") Long semesterId,
                                               @RequestParam("weekNo") Integer weekNo) {
        return Result.ok(freeSlotService.matrix(semesterId, weekNo));
    }

    /** SC07 单格空课名单（含姓名与班级，年级前端分组） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping
    public Result<List<FreeMemberVO>> members(@RequestParam("semesterId") Long semesterId,
                                              @RequestParam("weekNo") Integer weekNo,
                                              @RequestParam("weekday") Integer weekday,
                                              @RequestParam("slotIndex") Integer slotIndex) {
        return Result.ok(freeSlotService.members(semesterId, weekNo, weekday, slotIndex));
    }
}
