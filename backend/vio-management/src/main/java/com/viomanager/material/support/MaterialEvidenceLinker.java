package com.viomanager.material.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.material.entity.MaterialRequirement;
import com.viomanager.material.entity.MaterialSubmission;
import com.viomanager.material.entity.MaterialTask;
import com.viomanager.material.mapper.MaterialRequirementMapper;
import com.viomanager.material.mapper.MaterialSubmissionMapper;
import com.viomanager.material.mapper.MaterialTaskMapper;
import com.viomanager.material.service.MaterialCompletionService;
import com.viomanager.material.support.MaterialTeamQuerySupport.TeamRow;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * A06 佐证上传 → 材料侧补交联动器。
 *
 * <p>接口清单 §5 口径：「佐证补传后（<strong>A06 或 MS02 命中系统项</strong>），齐全率分母不变、分子补齐」。
 * 本类负责 A06 这一条路径：成果详情页传完佐证后，若该队所在比赛的任务下已有 §5 联动产生的
 * 系统佐证要求（{@code system_generated=1}），就按 MS02 同口径补一条
 * {@code material_submission}，让齐全率分母不变、分子 +1，队伍状态从 PENDING 回升。
 *
 * <p>为什么放在 material 包：它写的是材料侧的表、调的是材料侧重算器，成果包只调一个方法，
 * 避免 {@code AchievementServiceImpl} 反向依赖多个 material mapper 而膨胀/绕环路。
 *
 * <p>只处理 {@code system_generated=1} 的佐证要求：管理端手工建的普通 EVIDENCE 要求
 * 仍走 MS02 正常提交，不在 A06 自动代交的范围内。
 */
@Slf4j
@Component
public class MaterialEvidenceLinker {

    @Resource
    private MaterialTaskMapper taskMapper;

    @Resource
    private MaterialRequirementMapper requirementMapper;

    @Resource
    private MaterialSubmissionMapper submissionMapper;

    @Resource
    private MaterialTeamQuerySupport teamQuery;

    @Resource
    private MaterialCompletionService completionService;

    /**
     * 把 A06 上传的佐证记为对应系统佐证要求的"已交"，并重算该队齐全率。
     * 幂等收敛动作：队伍 / 任务 / 系统佐证要求任一不存在时静默跳过，绝不抛异常
     * （佐证上传本身已成功，不能因为材料侧没配要求就把 A06 整体失败）。
     *
     * @param teamId     成果所属队伍（个人成果 teamId 为空时直接跳过）
     * @param contestId  成果所属比赛
     * @param fileId     佐证文件 ID（已通过归属校验）
     * @param fileName   文件表权威文件名
     * @param fileSize   文件表权威大小（字节）
     * @param memberId   上传人（队长）的人员档案 ID，material_submission.submitted_by 非空
     * @param memberName 上传人姓名冗余
     */
    public void linkEvidenceSubmission(Long teamId, Long contestId, Long fileId, String fileName,
                                       Long fileSize, Long memberId, String memberName) {
        //1.入参防线：个人成果（teamId 空）或缺少归属人时不落材料侧记录
        if (teamId == null || contestId == null || fileId == null || memberId == null) {
            return;
        }
        //2.队伍必须存在（取 orgId 冗余列；队伍已删则跳过）
        TeamRow team = teamQuery.loadTeam(teamId);
        if (team == null) {
            log.debug("A06 联动：队伍 {} 不存在，跳过材料侧补交", teamId);
            return;
        }
        //3.该比赛的任务（一赛一任务是建任务的强规则；orderByAsc("id") 定序，容错取第一条不再漂移）
        List<MaterialTask> tasks = taskMapper.selectList(
                new QueryWrapper<MaterialTask>().eq("contest_id", team.getContestId())
                        .orderByAsc("id"));
        if (tasks.isEmpty()) {
            log.debug("A06 联动：比赛 {} 未配材料任务，跳过材料侧补交", team.getContestId());
            return;
        }
        MaterialTask task = tasks.get(0);
        //4.系统生成的佐证要求（§5 联动项）；没有说明该队还没触发过联动，无需补交
        List<MaterialRequirement> requirements = requirementMapper.selectList(
                new QueryWrapper<MaterialRequirement>()
                        .eq("task_id", task.getId())
                        .eq("material_type", "EVIDENCE")
                        .eq("system_generated", 1));
        if (requirements.isEmpty()) {
            log.debug("A06 联动：任务 {} 下无系统佐证要求，跳过材料侧补交", task.getId());
            return;
        }
        MaterialRequirement requirement = requirements.get(0);
        //5.覆盖式 upsert（与 MS02 同口径，含并发两道防线）：先锁同键行 → 失旧版 → 取历史最大版本 +1
        submissionMapper.lockByRequirementAndTeam(requirement.getId(), teamId);
        submissionMapper.invalidateCurrent(requirement.getId(), teamId);
        Integer maxVersionNo = submissionMapper.selectMaxVersionNo(requirement.getId(), teamId);
        MaterialSubmission submission = new MaterialSubmission();
        submission.setRequirementId(requirement.getId());
        submission.setTeamId(teamId);
        submission.setTaskId(task.getId());
        submission.setContestId(task.getContestId());
        submission.setSemesterId(task.getSemesterId());
        submission.setOrgId(team.getOrgId());
        submission.setFileId(fileId);
        submission.setFileName(fileName);
        submission.setFileSize(fileSize);
        submission.setIsCurrent(1);
        submission.setVersionNo((maxVersionNo == null ? 0 : maxVersionNo) + 1);
        submission.setSubmittedBy(memberId);
        submission.setSubmittedByName(memberName);
        //submittedAt 必须手工赋值：AuditMetaObjectHandler 只填四个审计列，不管业务时间列
        submission.setSubmittedAt(LocalDateTime.now());
        submissionMapper.insert(submission);
        //6.只重算本队（要求集没变，只是本队分子 +1，无需整场收敛）
        completionService.recompute(teamId);
    }
}
