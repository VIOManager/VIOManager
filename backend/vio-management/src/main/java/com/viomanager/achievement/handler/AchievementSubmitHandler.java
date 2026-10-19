package com.viomanager.achievement.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.achievement.entity.Achievement;
import com.viomanager.achievement.mapper.AchievementMapper;
import com.viomanager.approval.handler.ApprovalHandler;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.material.entity.MaterialRequirement;
import com.viomanager.material.entity.MaterialTask;
import com.viomanager.material.mapper.MaterialRequirementMapper;
import com.viomanager.material.mapper.MaterialTaskMapper;
import com.viomanager.material.service.MaterialCompletionService;
import com.viomanager.material.support.MaterialTeamQuerySupport;
import com.viomanager.material.support.MaterialTeamQuerySupport.TeamRow;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 成果提交审批回调（bizType=ACHIEVEMENT_SUBMIT，§5 联动）：
 * 批准 → 成果置 APPROVED + 给队伍的材料任务自动追加一条"佐证材料"必交项 + 重算齐全率；
 * 驳回 → 成果置 REJECTED，不动材料侧任何数据。
 *
 * 契约与幂等说明（ApprovalHandler 契约）：
 *  1. 骨架保证回调"至多一次"，且与审批处理在**同一事务**内；本 Handler 全部是数据库操作
 *     → **无需自建幂等**（事务内的库操作失败会连同审批结果一起回滚，契约第 1 条）。
 *  2. 幂等的第二层保证：EVIDENCE 要求"已存在则不再新增"——同一队伍通过 2 条成果只加 1 条佐证项。
 *  3. 待审批（PENDING）的成果**不占用**佐证必交项：只有审批通过才触发，且本 Handler 只在 onApproved 里加。
 *
 * 联动核心 linkEvidenceRequirement 同时服务管理员直录路径（AchievementApprovalSupport 的调用点位），
 * 两路都在各自调用方的事务内执行，本类不另挂 @Transactional。
 */
@Slf4j
@Component
public class AchievementSubmitHandler implements ApprovalHandler {

    @Resource
    private AchievementMapper achievementMapper;

    @Resource
    private MaterialTaskMapper taskMapper;

    @Resource
    private MaterialRequirementMapper requirementMapper;

    @Resource
    private MaterialCompletionService completionService;

    @Resource
    private MaterialTeamQuerySupport teamQuery;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public String bizType() {
        return "ACHIEVEMENT_SUBMIT";
    }

    @Override
    public Long onApproved(Long approvalId, Long bizId, String payloadJson) {
        //1.解析成果 ID（提交时写入的是字符串化雪花 ID）；解析失败记日志返回 null，绝不让异常炸掉审批
        Long achievementId = parseAchievementId(payloadJson);
        if (achievementId == null) {
            log.error("ACHIEVEMENT_SUBMIT 批准回调 payload 解析失败或缺少 achievementId，"
                    + "approvalId={}，payload={}", approvalId, payloadJson);
            return null;
        }
        //2.成果置 APPROVED：已是 APPROVED 直接返回（不重复处理）；不存在记 warn 返回 null
        Achievement achievement = achievementMapper.selectById(achievementId);
        if (achievement == null) {
            log.warn("ACHIEVEMENT_SUBMIT 批准回调找不到成果，approvalId={}，achievementId={}",
                    approvalId, achievementId);
            return null;
        }
        if ("APPROVED".equals(achievement.getStatus())) {
            return achievement.getId();
        }
        UpdateWrapper<Achievement> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", achievement.getId())
                .set("status", "APPROVED")
                .set("approved_at", LocalDateTime.now())
                .set("approved_by", approverUserId());
        achievementMapper.update(null, wrapper);
        //3+4.联动：给队伍材料任务追加佐证必交项（已有则跳过）并重算齐全率
        linkEvidenceRequirement(achievement.getTeamId());
        //5.返回成果 ID（创建型审批，骨架用它回填 approval.biz_id）
        return achievement.getId();
    }

