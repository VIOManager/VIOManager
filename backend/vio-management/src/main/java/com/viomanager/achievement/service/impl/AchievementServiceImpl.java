package com.viomanager.achievement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.viomanager.achievement.dto.AchievementCreateDTO;
import com.viomanager.achievement.dto.AchievementEvidenceDTO;
import com.viomanager.achievement.dto.AchievementQueryDTO;
import com.viomanager.achievement.dto.AchievementUpdateDTO;
import com.viomanager.achievement.entity.Achievement;
import com.viomanager.achievement.entity.AchievementFile;
import com.viomanager.achievement.mapper.AchievementFileMapper;
import com.viomanager.achievement.mapper.AchievementMapper;
import com.viomanager.achievement.service.AchievementService;
import com.viomanager.achievement.support.AchievementApprovalSupport;
import com.viomanager.achievement.vo.AchievementDetailVO;
import com.viomanager.achievement.vo.AchievementVO;
import com.viomanager.achievement.vo.MyAchievementVO;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.MaterialErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.file.service.IFileService;
import com.viomanager.material.support.MaterialTeamQuerySupport;
import com.viomanager.material.support.MaterialTeamQuerySupport.TeamMembership;
import com.viomanager.material.support.MaterialTeamQuerySupport.TeamRow;
import com.viomanager.material.support.MemberIdSupport;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 成果服务实现（A01–A06 + A08）：本类只做 DTO 转换、查询与编排调度，
 * A03 的"学生走审批 / 管理员直录"分支在 AchievementApprovalSupport；
 * team / team_member 访问一律走 MaterialTeamQuerySupport（sys_user 反查 memberId 走 MemberIdSupport）。
 */
@Service
public class AchievementServiceImpl implements AchievementService {

    @Resource
    private AchievementMapper achievementMapper;

    @Resource
    private AchievementFileMapper achievementFileMapper;

    @Resource
    private MaterialTeamQuerySupport teamQuery;

    @Resource
    private AchievementApprovalSupport approvalSupport;

    @Resource
    private IFileService fileService;

    @Resource
    private MemberIdSupport memberIdSupport;

    @Override
    public Result<List<AchievementVO>> list(AchievementQueryDTO dto) {
        //数据范围：管理端全量；学生只看自己在册队伍的成果——无队伍返回空数组，绝不返回全部
        List<Long> visibleTeamIds = null;
        CurrentUser user = UserHolder.getUser();
        if (user != null && "STUDENT".equals(user.getRole())) {
            Long memberId = memberIdSupport.currentMemberId(user);
            visibleTeamIds = memberId == null ? List.of()
                    : teamQuery.loadMyTeamMemberships(memberId).stream()
                            .map(TeamMembership::teamId).toList();
            if (visibleTeamIds.isEmpty()) {
                return Result.ok(List.of());
            }
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
            return Result.ok(List.of());
        }
        Map<Long, String> teamNames = teamQuery.loadTeamNames(achievements.stream()
                .map(Achievement::getTeamId).filter(teamId -> teamId != null).distinct().toList());
        return Result.ok(achievements.stream()
                .map(achievement -> fillVO(new AchievementVO(), achievement, teamNames)).toList());
    }

