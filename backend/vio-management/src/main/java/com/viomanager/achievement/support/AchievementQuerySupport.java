package com.viomanager.achievement.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.achievement.dto.AchievementQueryDTO;
import com.viomanager.achievement.entity.Achievement;
import com.viomanager.achievement.mapper.AchievementMapper;
import com.viomanager.achievement.vo.AchievementDetailVO;
import com.viomanager.achievement.vo.AchievementVO;
import com.viomanager.achievement.vo.MyAchievementVO;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.material.support.MaterialTeamQuerySupport;
import com.viomanager.material.support.MaterialTeamQuerySupport.TeamMembership;
import com.viomanager.material.support.MemberIdSupport;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 成果查询支撑（A01 列表 / A02 详情 / A08 我的成果，从 AchievementServiceImpl 拆出以守住
 * 单文件行数铁律，分法对齐 B 线的查询 / 命令双 Support）：
 * 数据范围——管理端全量；学生只看自己在册队伍的成果（无队伍返回空数组，绝不返回全部）；
 * team / team_member 访问一律走 MaterialTeamQuerySupport（sys_user 反查 memberId 走 MemberIdSupport）。
 */
@Component
public class AchievementQuerySupport {

    @Resource
    private AchievementMapper achievementMapper;

    @Resource
    private MaterialTeamQuerySupport teamQuery;

    @Resource
    private MemberIdSupport memberIdSupport;