    @Override
    public void onRejected(Long approvalId, Long bizId, String payloadJson) {
        Long achievementId = parseAchievementId(payloadJson);
        if (achievementId == null) {
            log.error("ACHIEVEMENT_SUBMIT 驳回回调 payload 解析失败或缺少 achievementId，"
                    + "approvalId={}，payload={}", approvalId, payloadJson);
            return;
        }
        Achievement achievement = achievementMapper.selectById(achievementId);
        if (achievement == null || "REJECTED".equals(achievement.getStatus())) {
            return;
        }
        UpdateWrapper<Achievement> wrapper = new UpdateWrapper<>();
        //驳回意见（comment）在回调签名与 payloadJson 里都拿不到——留空
        // TODO(骨架) 建议后续把 comment 随 payloadJson 下发或扩展回调上下文，届时回填 reject_reason
        wrapper.eq("id", achievement.getId())
                .set("status", "REJECTED")
                .set("reject_reason", null);
        achievementMapper.update(null, wrapper);
        //驳回不加佐证必交项、不重算齐全率（PENDING 成果不占用佐证项）
    }

    /**
     * §5 联动核心（审批批准与管理员直录两路共用）：
     * 该队所在比赛的材料任务下没有 EVIDENCE 要求时追加一条 system_generated=1 的必交项，
     * 随后重算齐全率——要求数 +1 而提交数不变 → 齐全率下降，队伍状态可能转 PENDING。
     */
    public void linkEvidenceRequirement(Long teamId) {
        TeamRow team = teamQuery.loadTeam(teamId);
        if (team == null) {
            log.warn("§5 联动找不到队伍，跳过佐证必交项追加：teamId={}", teamId);
            return;
        }
        //一赛一任务是建任务的强规则，selectList 容错取第一条
        List<MaterialTask> tasks = taskMapper.selectList(
                new QueryWrapper<MaterialTask>().eq("contest_id", team.getContestId()));
        if (tasks.isEmpty()) {
            log.warn("§5 联动：队伍 {} 所在比赛没有材料任务，跳过佐证必交项追加", teamId);
            return;
        }
        MaterialTask task = tasks.get(0);
        //已有 EVIDENCE 要求（含 system_generated=1 的历史项）则不再新增——"通过 2 条成果只加 1 条佐证项"
        Long existed = requirementMapper.selectCount(new QueryWrapper<MaterialRequirement>()
                .eq("task_id", task.getId()).eq("material_type", "EVIDENCE"));
        if (existed == null || existed == 0) {
            MaterialRequirement requirement = new MaterialRequirement();
            requirement.setTaskId(task.getId());
            requirement.setContestId(task.getContestId());
            requirement.setMaterialType("EVIDENCE");
            requirement.setName("佐证材料");
            requirement.setRequiredFlag(1);
            requirement.setFormats("pdf,jpg,zip");
            requirement.setMaxSizeMb(20);
            requirement.setNamingRule("队名-成果名-佐证");
            //系统追加项：管理端不可增删（MR02/MR03 拦截的就是这个标记）
            requirement.setSystemGenerated(1);
            requirement.setSortNo(999);
            requirementMapper.insert(requirement);
        }
        //重算齐全率（无新增时重算一次也幂等无害）
        completionService.recompute(teamId);
    }

    /** 从 payloadJson 解析成果 ID（提交时写入的是字符串化雪花 ID）；任何异常都吞掉返回 null */
    private Long parseAchievementId(String payloadJson) {
        try {
            Map<String, Object> payload =
                    objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() { });
            Object achievementId = payload.get("achievementId");
            return achievementId == null ? null : Long.valueOf(String.valueOf(achievementId));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 当前审批人账号 ID：回调签名不携带，但回调与审批动作在同一线程同一事务内执行，
     * UserHolder 里就是点"批准"的那位管理员（approvedBy 口径与 A03 管理员直录一致，都是 sys_user.id）。
     * （若日后骨架改为异步/定时触发回调，这里会拿不到人，需改为经 payloadJson 传递审批人）
     */
    private Long approverUserId() {
        CurrentUser user = UserHolder.getUser();
        return user == null ? null : user.getUserId();
    }
}
