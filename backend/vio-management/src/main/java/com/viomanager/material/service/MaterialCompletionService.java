package com.viomanager.material.service;

import com.viomanager.common.result.Result;
import com.viomanager.material.vo.MyTeamMaterialVO;
import com.viomanager.material.vo.TeamMaterialRowVO;
import java.util.List;

/**
 * 队伍材料完成度服务：齐全率重算（MS02 提交后触发）+ 两张完成度视图
 * （MS01 管理端队伍概览 / MS06 学生端我的材料）。
 */
public interface MaterialCompletionService {

    /**
     * 重算某队的齐全率与材料状态。幂等收敛动作，不做强校验——
     * 队伍 / 材料任务 / 要求任一不存在时静默返回，不抛异常。
     */
    void recompute(Long teamId);

    /** 管理端队伍提交概览（MS01）：逐要求的当前版本状态 + 齐全率与待补文案 */
    Result<TeamMaterialRowVO> teamOverview(Long teamId);

    /** 学生端我的材料（MS06）：我在册且 ACTIVE 的队伍逐队返回，比赛未配任务的队伍跳过 */
    Result<List<MyTeamMaterialVO>> myMaterials();
}
