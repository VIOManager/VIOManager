package com.viomanager.contest.controller;

import com.viomanager.common.result.Result;
import com.viomanager.contest.service.IContestQueryService;
import com.viomanager.contest.vo.ContestDetailVO;
import com.viomanager.contest.vo.ContestListItemVO;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 比赛查询接口（C01 列表 / C02 详情）。
 * 无 @RequireRole：学生可查询比赛。
 * 数据范围在 service 层按角色判定：超管全部 / 部门管理员本组织子树 / 学生仅 PUBLISHED 及之后状态。
 * /api/contests/pool 路由由 ContestPoolController 独立处理（字面路径优先于 {id}）。
 */
@RestController
@RequestMapping("/api/contests")
public class ContestQueryController {

    @Resource
    private IContestQueryService queryService;

    /** C01 列表：筛选 semesterId/status；学生仅可见 PUBLISHED 及之后 */
    @GetMapping
    public Result<List<ContestListItemVO>> list(
            @RequestParam(value = "semesterId", required = false) Long semesterId,
            @RequestParam(value = "status", required = false) String status) {
        return queryService.listContests(semesterId, status);
    }

    /** C02 详情：返回合并后生效规则 rules + 每项来源标注 + tracks 列表 */
    @GetMapping("/{id}")
    public Result<ContestDetailVO> detail(@PathVariable("id") Long id) {
        return queryService.getContestDetail(id);
    }
}
