package com.viomanager.material.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.material.entity.MaterialRequirement;
import com.viomanager.material.entity.MaterialTask;
import com.viomanager.material.mapper.MaterialRequirementMapper;
import com.viomanager.material.mapper.MaterialSubmissionMapper;
import com.viomanager.material.mapper.MaterialTaskMapper;
import com.viomanager.material.service.MaterialCompletionService;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 队伍材料齐全率重算实现：MS02 提交 / 覆盖成功后在同一事务内被调用。
 * 口径（C 线接口清单 §3 MS02）：
 *  - 分母 = 该比赛材料任务下全部未删要求（含选传项）；
 *  - 分子 = 其中有 is_current=1 提交的要求数；
 *  - completeRate 是 0–100 的整数（四舍五入），不是小数；
 *  - materialStatus：无任何提交 NOT_STARTED / 缺必传项 PENDING / 必传全齐 SUBMITTED。
 *
 * team 表读写集中在本类 loadTeam / updateTeamMaterial 两个私有方法（B 线未建包前的过渡方案）。
 */
@Service
public class MaterialCompletionServiceImpl implements MaterialCompletionService {

    @Resource
    private MaterialTaskMapper taskMapper;

    @Resource
    private MaterialRequirementMapper requirementMapper;

    @Resource
    private MaterialSubmissionMapper submissionMapper;

    @Resource
    private NamedParameterJdbcTemplate namedTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recompute(Long teamId) {
        //1.队伍不存在直接返回（重算是幂等收敛动作，不抛错）
        TeamRow team = loadTeam(teamId);
        if (team == null) {
            return;
        }
        //2.该比赛的材料任务：一赛一任务是建任务的强规则，selectList 容错取第一条
        List<MaterialTask> tasks = taskMapper.selectList(
                new QueryWrapper<MaterialTask>().eq("contest_id", team.contestId()));
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
        //7.回写 team 两列
        updateTeamMaterial(teamId, materialStatus, completeRate);
    }

    // TODO(联调) B 线提供 ITeamService 后，改成调它的 getById(teamId)
    private TeamRow loadTeam(Long teamId) {
        String sql = "SELECT id, contest_id, semester_id, org_id, leader_member_id, status, "
                + "material_status, material_complete_rate "
                + "FROM team WHERE id = :teamId AND deleted = 0";
        return namedTemplate.query(sql, Map.of("teamId", teamId), rs -> {
            if (!rs.next()) {
                return null;
            }
            return new TeamRow(
                    rs.getObject("id", Long.class),
                    rs.getObject("contest_id", Long.class),
                    rs.getObject("semester_id", Long.class),
                    rs.getObject("org_id", Long.class),
                    rs.getObject("leader_member_id", Long.class),
                    rs.getString("status"),
                    rs.getString("material_status"),
                    rs.getBigDecimal("material_complete_rate"));
        });
    }

    // TODO(联调) B 线提供 ITeamService 后，改成调它的更新接口
    private void updateTeamMaterial(Long teamId, String materialStatus, int completeRate) {
        String sql = "UPDATE team SET material_status = :status, material_complete_rate = :rate "
                + "WHERE id = :teamId AND deleted = 0";
        namedTemplate.update(sql,
                Map.of("status", materialStatus, "rate", completeRate, "teamId", teamId));
    }

    /** team 行只读快照（B 线实体未建前的过渡载体，本类只消费 contestId） */
    private record TeamRow(Long id, Long contestId, Long semesterId, Long orgId, Long leaderMemberId,
                           String status, String materialStatus, BigDecimal completeRate) {
    }
}
