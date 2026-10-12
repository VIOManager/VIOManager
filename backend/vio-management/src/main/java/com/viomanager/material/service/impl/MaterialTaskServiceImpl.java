package com.viomanager.material.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.MaterialErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.material.dto.MaterialTaskCreateDTO;
import com.viomanager.material.dto.MaterialTaskUpdateDTO;
import com.viomanager.material.entity.MaterialRequirement;
import com.viomanager.material.entity.MaterialTask;
import com.viomanager.material.mapper.MaterialRequirementMapper;
import com.viomanager.material.mapper.MaterialTaskMapper;
import com.viomanager.material.service.MaterialTaskService;
import com.viomanager.material.vo.MaterialTaskVO;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 材料任务服务实现（MT01–MT04），口径以 C 线接口清单 §2 为准。
 *
 * 跨模块取数约定：contest / team 是 B 线的表，本模块不建其实体与 Mapper，
 * 一律用 NamedParameterJdbcTemplate 手写 SQL（骨架 ApprovalActionServiceImpl 先例）；
 * C 线自己的表走 MyBatis-Plus Mapper，其 @DataScope(SELF) 声明 = 放行不注入条件。
 * 手写 SQL 一律显式带 deleted = 0——@TableLogic 只对 mapper 自动方法生效（见 BaseEntity 注释）。
 *
 * ⚠️ 同一比赛唯一任务无数据库兜底（material_task 无 UNIQUE KEY，见其实体注释）：
 * service 层先查后建；并发双击窗口内理论上可插入两条，彻底封死需补 V2 唯一索引（组长决策）。
 */
@Service
public class MaterialTaskServiceImpl implements MaterialTaskService {

    /** 队伍材料齐全率达标线（百分数）：team.material_complete_rate >= 100 记"已交齐" */
    private static final int COMPLETE_RATE_FULL = 100;

    @Resource
    private MaterialTaskMapper taskMapper;

    @Resource
    private MaterialRequirementMapper requirementMapper;

    @Resource
    private NamedParameterJdbcTemplate namedTemplate;

