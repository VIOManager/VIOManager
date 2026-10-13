package com.viomanager.material.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.MaterialTypeEnum;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.material.entity.MaterialRequirement;
import com.viomanager.material.entity.MaterialSubmission;
import com.viomanager.material.entity.MaterialTask;
import com.viomanager.material.mapper.MaterialRequirementMapper;
import com.viomanager.material.mapper.MaterialSubmissionMapper;
import com.viomanager.material.mapper.MaterialTaskMapper;
import com.viomanager.material.service.MaterialCompletionService;
import com.viomanager.material.service.MaterialRequirementService;
import com.viomanager.material.vo.MaterialRequirementVO;
import com.viomanager.material.vo.MaterialSubmitInfoVO;
import com.viomanager.material.vo.MyTeamMaterialVO;
import com.viomanager.material.vo.TeamMaterialRowVO;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 队伍材料完成度实现：齐全率重算（recompute）+ MS01 管理端队伍概览 + MS06 学生端我的材料。
 * 重算口径（C 线接口清单 §3 MS02）：分母为任务下全部未删要求（含选传），分子为有 is_current=1 提交的
 * 要求数，completeRate 取 0–100 整数。team 表读写集中在四个带 TODO(联调) 注释的私有方法里。
 */
@Service
public class MaterialCompletionServiceImpl implements MaterialCompletionService {

    /** 概览出参的时间文案格式（规范 §2.1：yyyy-MM-dd HH:mm:ss） */
    private static final DateTimeFormatter VO_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private MaterialTaskMapper taskMapper;

    @Resource
    private MaterialRequirementMapper requirementMapper;

    @Resource
    private MaterialSubmissionMapper submissionMapper;

    @Resource
    private MaterialRequirementService requirementService;

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

