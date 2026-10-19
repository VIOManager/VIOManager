package com.viomanager.material.controller;

import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.common.result.Result;
import com.viomanager.material.service.MaterialCompletionService;
import com.viomanager.material.vo.MyTeamMaterialVO;
import com.viomanager.material.vo.TeamMaterialRowVO;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 材料概览接口：MS01 管理端队伍提交概览 + MS06 学生端我的材料，口径以 C 线接口清单 §3 为准。
 * 查询逻辑落在 MaterialCompletionService（概览即完成度视图，与重算同域）。
 */
@RestController
@RequestMapping("/api/materials")
public class MaterialOverviewController {

    @Resource
    private MaterialCompletionService completionService;

    /** MS01 队伍提交概览：队伍信息 + 齐全率/待补文案 + 逐要求当前版本状态（单个对象，非数组） */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN"})
    @GetMapping("/teams/{teamId}/overview")
    public Result<TeamMaterialRowVO> teamOverview(@PathVariable("teamId") Long teamId) {
        return completionService.teamOverview(teamId);
    }

    /**
     * MS06 我的材料：学生视角逐队返回任务与逐要求状态，无队伍返回空数组。
     * @DataScope(SELF)：本接口数据范围 = 业务 SQL 自带"本人"条件（按当前人查在册队伍），
     * 学生可见性声明——C 线三张 Mapper 接口均已声明 SELF，这里再标一次防骨架策略收紧时漏网。
     */
    @RequireRole("STUDENT")
    @DataScope(scope = DataScope.Scope.SELF)
    @GetMapping("/my")
    public Result<List<MyTeamMaterialVO>> myMaterials() {
        return completionService.myMaterials();
    }
}