    @Override
    public Result<List<MaterialTaskVO>> list(Long contestId) {
        QueryWrapper<MaterialTask> wrapper = new QueryWrapper<>();
        if (contestId != null) {
            wrapper.eq("contest_id", contestId);
        }
        wrapper.orderByDesc("created_at");
        List<MaterialTask> tasks = taskMapper.selectList(wrapper);
        if (tasks.isEmpty()) {
            return Result.ok(List.of());
        }
        //三块关联数据各一次批量查，避免列表页 N+1
        List<Long> contestIds = tasks.stream().map(MaterialTask::getContestId).distinct().toList();
        List<Long> taskIds = tasks.stream().map(MaterialTask::getId).toList();
        Map<Long, ContestRow> contests = loadContests(contestIds);
        Map<Long, Integer> requirementCounts = countRequirementsByTask(taskIds);
        Map<Long, TeamStat> teamStats = countTeamStats(contestIds);
        List<MaterialTaskVO> voList = new ArrayList<>(tasks.size());
        for (MaterialTask task : tasks) {
            voList.add(toVO(task, contests, requirementCounts, teamStats));
        }
        return Result.ok(voList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<MaterialTaskVO> create(MaterialTaskCreateDTO dto) {
        //1.同一比赛只能建一个任务：先查未删记录，命中即拒（并发说明见类注释）
        Long existed = taskMapper.selectCount(
                new QueryWrapper<MaterialTask>().eq("contest_id", dto.getContestId()));
        if (existed != null && existed > 0) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "该比赛已配置材料任务，请直接维护其要求");
        }
        //2.比赛必须存在，semesterId / orgId 从 contest 行带出（B 线表，手写 SQL）
        Map<Long, ContestRow> contests = loadContests(List.of(dto.getContestId()));
        ContestRow contest = contests.get(dto.getContestId());
        if (contest == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "所选比赛不存在");
        }
        //3.落库：status 默认 ACTIVE；审计四字段由 AuditMetaObjectHandler 自动填，不手工设置
        MaterialTask task = new MaterialTask();
        task.setContestId(dto.getContestId());
        task.setSemesterId(contest.semesterId());
        task.setOrgId(contest.orgId());
        task.setName(dto.getName());
        task.setDescription(dto.getDescription());
        task.setDeadline(dto.getDeadline());
        task.setStatus("ACTIVE");
        taskMapper.insert(task);
        //4.出参：新任务要求条数为 0；队伍两数按该比赛当前数据现算
        Map<Long, TeamStat> teamStats = countTeamStats(List.of(task.getContestId()));
        return Result.ok(toVO(task, contests, Map.of(), teamStats));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MaterialTaskUpdateDTO dto) {
        //1.任务必须存在（selectById 经 @TableLogic 自动滤掉已删记录）
        MaterialTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "材料任务不存在");
        }
        //2.只改三字段：set 显式写值（含 null），PUT 全量替换，deadline 传空 = 恢复不限期
        UpdateWrapper<MaterialTask> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id)
                .set("name", dto.getName())
                .set("description", dto.getDescription())
                .set("deadline", dto.getDeadline());
        if (taskMapper.update(null, wrapper) == 0) {
            //并发兜底：查出后任务恰好被他人删除
            throw new BizException(CommonErrorCode.NOT_FOUND, "材料任务不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        //1.任务必须存在
        MaterialTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "材料任务不存在");
        }
        //2.任一要求已有提交记录 → 5006 拒绝：子查询圈定该任务下未删的要求，再看未删提交
        if (countTaskSubmissions(id) > 0) {
            throw new BizException(MaterialErrorCode.TASK_HAS_SUBMISSION);
        }
        //3.级联逻辑删除任务下所有未删要求（@TableLogic 把 delete 转成 UPDATE deleted=1，不物理删）
        requirementMapper.delete(new QueryWrapper<MaterialRequirement>().eq("task_id", id));
        //4.最后删任务本身，同为逻辑删除
        taskMapper.deleteById(id);
    }

    /** 组装出参：聚合数据查不到（比赛被删 / 无要求 / 无队伍）按 0 或 null 兜底 */
    private MaterialTaskVO toVO(MaterialTask task, Map<Long, ContestRow> contests,
                                Map<Long, Integer> requirementCounts, Map<Long, TeamStat> teamStats) {
        MaterialTaskVO vo = new MaterialTaskVO();
        vo.setId(task.getId());
        vo.setContestId(task.getContestId());
        ContestRow contest = contests.get(task.getContestId());
        vo.setContestName(contest == null ? null : contest.name());
        vo.setName(task.getName());
        vo.setDescription(task.getDescription());
        vo.setDeadline(task.getDeadline());
        vo.setStatus(task.getStatus());
        vo.setRequirementCount(requirementCounts.getOrDefault(task.getId(), 0));
        TeamStat stat = teamStats.getOrDefault(task.getContestId(), TeamStat.EMPTY);
        vo.setTeamTotal(stat.total());
        vo.setTeamComplete(stat.complete());
        vo.setCreatedAt(task.getCreatedAt());
        return vo;
    }

    /** 批量查比赛（B 线表不建实体，只取本模块要用的三列）：contestId → 行快照 */
    private Map<Long, ContestRow> loadContests(Collection<Long> contestIds) {
        String sql = "SELECT id, name, semester_id, org_id FROM contest "
                + "WHERE id IN (:ids) AND deleted = 0";
        return namedTemplate.query(sql, Map.of("ids", contestIds), rs -> {
            Map<Long, ContestRow> map = new HashMap<>();
            while (rs.next()) {
                map.put(rs.getLong("id"), new ContestRow(rs.getString("name"),
                        rs.getObject("semester_id", Long.class),
                        rs.getObject("org_id", Long.class)));
            }
            return map;
        });
    }

    /** 批量统计各任务下未删的要求条数：taskId → count */
    private Map<Long, Integer> countRequirementsByTask(Collection<Long> taskIds) {
        String sql = "SELECT task_id, COUNT(*) AS cnt FROM material_requirement "
                + "WHERE task_id IN (:ids) AND deleted = 0 GROUP BY task_id";
        return namedTemplate.query(sql, Map.of("ids", taskIds), rs -> {
            Map<Long, Integer> map = new HashMap<>();
            while (rs.next()) {
                map.put(rs.getLong("task_id"), (int) rs.getLong("cnt"));
            }
            return map;
        });
    }

    /**
     * 批量统计各比赛的队伍两数：contestId → (total, complete)。
     * total = ACTIVE 队伍数；complete = 其中材料齐全率 >= 100 的队伍数（C 线回写列）。
     */
    private Map<Long, TeamStat> countTeamStats(Collection<Long> contestIds) {
        String sql = "SELECT contest_id, COUNT(*) AS total, "
                + "SUM(CASE WHEN material_complete_rate >= " + COMPLETE_RATE_FULL
                + " THEN 1 ELSE 0 END) AS complete "
                + "FROM team WHERE contest_id IN (:ids) AND status = 'ACTIVE' AND deleted = 0 "
                + "GROUP BY contest_id";
        return namedTemplate.query(sql, Map.of("ids", contestIds), rs -> {
            Map<Long, TeamStat> map = new HashMap<>();
            while (rs.next()) {
                map.put(rs.getLong("contest_id"),
                        new TeamStat((int) rs.getLong("total"), (int) rs.getLong("complete")));
            }
            return map;
        });
    }

    /** 该任务下任一未删要求是否存在未删提交记录（MT04 的 5006 前置检查） */
    private long countTaskSubmissions(Long taskId) {
        String sql = "SELECT COUNT(*) FROM material_submission WHERE deleted = 0 "
                + "AND requirement_id IN (SELECT id FROM material_requirement "
                + "WHERE task_id = :taskId AND deleted = 0)";
        Long count = namedTemplate.queryForObject(sql, Map.of("taskId", taskId), Long.class);
        return count == null ? 0L : count;
    }

    /** contest 行的最小快照：name 比赛名 / semesterId 建任务带出 / orgId 同步归属组织 */
    private record ContestRow(String name, Long semesterId, Long orgId) {
    }

    /** 比赛维度的队伍统计：total = ACTIVE 队伍数，complete = 齐全率达标数 */
    private record TeamStat(int total, int complete) {

        static final TeamStat EMPTY = new TeamStat(0, 0);
    }
}
