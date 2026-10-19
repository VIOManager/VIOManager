package com.viomanager.achievement.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.achievement.dto.AchievementCreateDTO;
import com.viomanager.achievement.entity.Achievement;
import com.viomanager.achievement.handler.AchievementSubmitHandler;
import com.viomanager.achievement.mapper.AchievementMapper;
import com.viomanager.approval.dto.ApprovalCreateDTO;
import com.viomanager.approval.service.ApprovalClient;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.material.support.MaterialTeamQuerySupport;
import com.viomanager.material.support.MaterialTeamQuerySupport.TeamRow;
import com.viomanager.material.support.TeamOrgScopeSupport;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 成果入库与审批路由（A03 的分支编排，从 AchievementServiceImpl 抽出以守住单文件行数铁律）：
 *  - 学生（STUDENT）：队长校验 → 建成果（PENDING / USER）→ 调 ApprovalClient 创建 ACHIEVEMENT_SUBMIT 审批单
 *  - 管理员（SUPER_ADMIN / DEPT_ADMIN）：建成果（APPROVED / ADMIN）→ 触发 §5 联动（调用点位见 triggerSection5Linkage）
 * 与调用方在同一事务内：审批单创建失败会连同成果插入一起回滚，不留孤儿 PENDING 成果。
 * team / team_member 查询一律走 MaterialTeamQuerySupport。
 */
@Component
public class AchievementApprovalSupport {

    @Resource
    private AchievementMapper achievementMapper;

    @Resource
    private ApprovalClient approvalClient;

    @Resource
    private MaterialTeamQuerySupport teamQuery;

    @Resource
    private AchievementSubmitHandler achievementSubmitHandler;

    @Resource
    private TeamOrgScopeSupport orgScope;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 入库并路由：achievement 由调用方完成 DTO 转换（归属人已填），
     * status / submitChannel / 审批字段由本方法按分支补齐。
     */
    public void insertAndRoute(Achievement achievement, TeamRow team,
                               AchievementCreateDTO dto, CurrentUser user, Long memberId) {
        boolean student = user != null && "STUDENT".equals(user.getRole());
        if (student) {
            //学生：仅队长可提交成果（memberId 未绑定时同样拦在这）
            if (memberId == null || !teamQuery.isTeamLeader(dto.getTeamId(), memberId)) {
                throw new BizException(CommonErrorCode.FORBIDDEN, "仅队长可提交成果");
            }
            achievement.setStatus("PENDING");
            achievement.setSubmitChannel("USER");
            achievementMapper.insert(achievement);
            createSubmitApproval(achievement, team, dto, user, memberId);
            return;
        }
        //管理员：直接录入生效。先过组织子树校验（部门管理员越权录其他部门队伍 1003；
        //超管不受影响；学生分支已在上面返回，不经过这里——学生由队长校验负责）
        orgScope.assertTeamInScope(dto.getTeamId());
        //owner_member_id 列非空 ——
        //纯管理账号（sys_user.member_id 为 NULL，如 demo 的 admin）不能作为成果归属人，
        //回退为该队队长：管理员代录的成果仍归属队伍，A07 本人导出 / A08 聚合均按 owner 收敛，
        //记在管理员名下会让该成果永远进不了学生本人的导出（实测过的问题）。
        if (memberId == null) {
            Long leaderMemberId = team.getLeaderMemberId();
            if (leaderMemberId == null) {
                throw new BizException(CommonErrorCode.PARAM_INVALID,
                        "当前账号未绑定人员档案且该队无队长，无法确定成果归属人；请改用已绑定档案的账号录入");
            }
            achievement.setOwnerMemberId(leaderMemberId);
            achievement.setOwnerName(teamQuery.loadLeaderName(dto.getTeamId(), leaderMemberId));
        }
        achievement.setStatus("APPROVED");
        achievement.setSubmitChannel("ADMIN");
        achievement.setApprovedAt(LocalDateTime.now());
        achievement.setApprovedBy(user == null ? null : user.getUserId());
        achievementMapper.insert(achievement);
        //管理员直接通过的成果立即触发 §5 联动（第二个任务的 Handler 接上）
        triggerSection5Linkage(achievement);
    }

    /** 创建 ACHIEVEMENT_SUBMIT 审批单：payloadJson 必须含成果 ID（批准回调按它定位成果），Jackson 序列化 Map */
    private void createSubmitApproval(Achievement achievement, TeamRow team,
                                      AchievementCreateDTO dto, CurrentUser user, Long memberId) {
        ApprovalCreateDTO approval = new ApprovalCreateDTO();
        approval.setBizType("ACHIEVEMENT_SUBMIT");
        //创建型审批：bizId 留 null，批准后由回调按 payloadJson 里的 achievementId 定位成果
        approval.setBizTitle(team.getName() + " 成果提交：" + dto.getName());
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("achievementId", String.valueOf(achievement.getId()));
        payload.put("teamId", String.valueOf(dto.getTeamId()));
        payload.put("name", dto.getName());
        payload.put("type", dto.getType());
        try {
            approval.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "审批单内容序列化失败");
        }
        approval.setApplicantMemberId(memberId);
        approval.setApplicantName(user == null ? null : user.getRealName());
        approval.setApproverOrgId(team.getOrgId());
        approval.setTeamId(dto.getTeamId());
        approval.setContestId(team.getContestId());
        approval.setSemesterId(team.getSemesterId());
        approvalClient.createApproval(approval);
    }

    /**
     * §5 联动调用点位（与审批批准回调共用 AchievementSubmitHandler.handleSection5Linkage）：
     * 有审批通过成果但未传佐证的队伍，必交项额外加 1 条"成果佐证材料"
     * （material_requirement.system_generated=1，管理端不可增删）；
     * 管理员直录已带佐证的成果不追加必交项，仅按比赛整场重算。
     */
    private void triggerSection5Linkage(Achievement achievement) {
        achievementSubmitHandler.handleSection5Linkage(achievement);
    }
}