    /** A01 成果列表：筛选（teamId / type / status / keyword 模糊）+ 学生数据范围 + 队名填充 */
    public Result<List<AchievementVO>> list(AchievementQueryDTO dto) {
        //学生数据范围：无队伍直接空数组，绝不返回全部
        List<Long> visibleTeamIds = studentVisibleTeamIds(UserHolder.getUser());
        if (visibleTeamIds != null && visibleTeamIds.isEmpty()) {
            return Result.ok(new ArrayList<>());
        }
        QueryWrapper<Achievement> wrapper = new QueryWrapper<>();
        if (dto.getTeamId() != null) {
            wrapper.eq("team_id", dto.getTeamId());
        }
        if (dto.getType() != null && !dto.getType().isBlank()) {
            wrapper.eq("achievement_type", dto.getType());
        }
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            wrapper.eq("status", dto.getStatus());
        }
        if (dto.getKeyword() != null && !dto.getKeyword().isBlank()) {
            wrapper.like("name", dto.getKeyword());
        }
        if (visibleTeamIds != null) {
            wrapper.in("team_id", visibleTeamIds);
        }
        wrapper.orderByDesc("created_at");
        List<Achievement> achievements = achievementMapper.selectList(wrapper);
        if (achievements.isEmpty()) {
            return Result.ok(new ArrayList<>());
        }
        return Result.ok(toVOList(achievements));
    }

    /** A02 详情：管理端或本队在册成员（否则 403），附 viewerIsLeader 标识 */
    public Result<AchievementDetailVO> detail(Long id) {
        Achievement achievement = achievementMapper.selectById(id);
        if (achievement == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "成果不存在");
        }
        //权限：管理端或本队在册成员
        CurrentUser user = UserHolder.getUser();
        Long memberId = memberIdSupport.currentMemberId(user);
        if (!isAdmin(user) && (memberId == null || achievement.getTeamId() == null
                || !teamQuery.isTeamMember(achievement.getTeamId(), memberId))) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "仅本队成员与管理端可查看");
        }
        Map<Long, String> teamNames = teamQuery.loadTeamNames(
                achievement.getTeamId() == null
                        ? Collections.emptyList() : Collections.singletonList(achievement.getTeamId()));
        AchievementDetailVO vo = fillVO(new AchievementDetailVO(), achievement, teamNames);
        vo.setDescription(achievement.getDescription());
        vo.setEvidenceFileId(achievement.getEvidenceFileId());
        vo.setEvidenceFileName(achievement.getEvidenceFileName());
        vo.setCreatedAt(achievement.getCreatedAt());
        vo.setViewerIsLeader(memberId != null && achievement.getTeamId() != null
                && teamQuery.isTeamLeader(achievement.getTeamId(), memberId));
        return Result.ok(vo);
    }

    /** A08 我的成果：在册且 ACTIVE 的队伍（含队长标识）+ 这些队伍的成果动态聚合，不读冗余表 */
    public Result<MyAchievementVO> myContributions() {
        MyAchievementVO vo = new MyAchievementVO();
        Long memberId = memberIdSupport.currentMemberId(UserHolder.getUser());
        if (memberId == null) {
            return Result.ok(vo);
        }
        //队伍取在册且 ACTIVE 的（含队长标识）
        for (TeamMembership membership : teamQuery.loadMyTeamMemberships(memberId)) {
            if (!"ACTIVE".equals(membership.getTeamStatus())) {
                continue;
            }
            vo.getTeams().add(new MyAchievementVO.TeamBrief()
                    .setTeamId(membership.getTeamId())
                    .setTeamName(membership.getTeamName())
                    .setIsLeader(membership.isLeader()));
        }
        if (vo.getTeams().isEmpty()) {
            return Result.ok(vo);
        }
        List<Long> teamIds = new ArrayList<>();
        for (MyAchievementVO.TeamBrief brief : vo.getTeams()) {
            teamIds.add(brief.getTeamId());
        }
        List<Achievement> achievements = achievementMapper.selectList(
                new QueryWrapper<Achievement>().in("team_id", teamIds).orderByDesc("created_at"));
        vo.getItems().addAll(toVOList(achievements));
        return Result.ok(vo);
    }

    /** 成果列表 → VO 列表（批量查队名 + 逐条填充） */
    public List<AchievementVO> toVOList(List<Achievement> achievements) {
        Map<Long, String> teamNames = teamQuery.loadTeamNames(distinctTeamIds(achievements));
        List<AchievementVO> voList = new ArrayList<>(achievements.size());
        for (Achievement achievement : achievements) {
            voList.add(fillVO(new AchievementVO(), achievement, teamNames));
        }
        return voList;
    }

    /** 实体 → VO 公共字段填充（详情 VO 经泛型复用；接口字段名与实体列名的映射见 AchievementVO 注释） */
    public <V extends AchievementVO> V fillVO(V vo, Achievement achievement,
                                               Map<Long, String> teamNames) {
        vo.setId(achievement.getId());
        vo.setTeamId(achievement.getTeamId());
        vo.setTeamName(achievement.getTeamId() == null ? null : teamNames.get(achievement.getTeamId()));
        vo.setType(achievement.getAchievementType());
        vo.setName(achievement.getName());
        vo.setLevel(achievement.getLevel());
        vo.setAchievedAt(achievement.getAwardDate());
        vo.setStatus(achievement.getStatus());
        vo.setSubmittedBy(achievement.getOwnerName());
        vo.setHasEvidence(achievement.getEvidenceFileId() != null);
        return vo;
    }

    /** 学生视角可见队伍 ID：非学生返回 null（不筛）；学生无档案或无队伍返回空 List（页面空数组） */
    private List<Long> studentVisibleTeamIds(CurrentUser user) {
        if (user == null || !"STUDENT".equals(user.getRole())) {
            return null;
        }
        Long memberId = memberIdSupport.currentMemberId(user);
        List<Long> teamIds = new ArrayList<>();
        if (memberId != null) {
            for (TeamMembership membership : teamQuery.loadMyTeamMemberships(memberId)) {
                teamIds.add(membership.getTeamId());
            }
        }
        return teamIds;
    }

    /** 成果里的非空 teamId 去重列表（查队名用） */
    private List<Long> distinctTeamIds(List<Achievement> achievements) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Achievement achievement : achievements) {
            if (achievement.getTeamId() != null) {
                ids.add(achievement.getTeamId());
            }
        }
        return new ArrayList<>(ids);
    }

    /** 是否管理端角色（SUPER_ADMIN / DEPT_ADMIN） */
    private boolean isAdmin(CurrentUser user) {
        return user != null && ("SUPER_ADMIN".equals(user.getRole()) || "DEPT_ADMIN".equals(user.getRole()));
    }
}