    @Override
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
        Map<Long, String> teamNames = teamQuery.loadTeamNames(achievement.getTeamId() == null
                ? List.of() : List.of(achievement.getTeamId()));
        AchievementDetailVO vo = fillVO(new AchievementDetailVO(), achievement, teamNames);
        vo.setDescription(achievement.getDescription());
        vo.setEvidenceFileId(achievement.getEvidenceFileId());
        vo.setEvidenceFileName(achievement.getEvidenceFileName());
        vo.setCreatedAt(achievement.getCreatedAt());
        vo.setViewerIsLeader(memberId != null && achievement.getTeamId() != null
                && teamQuery.isTeamLeader(achievement.getTeamId(), memberId));
        return Result.ok(vo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<AchievementVO> create(AchievementCreateDTO dto) {
        //0.入参完整性（team_id / achievement_type / name 列 NOT NULL，日期为业务必填）
        if (dto.getTeamId() == null) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "teamId 不能为空");
        }
        if (dto.getType() == null || dto.getType().isBlank()) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "type 不能为空");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "name 不能为空");
        }
        if (dto.getAchievedAt() == null) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "achievedAt 不能为空");
        }
        //1.队伍必须存在：contestId / semesterId / orgId 随队带出（列非空或冗余）
        TeamRow team = teamQuery.loadTeam(dto.getTeamId());
        if (team == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "赛队不存在");
        }
        //2.DTO → 实体（归属人为当前登录人；队长资格与角色分支由 Support 编排）
        CurrentUser user = UserHolder.getUser();
        Long memberId = memberIdSupport.currentMemberId(user);
        //佐证文件归属（可选字段带了才查）：fileId 必须为当前用户上传，文件名以文件表权威值为准
        if (dto.getEvidenceFileId() != null) {
            dto.setEvidenceFileName(fileService.loadOwnedFile(
                    dto.getEvidenceFileId(), user.getUserId()).getOriginalName());
        }
        Achievement achievement = new Achievement();
        achievement.setTeamId(dto.getTeamId());
        achievement.setContestId(team.contestId());
        achievement.setSemesterId(team.semesterId());
        achievement.setOrgId(team.orgId());
        achievement.setOwnerMemberId(memberId);
        achievement.setOwnerName(user == null ? null : user.getRealName());
        achievement.setAchievementType(dto.getType());
        achievement.setName(dto.getName());
        achievement.setLevel(dto.getLevel());
        achievement.setAwardDate(dto.getAchievedAt());
        achievement.setDescription(dto.getDescription());
        achievement.setEvidenceFileId(dto.getEvidenceFileId());
        achievement.setEvidenceFileName(dto.getEvidenceFileName());
        //3.入库并路由（同一事务）：学生 PENDING + 审批单 / 管理员 APPROVED + §5 联动点位
        approvalSupport.insertAndRoute(achievement, team, dto, user, memberId);
        return Result.ok(fillVO(new AchievementVO(), achievement,
                Map.of(dto.getTeamId(), team.name())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AchievementUpdateDTO dto) {
        //只改四个字段（PUT 全量替换，含置空）；0 行 = 不存在或已删
        UpdateWrapper<Achievement> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id)
                .set("name", dto.getName())
                .set("level", dto.getLevel())
                .set("award_date", dto.getAchievedAt())
                .set("description", dto.getDescription());
        if (achievementMapper.update(null, wrapper) == 0) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "成果不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Achievement achievement = achievementMapper.selectById(id);
        if (achievement == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "成果不存在");
        }
        if ("APPROVED".equals(achievement.getStatus())) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "已通过的成果不可删除");
        }
        //级联逻辑删除附件（@TableLogic 转 UPDATE deleted=1），再删成果本身，均不物理删
        achievementFileMapper.delete(new QueryWrapper<AchievementFile>().eq("achievement_id", id));
        achievementMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadEvidence(Long id, AchievementEvidenceDTO dto) {
        if (dto.getFileId() == null) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "fileId 不能为空");
        }
        if (dto.getFileName() == null || dto.getFileName().isBlank()) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "fileName 不能为空");
        }
        Achievement achievement = achievementMapper.selectById(id);
        if (achievement == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "成果不存在");
        }
        //仅当前队长可上传/覆盖佐证（A06，5007）
        Long memberId = memberIdSupport.currentMemberId(UserHolder.getUser());
        if (memberId == null || achievement.getTeamId() == null
                || !teamQuery.isTeamLeader(achievement.getTeamId(), memberId)) {
            throw new BizException(MaterialErrorCode.EVIDENCE_LEADER_ONLY);
        }
        //文件归属：fileId 必须为当前队长本人上传，文件名以文件表权威值覆盖自报（防伪造 fileId 与假名落库）
        dto.setFileName(fileService.loadOwnedFile(
                dto.getFileId(), UserHolder.getUser().getUserId()).getOriginalName());
        //单文件覆盖式：失效旧版 → 插新版本（uploadedAt 手工赋值，业务时间列不归审计填充器）
        achievementFileMapper.invalidateCurrent(id, "EVIDENCE");
        Integer maxVersion = achievementFileMapper.selectMaxVersionNo(id, "EVIDENCE");
        AchievementFile file = new AchievementFile();
        file.setAchievementId(id);
        file.setFileId(dto.getFileId());
        file.setFileName(dto.getFileName());
        file.setFileType("EVIDENCE");
        file.setIsCurrent(1);
        file.setVersionNo((maxVersion == null ? 0 : maxVersion) + 1);
        file.setUploadedAt(LocalDateTime.now());
        achievementFileMapper.insert(file);
        //同步 achievement 两列（前端只读这两个，hasEvidence 也由它派生）
        UpdateWrapper<Achievement> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id)
                .set("evidence_file_id", dto.getFileId())
                .set("evidence_file_name", dto.getFileName());
        achievementMapper.update(null, wrapper);
    }

    @Override
    public Result<MyAchievementVO> myContributions() {
        MyAchievementVO vo = new MyAchievementVO();
        Long memberId = memberIdSupport.currentMemberId(UserHolder.getUser());
        if (memberId == null) {
            return Result.ok(vo);
        }
        //队伍取在册且 ACTIVE 的（含队长标识）
        for (TeamMembership membership : teamQuery.loadMyTeamMemberships(memberId)) {
            if (!"ACTIVE".equals(membership.teamStatus())) {
                continue;
            }
            vo.getTeams().add(new MyAchievementVO.TeamBrief()
                    .setTeamId(membership.teamId())
                    .setTeamName(membership.teamName())
                    .setIsLeader(membership.isLeader()));
        }
        if (vo.getTeams().isEmpty()) {
            return Result.ok(vo);
        }
        List<Long> teamIds = vo.getTeams().stream().map(MyAchievementVO.TeamBrief::getTeamId).toList();
        Map<Long, String> teamNames = teamQuery.loadTeamNames(teamIds);
        List<Achievement> achievements = achievementMapper.selectList(
                new QueryWrapper<Achievement>().in("team_id", teamIds)
                        .orderByDesc("created_at"));
        achievements.forEach(achievement -> vo.getItems()
                .add(fillVO(new AchievementVO(), achievement, teamNames)));
        return Result.ok(vo);
    }

    /** 实体 → VO 公共字段填充（详情 VO 经泛型复用；接口字段名与实体列名的映射见 AchievementVO 注释） */
    private <V extends AchievementVO> V fillVO(V vo, Achievement achievement,
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

    /** 是否管理端角色（SUPER_ADMIN / DEPT_ADMIN） */
    private boolean isAdmin(CurrentUser user) {
        return user != null && ("SUPER_ADMIN".equals(user.getRole()) || "DEPT_ADMIN".equals(user.getRole()));
    }
}