    @Override
    public Result<TeamMaterialRowVO> teamOverview(Long teamId) {
        //1.队伍必须存在
        TeamRow team = loadTeam(teamId);
        if (team == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "赛队不存在");
        }
        //2.该比赛的材料任务：没配任务按 404 拒绝（与 MS02 校验链同口径）
        List<MaterialTask> tasks = taskMapper.selectList(
                new QueryWrapper<MaterialTask>().eq("contest_id", team.contestId()));
        if (tasks.isEmpty()) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "材料任务不存在");
        }
        //3.任务下全部未删要求（sort_no / id 升序）与该队当前生效提交
        List<MaterialRequirement> reqs = requirementMapper.selectList(
                new QueryWrapper<MaterialRequirement>().eq("task_id", tasks.get(0).getId())
                        .orderByAsc("sort_no").orderByAsc("id"));
        Map<Long, MaterialSubmission> currents = currentSubmissionsByTeam(teamId);
        //4.逐要求组装提交状态行；statusText 只数必传项缺口
        List<MaterialSubmitInfoVO> items = new ArrayList<>(reqs.size());
        int requiredMissing = 0;
        for (MaterialRequirement req : reqs) {
            MaterialSubmitInfoVO item = new MaterialSubmitInfoVO();
            item.setRequirementId(req.getId());
            item.setRequirementName(req.getName());
            item.setMaterialType(req.getMaterialType());
            item.setMaterialTypeLabel(MaterialTypeEnum.labelOf(req.getMaterialType()));
            SubmitState state = SubmitState.of(currents.get(req.getId()));
            item.setStatus(state.status());
            item.setVersion(state.version());
            item.setFileName(state.fileName());
            item.setUploadedAt(state.uploadedAt());
            if ("NOT_SUBMITTED".equals(state.status())
                    && req.getRequiredFlag() != null && req.getRequiredFlag() == 1) {
                requiredMissing++;
            }
            items.add(item);
        }
        //5.组装概览行（completeRate 读 team 表现值，不现算）
        TeamMaterialRowVO vo = new TeamMaterialRowVO();
        vo.setTeamId(teamId);
        vo.setTeamName(team.name());
        vo.setLeaderName(loadLeaderName(teamId, team.leaderMemberId()));
        vo.setCompleteRate(team.completeRate() == null ? 0 : team.completeRate().intValue());
        vo.setStatusText(requiredMissing > 0 ? "待补 " + requiredMissing + " 项" : "齐全");
        vo.setItems(items);
        return Result.ok(vo);
    }

    @Override
    public Result<List<MyTeamMaterialVO>> myMaterials() {
        //1.当前学生的人员档案 ID（sys_user.member_id；没绑定直接空数组）
        Long memberId = currentMemberId(UserHolder.getUser());
        if (memberId == null) {
            return Result.ok(List.of());
        }
        //2.我在册（CONFIRMED/UNCONFIRMED）且 ACTIVE 的队伍
        List<MyTeamMaterialVO> voList = new ArrayList<>();
        for (MyTeamRow team : loadMyTeams(memberId)) {
            //3.该队所在比赛没配材料任务 → 跳过这支队伍（不报错、不出现在数组里）
            List<MaterialTask> tasks = taskMapper.selectList(
                    new QueryWrapper<MaterialTask>().eq("contest_id", team.contestId()));
            if (tasks.isEmpty()) {
                continue;
            }
            MaterialTask task = tasks.get(0);
            //4.完整要求对象复用 MR 列表服务（allowedFormats 数组 + 中文名，sort_no / id 升序；
            //   任务已在上面确认存在，listByTask 恒返回 ok 且 data 非空）
            List<MaterialRequirementVO> reqVos = requirementService.listByTask(task.getId()).getData();
            //5.逐要求组装提交状态，统计必传缺口，随后组装这支队伍
            Map<Long, MaterialSubmission> currents = currentSubmissionsByTeam(team.id());
            List<MyTeamMaterialVO.Item> items = new ArrayList<>(reqVos.size());
            int requiredMissing = 0;
            for (MaterialRequirementVO reqVo : reqVos) {
                MyTeamMaterialVO.Item item = new MyTeamMaterialVO.Item();
                item.setRequirement(reqVo);
                item.setRequirementId(reqVo.getId());
                SubmitState state = SubmitState.of(currents.get(reqVo.getId()));
                item.setStatus(state.status());
                item.setVersion(state.version());
                item.setFileName(state.fileName());
                item.setUploadedAt(state.uploadedAt());
                if ("NOT_SUBMITTED".equals(state.status()) && reqVo.getRequiredFlag() != null
                        && reqVo.getRequiredFlag() == 1) {
                    requiredMissing++;
                }
                items.add(item);
            }
            MyTeamMaterialVO vo = new MyTeamMaterialVO();
            vo.setTeamId(team.id());
            vo.setTeamName(team.name());
            vo.setTask(new MyTeamMaterialVO.TaskBrief()
                    .setId(task.getId()).setTitle(task.getName()).setDeadline(task.getDeadline()));
            vo.setCompleteRate(team.completeRate() == null ? 0 : team.completeRate().intValue());
            vo.setRequiredMissing(requiredMissing);
            vo.setItems(items);
            voList.add(vo);
        }
        return Result.ok(voList);
    }

    /** 该队全部当前生效提交：requirementId → 提交记录（is_current=1，@TableLogic 自动滤已删） */
    private Map<Long, MaterialSubmission> currentSubmissionsByTeam(Long teamId) {
        List<MaterialSubmission> submissions = submissionMapper.selectList(
                new QueryWrapper<MaterialSubmission>().eq("team_id", teamId).eq("is_current", 1));
        Map<Long, MaterialSubmission> map = new HashMap<>();
        for (MaterialSubmission submission : submissions) {
            map.put(submission.getRequirementId(), submission);
        }
        return map;
    }

    /** 当前登录用户的人员档案 ID（sys_user.member_id；未绑定返回 null），照 MaterialSubmissionServiceImpl 的写法 */
    private Long currentMemberId(CurrentUser user) {
        if (user == null || user.getUserId() == null) {
            return null;
        }
        List<Long> ids = namedTemplate.queryForList(
                "SELECT member_id FROM sys_user WHERE id = :userId AND deleted = 0",
                Map.of("userId", user.getUserId()), Long.class);
        return ids.isEmpty() || ids.get(0) == null ? null : ids.get(0);
    }

    // TODO(联调) B 线提供 ITeamService 后，改成调它的 getById(teamId)
    private TeamRow loadTeam(Long teamId) {
        String sql = "SELECT id, name, contest_id, semester_id, org_id, leader_member_id, status, "
                + "material_status, material_complete_rate FROM team WHERE id = :teamId AND deleted = 0";
        return namedTemplate.query(sql, Map.of("teamId", teamId), rs -> {
            if (!rs.next()) {
                return null;
            }
            return new TeamRow(rs.getObject("id", Long.class), rs.getString("name"),
                    rs.getObject("contest_id", Long.class), rs.getObject("semester_id", Long.class),
                    rs.getObject("org_id", Long.class), rs.getObject("leader_member_id", Long.class),
                    rs.getString("status"), rs.getString("material_status"),
                    rs.getBigDecimal("material_complete_rate"));
        });
    }

    // TODO(联调) B 线提供队伍查询服务后，改成调它的"按成员查在册队伍"
    private List<MyTeamRow> loadMyTeams(Long memberId) {
        String sql = "SELECT id, name, contest_id, material_complete_rate FROM team WHERE id IN "
                + "(SELECT team_id FROM team_member WHERE member_id = :memberId "
                + "AND status IN ('CONFIRMED', 'UNCONFIRMED') AND deleted = 0) AND status = 'ACTIVE' AND deleted = 0";
        return namedTemplate.query(sql, Map.of("memberId", memberId), rs -> {
            List<MyTeamRow> list = new ArrayList<>();
            while (rs.next()) {
                list.add(new MyTeamRow(rs.getObject("id", Long.class), rs.getString("name"),
                        rs.getObject("contest_id", Long.class),
                        rs.getBigDecimal("material_complete_rate")));
            }
            return list;
        });
    }

    // TODO(联调) B 线提供 ITeamService 后，队长名随 getById 一并带出（现为按 leader_member_id 查 team_member 冗余名）
    private String loadLeaderName(Long teamId, Long leaderMemberId) {
        if (leaderMemberId == null) {
            return null;
        }
        List<String> names = namedTemplate.queryForList(
                "SELECT member_name FROM team_member WHERE team_id = :teamId AND member_id = :memberId AND deleted = 0",
                Map.of("teamId", teamId, "memberId", leaderMemberId), String.class);
        return names.isEmpty() ? null : names.get(0);
    }

    // TODO(联调) B 线提供 ITeamService 后，改成调它的更新接口
    private void updateTeamMaterial(Long teamId, String materialStatus, int completeRate) {
        String sql = "UPDATE team SET material_status = :status, material_complete_rate = :rate "
                + "WHERE id = :teamId AND deleted = 0";
        namedTemplate.update(sql,
                Map.of("status", materialStatus, "rate", completeRate, "teamId", teamId));
    }

    /** team 行只读快照（B 线实体未建前的过渡载体，本类消费 name / contestId / leaderMemberId / completeRate） */
    private record TeamRow(Long id, String name, Long contestId, Long semesterId, Long orgId,
                           Long leaderMemberId, String status, String materialStatus,
                           BigDecimal completeRate) {
    }

    /** 我在册且 ACTIVE 的队伍行（B 线实体未建前的过渡载体） */
    private record MyTeamRow(Long id, String name, Long contestId, BigDecimal completeRate) {
    }

    /** 提交状态四元组（MS01 / MS06 共用口径）：未提交时 version=0、fileName / uploadedAt 为空串 */
    private record SubmitState(String status, int version, String fileName, String uploadedAt) {

        static SubmitState of(MaterialSubmission current) {
            if (current == null) {
                return new SubmitState("NOT_SUBMITTED", 0, "", "");
            }
            //version_no / file_name / submitted_at 三列 DDL 均 NOT NULL，不再逐字段判空
            return new SubmitState("SUBMITTED", current.getVersionNo(), current.getFileName(),
                    current.getSubmittedAt().format(VO_TIME));
        }
    }
}
