package com.viomanager.achievement.controller;

import com.viomanager.achievement.service.AchievementService;
import com.viomanager.achievement.vo.MyAchievementVO;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.common.result.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端成果接口（A08 我的成果）。
 * @DataScope(SELF)：本接口数据范围 = 业务 SQL 自带"本人"条件（按当前人查在册队伍再聚合成果），
 * 学生可见性声明——C 线相关 Mapper 均已声明 SELF，这里再标一次防骨架策略收紧时漏网。
 */
@RestController
@RequestMapping("/api/achievements")
public class MyAchievementController {

    @Resource
    private AchievementService achievementService;

    /** A08 我的成果：参赛记录 + 获奖记录从成员关系与成果动态聚合，不读冗余表 */
    @RequireRole("STUDENT")
    @DataScope(scope = DataScope.Scope.SELF)
    @GetMapping("/my/contributions")
    public Result<MyAchievementVO> myContributions() {
        return achievementService.myContributions();
    }
}
