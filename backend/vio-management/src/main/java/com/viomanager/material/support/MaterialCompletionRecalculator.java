package com.viomanager.material.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.material.entity.MaterialRequirement;
import com.viomanager.material.entity.MaterialTask;
import com.viomanager.material.mapper.MaterialRequirementMapper;
import com.viomanager.material.mapper.MaterialSubmissionMapper;
import com.viomanager.material.mapper.MaterialTaskMapper;
import com.viomanager.material.support.MaterialTeamQuerySupport.TeamRow;
import jakarta.annotation.Resource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 齐全率重算器（C 线接口清单 §3 MS02 口径）：分母为任务下全部未删要求（含选传），
 * 分子为有 is_current=1 提交的要求数，completeRate 取 0–100 整数。
 *
 * <p>为什么单独成一个 Bean：重算有三个触发侧——材料提交（MS02/MS03）、
 * 材料要求增删改（MR01–MR03）、成果 §5 联动。其中要求侧的服务本身被
 * {@code MaterialCompletionServiceImpl} 依赖（MS06 复用它的 listByTask），
 * 若反向注入完成度服务会形成循环依赖（Spring Boot 3 默认禁止），故把重算逻辑
 * 收敛到这里，供三条触发侧独立注入。
 *
 * <p>⚠️ 材料要求是<strong>按比赛共享</strong>的（material_requirement 无 team_id 列，只挂 task_id）：
 * 一条要求的新增/删除/必传标记变化，会改变该比赛<strong>所有队伍</strong>的分母。
 * 因此要求侧必须用 {@link #recomputeByContest(Long)} 批量收敛，不能只算单队。
 */
@Component
public class MaterialCompletionRecalculator {

    @Resource
    private MaterialTaskMapper taskMapper;

    @Resource
    private MaterialRequirementMapper requirementMapper;

    @Resource
    private MaterialSubmissionMapper submissionMapper;

    @Resource
    private MaterialTeamQuerySupport teamQuery;

    /**
     * 重算单队齐全率与材料状态。幂等收敛动作，不做强校验——
     * 队伍 / 材料任务 / 要求任一不存在时静默返回，不抛异常
     * （材料提交主流程不应因队伍恰好被解散或比赛未配任务而整体失败）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void recompute(Long teamId) {
        //1.队伍不存在直接返回（重算是幂等收敛动作，不抛错）
        TeamRow team = teamQuery.loadTeam(teamId);
        if (team == null) {
            return;
        }
        //2.该比赛的材料任务：一赛一任务是建任务的强规则；orderByAsc("id") 定序，容错取第一条不再漂移
        List<MaterialTask> tasks = taskMapper.selectList(
                new QueryWrapper<MaterialTask>().eq("contest_id", team.getContestId())
                        .orderByAsc("id"));
        if (tasks.isEmpty()) {
            return;
        }
        MaterialTask task = tasks.get(0);
        //3.任务下全部未删要求（含选传：选传项进分母，不计入缺项）
        List<MaterialRequirement> reqs = requirementMapper.selectList(
                new QueryWrapper<MaterialRequirement>().eq("task_id", task.getId()));
        if (reqs.isEmpty()) {
            return;
        }
        //4.统计：有 is_current=1 提交的算已交；没交且必传的计缺项
        Set<Long> submittedReqIds = new HashSet<>(
                submissionMapper.selectCurrentRequirementIds(teamId));
        int submitted = 0;
        int requiredMissing = 0;
        for (MaterialRequirement req : reqs) {
            if (submittedReqIds.contains(req.getId())) {
                submitted++;
            } else if (req.getRequiredFlag() != null && req.getRequiredFlag() == 1) {
                requiredMissing++;
            }
        }
        //5.齐全率：0–100 整数，分母是全部要求数（含选传）
        int completeRate = Math.round(submitted * 100.0f / reqs.size());
        //6.队伍材料状态
        String materialStatus = submitted == 0 ? "NOT_STARTED"
                : requiredMissing > 0 ? "PENDING" : "SUBMITTED";
        //7.回写 team 两列（经 MaterialTeamQuerySupport 走 B 线口子）
        teamQuery.updateTeamMaterial(teamId, materialStatus, completeRate);
    }

    /**
     * 按比赛批量重算：材料要求按比赛共享，要求集变动会影响该比赛下所有队伍的齐全率分母，
     * 必须整场收敛而不是只算触发的那一支队伍。
     *
     * <p>逐个队伍串行收敛（单队重算内部已静默容错，不会因某支队伍异常中断整批）；
     * 外层事务由本方法开启，内层 {@link #recompute(Long)} 为同类自调用，
     * 事务不重复开启、随外层一起提交或回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public void recomputeByContest(Long contestId) {
        if (contestId == null) {
            return;
        }
        for (Long teamId : teamQuery.loadTeamIdsByContest(contestId)) {
            recompute(teamId);
        }
    }
}
