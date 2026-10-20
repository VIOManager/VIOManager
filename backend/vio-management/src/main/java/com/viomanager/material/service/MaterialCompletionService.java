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

    /**
     * 按比赛批量重算该比赛下全部队伍的齐全率与材料状态。
     * 材料要求按比赛共享（无 team_id 列），要求集变动（含 §5 联动追加佐证项）会影响
     * 该比赛所有队伍的分母，故要求侧与联动侧必须用本方法整场收敛，不能只算单队。
     */
    void recomputeByContest(Long contestId);

    /** 管理端队伍提交概览（MS01）：逐要求的当前版本状态 + 齐全率与待补文案 */
    Result<TeamMaterialRowVO> teamOverview(Long teamId);

    /** 学生端我的材料（MS06）：我在册且 ACTIVE 的队伍逐队返回，比赛未配任务的队伍跳过 */
    Result<List<MyTeamMaterialVO>> myMaterials();
}
