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
import com.viomanager.achievement.support.AchievementQuerySupport;
import com.viomanager.achievement.vo.AchievementDetailVO;
import com.viomanager.achievement.vo.AchievementVO;
import com.viomanager.achievement.vo.MyAchievementVO;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.MaterialErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.file.entity.FileObject;
import com.viomanager.file.service.IFileService;
import com.viomanager.material.support.MaterialEvidenceLinker;
import com.viomanager.material.support.MaterialTeamQuerySupport;
import com.viomanager.material.support.MaterialTeamQuerySupport.TeamRow;
import com.viomanager.material.support.MemberIdSupport;
import com.viomanager.material.support.TeamOrgScopeSupport;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 成果服务实现——写口（A03 提交 / A04 编辑 / A05 删除 / A06 佐证上传）。
 * 读口（A01 列表 / A02 详情 / A08 我的成果）委托 AchievementQuerySupport；
 * A03 的"学生走审批 / 管理员直录"分支在 AchievementApprovalSupport；
 * team / team_member 访问一律走 MaterialTeamQuerySupport（sys_user 反查 memberId 走 MemberIdSupport）。
 */
@Service
public class AchievementServiceImpl implements AchievementService {

    /** A03 合法成果类型（接口清单口径五种；common/enums 无既有枚举，先在本类收敛，不新建枚举类） */
    private static final Set<String> ACHIEVEMENT_TYPES =
            Set.of("AWARD", "RATING", "QUALIFICATION", "ACTIVITY", "OTHER");

    @Resource
    private AchievementMapper achievementMapper;

    @Resource
    private AchievementFileMapper achievementFileMapper;

    @Resource
    private MaterialTeamQuerySupport teamQuery;

    @Resource
    private AchievementApprovalSupport approvalSupport;

    @Resource
    private AchievementQuerySupport querySupport;

    @Resource
    private IFileService fileService;

    @Resource
    private MemberIdSupport memberIdSupport;

    /** A06 佐证上传后补交材料侧系统佐证项（接口清单 §5「分子补齐」） */
    @Resource
    private MaterialEvidenceLinker evidenceLinker;

    @Resource
    private TeamOrgScopeSupport orgScope;

    @Override
    public Result<List<AchievementVO>> list(AchievementQueryDTO dto) {
        return querySupport.list(dto);
    }

    @Override
    public Result<AchievementDetailVO> detail(Long id) {
        return querySupport.detail(id);
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
        if (!ACHIEVEMENT_TYPES.contains(dto.getType())) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "成果类型非法");
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
        achievement.setContestId(team.getContestId());
        achievement.setSemesterId(team.getSemesterId());
        achievement.setOrgId(team.getOrgId());
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
        Map<Long, String> teamNameMap = new HashMap<>();
        teamNameMap.put(dto.getTeamId(), team.getName());
        return Result.ok(querySupport.fillVO(new AchievementVO(), achievement, teamNameMap));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AchievementUpdateDTO dto) {
        //0.入参完整性：name / award_date 列 NOT NULL，null 落库会触发 MySQL 1048 兜底 1000，先拦成 1001；
        //   level / description 列可空，传 null = 置空，不校验
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "成果名称不能为空");
        }
        if (dto.getAchievedAt() == null) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "获奖日期不能为空");
        }
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
        //0.5 部门管理员组织子树校验（越权 1003；超管/学生不受影响，学生由下方队长校验负责）
        orgScope.assertTeamInScope(achievement.getTeamId());
        //仅当前队长可上传/覆盖佐证（A06，5007）
        Long memberId = memberIdSupport.currentMemberId(UserHolder.getUser());
        if (memberId == null || achievement.getTeamId() == null
                || !teamQuery.isTeamLeader(achievement.getTeamId(), memberId)) {
            throw new BizException(MaterialErrorCode.EVIDENCE_LEADER_ONLY);
        }
        //文件归属：fileId 必须为当前队长本人上传，文件名/大小以文件表权威值覆盖自报（防伪造 fileId 与假名落库）
        FileObject ownedFile = fileService.loadOwnedFile(
                dto.getFileId(), UserHolder.getUser().getUserId());
        dto.setFileName(ownedFile.getOriginalName());
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
        //§5「分子补齐」：该队已有系统佐证要求时，把这份佐证同步记为材料侧已交并重算齐全率
        //（接口清单 §5 明确 A06 与 MS02 两条路径同效；否则队长传完佐证齐全率仍不回升）
        evidenceLinker.linkEvidenceSubmission(achievement.getTeamId(), achievement.getContestId(),
                dto.getFileId(), ownedFile.getOriginalName(), ownedFile.getFileSize(),
                memberId, UserHolder.getUser() == null ? null : UserHolder.getUser().getRealName());
    }

    @Override
    public Result<MyAchievementVO> myContributions() {
        return querySupport.myContributions();
    }
}
