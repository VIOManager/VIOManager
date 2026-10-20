package com.viomanager.material.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.viomanager.material.support.MaterialCompletionRecalculator;
import com.viomanager.material.support.MaterialTeamQuerySupport;
import com.viomanager.material.support.MaterialTeamQuerySupport.MyTeamRow;
import com.viomanager.material.support.MaterialTeamQuerySupport.TeamRow;
import com.viomanager.material.support.MemberIdSupport;
import com.viomanager.material.support.TeamOrgScopeSupport;
import com.viomanager.material.vo.MaterialRequirementVO;
import com.viomanager.material.vo.MaterialSubmitInfoVO;
import com.viomanager.material.vo.MyTeamMaterialVO;
import com.viomanager.material.vo.TeamMaterialRowVO;
import jakarta.annotation.Resource;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 队伍材料完成度实现：齐全率重算（recompute）+ MS01 管理端队伍概览 + MS06 学生端我的材料。
 * 重算口径（C 线接口清单 §3 MS02）：分母为任务下全部未删要求（含选传），分子为有 is_current=1 提交的
 * 要求数，completeRate 取 0–100 整数。team / team_member 表的跨线 SQL 全部集中在
 * MaterialTeamQuerySupport（B 线过渡方案，TODO(联调) 注释在该类逐方法标注）；
 * 当前人 → 人员档案 ID 反查走 MemberIdSupport（material / achievement 两包共用）。
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
    private MaterialTeamQuerySupport teamQuery;

    @Resource
    private MemberIdSupport memberIdSupport;

    @Resource
    private TeamOrgScopeSupport orgScope;

    @Resource
    private MaterialCompletionRecalculator recalculator;

    @Override
    public void recompute(Long teamId) {
        //实现收敛到 MaterialCompletionRecalculator（单份逻辑）：本服务已被
        //MaterialRequirementService 依赖，若在这里放重算实现会与它形成循环依赖
        recalculator.recompute(teamId);
    }

    @Override
    public void recomputeByContest(Long contestId) {
        recalculator.recomputeByContest(contestId);
    }

    @Override
    public Result<TeamMaterialRowVO> teamOverview(Long teamId) {
        //0.部门管理员组织子树裁剪（越权 1003；超管/学生不受影响，MS01 本就是管理端口）
        orgScope.assertTeamInScope(teamId);
        //1.队伍必须存在
        TeamRow team = teamQuery.loadTeam(teamId);
        if (team == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "赛队不存在");
        }
        //2.该比赛的材料任务：没配任务按 404 拒绝（与 MS02 校验链同口径）；orderByAsc("id") 定序取第一条
        List<MaterialTask> tasks = taskMapper.selectList(
                new QueryWrapper<MaterialTask>().eq("contest_id", team.getContestId())
                        .orderByAsc("id"));
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
            item.setStatus(state.getStatus());
            item.setVersion(state.getVersion());
            item.setFileName(state.getFileName());
            item.setUploadedAt(state.getUploadedAt());
            if ("NOT_SUBMITTED".equals(state.getStatus())
                    && req.getRequiredFlag() != null && req.getRequiredFlag() == 1) {
                requiredMissing++;
            }
            items.add(item);
        }
        //5.组装概览行（completeRate 读 team 表现值，不现算）
        TeamMaterialRowVO vo = new TeamMaterialRowVO();
        vo.setTeamId(teamId);
        vo.setTeamName(team.getName());
        vo.setLeaderName(teamQuery.loadLeaderName(teamId, team.getLeaderMemberId()));
        vo.setCompleteRate(team.getCompleteRate() == null ? 0 : team.getCompleteRate().intValue());
        vo.setStatusText(requiredMissing > 0 ? "待补 " + requiredMissing + " 项" : "齐全");
        vo.setItems(items);
        return Result.ok(vo);
    }

    @Override
    public Result<List<MyTeamMaterialVO>> myMaterials() {
        //1.当前学生的人员档案 ID（sys_user.member_id；没绑定直接空数组）
        Long memberId = memberIdSupport.currentMemberId(UserHolder.getUser());
        if (memberId == null) {
            return Result.ok(new ArrayList<>());
        }
        //2.我在册（CONFIRMED/UNCONFIRMED）且 ACTIVE 的队伍
        List<MyTeamMaterialVO> voList = new ArrayList<>();
        for (MyTeamRow team : teamQuery.loadMyTeams(memberId)) {
            //3.该队所在比赛没配材料任务 → 跳过这支队伍（不报错、不出现在数组里）；orderByAsc("id") 定序
            List<MaterialTask> tasks = taskMapper.selectList(
                    new QueryWrapper<MaterialTask>().eq("contest_id", team.getContestId())
                            .orderByAsc("id"));
            if (tasks.isEmpty()) {
                continue;
            }
            MaterialTask task = tasks.get(0);
            //4.完整要求对象复用 MR 列表服务（allowedFormats 数组 + 中文名，sort_no / id 升序；
            //   任务已在上面确认存在，listByTask 恒返回 ok 且 data 非空）
            List<MaterialRequirementVO> reqVos = requirementService.listByTask(task.getId()).getData();
            //5.逐要求组装提交状态，统计必传缺口，随后组装这支队伍
            Map<Long, MaterialSubmission> currents = currentSubmissionsByTeam(team.getId());
            List<MyTeamMaterialVO.Item> items = new ArrayList<>(reqVos.size());
            int requiredMissing = 0;
            for (MaterialRequirementVO reqVo : reqVos) {
                MyTeamMaterialVO.Item item = new MyTeamMaterialVO.Item();
                item.setRequirement(reqVo);
                item.setRequirementId(reqVo.getId());
                SubmitState state = SubmitState.of(currents.get(reqVo.getId()));
                item.setStatus(state.getStatus());
                item.setVersion(state.getVersion());
                item.setFileName(state.getFileName());
                item.setUploadedAt(state.getUploadedAt());
                if ("NOT_SUBMITTED".equals(state.getStatus()) && reqVo.getRequiredFlag() != null
                        && reqVo.getRequiredFlag() == 1) {
                    requiredMissing++;
                }
                items.add(item);
            }
            MyTeamMaterialVO vo = new MyTeamMaterialVO();
            vo.setTeamId(team.getId());
            vo.setTeamName(team.getName());
            vo.setTask(new MyTeamMaterialVO.TaskBrief()
                    .setId(task.getId()).setTitle(task.getName()).setDeadline(task.getDeadline()));
            vo.setCompleteRate(team.getCompleteRate() == null ? 0 : team.getCompleteRate().intValue());
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

    /** 提交状态四元组（MS01 / MS06 共用口径）：未提交时 version=0、fileName / uploadedAt 为空串 */
    @Data
    @AllArgsConstructor
    private static class SubmitState {
        private String status;
        private int version;
        private String fileName;
        private String uploadedAt;

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
