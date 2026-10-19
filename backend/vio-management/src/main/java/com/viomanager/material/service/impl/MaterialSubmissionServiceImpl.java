package com.viomanager.material.service.impl;

import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.MaterialErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.file.config.FileProperties;
import com.viomanager.file.entity.FileObject;
import com.viomanager.file.service.IFileService;
import com.viomanager.material.dto.MaterialBatchUploadDTO;
import com.viomanager.material.dto.MaterialSubmitDTO;
import com.viomanager.material.entity.MaterialRequirement;
import com.viomanager.material.entity.MaterialSubmission;
import com.viomanager.material.entity.MaterialTask;
import com.viomanager.material.mapper.MaterialRequirementMapper;
import com.viomanager.material.mapper.MaterialSubmissionMapper;
import com.viomanager.material.mapper.MaterialTaskMapper;
import com.viomanager.material.service.MaterialCompletionService;
import com.viomanager.material.service.MaterialSubmissionService;
import com.viomanager.material.support.MaterialTeamQuerySupport;
import com.viomanager.material.support.MaterialTeamQuerySupport.TeamRow;
import com.viomanager.material.support.MemberIdSupport;
import com.viomanager.material.support.MaterialZipBatchSupport;
import com.viomanager.material.vo.BatchMatchResultVO;
import com.viomanager.material.vo.MaterialSubmitVO;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 材料提交服务实现（MS02 单份提交 / MS03 zip 批量上传）：校验 → 同事务落库 → 触发齐全率重算。
 * 校验顺序是产品口径（顺序错会误导用户，比如先报格式后报非队员），**不要调整顺序**；
 * 第 0 步的入参完整性拦截是空值防线——file_id / file_name / file_size 列 NOT NULL，
 * 空值放进 9 步链会在中途 NPE 或落库 500。
 *
 * 跨模块取数（B 线尚未建 team 包、CurrentUser 无 memberId）：
 *  - team / team_member 表 SQL 集中在 MaterialTeamQuerySupport（TODO(联调) 注释在该类逐方法标注）；
 *  - 当前用户的人员档案 ID 反查抽在 MemberIdSupport（material / achievement 两包共用）。
 */
@Service
public class MaterialSubmissionServiceImpl implements MaterialSubmissionService {

    @Resource
    private MaterialTaskMapper taskMapper;

    @Resource
    private MaterialRequirementMapper requirementMapper;

    @Resource
    private MaterialSubmissionMapper submissionMapper;

    @Resource
    private MaterialCompletionService completionService;

    @Resource
    private FileProperties fileProperties;

    @Resource
    private IFileService fileService;

    @Resource
    private MaterialTeamQuerySupport teamQuery;

    @Resource
    private MaterialZipBatchSupport zipBatch;

    @Resource
    private MemberIdSupport memberIdSupport;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<MaterialSubmitVO> submit(Long requirementId, MaterialSubmitDTO dto) {
        //0.入参完整性（空值防线，不属于 8 步业务校验链）
        if (dto.getTeamId() == null) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "teamId 不能为空");
        }
        if (dto.getFileId() == null) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "fileId 不能为空");
        }
        if (dto.getFileName() == null || dto.getFileName().isBlank()) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "fileName 不能为空");
        }
        if (dto.getFileSize() == null || dto.getFileSize() <= 0) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "fileSize 必须为正数（字节）");
        }
        //1.材料要求存在（selectById 经 @TableLogic 自动滤已删）
        MaterialRequirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "材料要求不存在");
        }
        //2.赛队存在
        TeamRow team = teamQuery.loadTeam(dto.getTeamId());
        if (team == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "赛队不存在");
        }
        //3.材料任务存在（requirement.task_id 是建要求时的冗余列）
        MaterialTask task = taskMapper.selectById(requirement.getTaskId());
        if (task == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "材料任务不存在");
        }
        //4.比赛匹配：要求随任务、队伍随比赛，两边必须同一比赛 → 5004
        if (!Objects.equals(task.getContestId(), team.getContestId())) {
            throw new BizException(MaterialErrorCode.CONTEST_MISMATCH);
        }
        //5.未过截止：deadline 为 null 视为不限期；now == deadline 仍可提交
        if (task.getDeadline() != null && LocalDateTime.now().isAfter(task.getDeadline())) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "已过截止时间，禁止提交或覆盖");
        }
        //6.在册成员：队长或队员均可（team_member.status CONFIRMED / UNCONFIRMED 都算在队）→ 5003
        //  ⚠️ 2026-10-19 组长拍板：部门管理员（DEPT_ADMIN）可代队伍提交材料，跳过在册校验；
        //     超级管理员仍不代提交（越权面过大），须用队员账号操作。
        //     代提交人记在 submitted_by / submitted_by_name，审计可追溯。
        //  ⚠️ submitted_by 列 NOT NULL（V1 DDL）：任何提交人都必须有人员档案。
        //     纯管理账号（sys_user.member_id 为 NULL）先挡在这里，避免把 null 写进 NOT NULL 列
        //     触发 SQL 异常变成"系统繁忙"，也避免报错文案含糊。
        // TODO(联调) 部门管理员目前不限组织子树（组长口径"能交就行"）；如需按部门收敛，在此加 org 子树判定。
        CurrentUser user = UserHolder.getUser();
        Long memberId = memberIdSupport.currentMemberId(user);
        if (memberId == null) {
            throw new BizException(MaterialErrorCode.NOT_TEAM_MEMBER,
                    "该账号未绑定人员档案，不能提交材料；请使用该队在册队员账号提交");
        }
        if (!isDeptAdmin(user) && !teamQuery.isTeamMember(dto.getTeamId(), memberId)) {
            throw new BizException(MaterialErrorCode.NOT_TEAM_MEMBER);
        }
        //7.文件归属：fileId 必须为当前用户上传，fileName / fileSize 以文件表权威值覆盖客户端自报
        //   （防拿别人的 fileId 挂名提交，也防假名假大小绕过白名单与上限）
        FileObject ownedFile = fileService.loadOwnedFile(dto.getFileId(), user.getUserId());
        dto.setFileName(ownedFile.getOriginalName());
        dto.setFileSize(ownedFile.getFileSize());
        //8.扩展名白名单：allowed_formats 为空表示不限 → 5001
        String formats = requirement.getFormats();
        if (formats != null && !formats.isBlank()) {
            List<String> allowed = splitFormats(formats);
            String ext = extractExt(dto.getFileName());
            if (!allowed.contains(ext)) {
                throw new BizException(MaterialErrorCode.FILE_FORMAT_NOT_ALLOWED,
                        "文件格式不允许，允许的格式：" + String.join(",", allowed));
            }
        }
        //9.大小上限：maxSizeMb 为空用全局配置 vio.file.max-size-mb（默认 20MB）→ 5002
        int limitMb = requirement.getMaxSizeMb() == null
                ? fileProperties.getMaxSizeMb() : requirement.getMaxSizeMb();
        if (dto.getFileSize() > limitMb * 1024L * 1024L) {
            throw new BizException(MaterialErrorCode.FILE_TOO_LARGE);
        }
        //覆盖上传（并发两道防线，见 MaterialSubmissionMapper 注释）：先锁同键行 → 失效旧版 → 插新版本
        submissionMapper.lockByRequirementAndTeam(requirementId, dto.getTeamId());
        submissionMapper.invalidateCurrent(requirementId, dto.getTeamId());
        Integer maxVersion = submissionMapper.selectMaxVersionNo(requirementId, dto.getTeamId());
        int newVersion = (maxVersion == null ? 0 : maxVersion) + 1;
        MaterialSubmission submission = new MaterialSubmission();
        submission.setRequirementId(requirementId);
        submission.setTeamId(dto.getTeamId());
        submission.setTaskId(task.getId());
        submission.setContestId(task.getContestId());
        submission.setSemesterId(task.getSemesterId());
        submission.setOrgId(team.getOrgId());
        submission.setFileId(dto.getFileId());
        submission.setFileName(dto.getFileName());
        submission.setFileSize(dto.getFileSize());
        submission.setIsCurrent(1);
        submission.setVersionNo(newVersion);
        submission.setSubmittedBy(memberId);
        submission.setSubmittedByName(user.getRealName());
        //submittedAt 必须手工赋值：AuditMetaObjectHandler 只填四个审计列，不管业务时间列
        submission.setSubmittedAt(LocalDateTime.now());
        submissionMapper.insert(submission);
        //提交成功 → 齐全率与队伍材料状态重算（同事务，重算失败提交一并回滚）
        completionService.recompute(dto.getTeamId());
        return Result.ok(new MaterialSubmitVO().setVersion(newVersion));
    }

    /**
     * zip 批量上传（MS03）：7 步校验（与单份提交同口径同顺序）→ 两层匹配与落库（MaterialZipBatchSupport，
     * 同一事务）→ 齐全率重算。"真实解压未经实测、当前走兜底匹配"的说明见该 Support 类 Javadoc。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<BatchMatchResultVO> batchUpload(MaterialBatchUploadDTO dto) {
        //0.入参完整性（空值防线，同单份提交第 0 步）
        if (dto.getTeamId() == null) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "teamId 不能为空");
        }
        if (dto.getTaskId() == null) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "taskId 不能为空");
        }
        if (dto.getFileId() == null) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "fileId 不能为空");
        }
        if (dto.getFileName() == null || dto.getFileName().isBlank()) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "fileName 不能为空");
        }
        //1.任务存在
        MaterialTask task = taskMapper.selectById(dto.getTaskId());
        if (task == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "材料任务不存在");
        }
        //2.赛队存在
        TeamRow team = teamQuery.loadTeam(dto.getTeamId());
        if (team == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "赛队不存在");
        }
        //3.比赛匹配 → 5004
        if (!Objects.equals(task.getContestId(), team.getContestId())) {
            throw new BizException(MaterialErrorCode.CONTEST_MISMATCH);
        }
        //4.未过截止（deadline 为 null = 不限期）→ 1006
        if (task.getDeadline() != null && LocalDateTime.now().isAfter(task.getDeadline())) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "已过截止时间，禁止提交");
        }
        //5.在册成员（队长或队员）→ 5003；部门管理员可代提交（同 MS02 第 6 步口径）。
        //  submitted_by 列 NOT NULL → 无人员档案的纯管理账号先行拦截，避免 SQL 异常。
        CurrentUser user = UserHolder.getUser();
        Long memberId = memberIdSupport.currentMemberId(user);
        if (memberId == null) {
            throw new BizException(MaterialErrorCode.NOT_TEAM_MEMBER,
                    "该账号未绑定人员档案，不能提交材料；请使用该队在册队员账号提交");
        }
        if (!isDeptAdmin(user) && !teamQuery.isTeamMember(dto.getTeamId(), memberId)) {
            throw new BizException(MaterialErrorCode.NOT_TEAM_MEMBER);
        }
        //6.文件归属：fileId 必须为当前用户上传，fileName / fileSize 以文件表权威值覆盖自报
        //   （zip 后缀判定用权威名；压缩包真实大小要落到各条合成提交记录上）
        FileObject ownedZip = fileService.loadOwnedFile(dto.getFileId(), user.getUserId());
        dto.setFileName(ownedZip.getOriginalName());
        //7.仅支持 zip 压缩包（忽略大小写）→ 5001
        if (!dto.getFileName().toLowerCase().endsWith(".zip")) {
            throw new BizException(MaterialErrorCode.FILE_FORMAT_NOT_ALLOWED, "批量上传仅支持 zip 压缩包");
        }
        //两层匹配 + 未交要求落库（同一事务），随后重算齐全率
        BatchMatchResultVO vo = zipBatch.matchAndInsert(task, team, memberId, user.getRealName(),
                dto, ownedZip.getFileSize() == null ? 0L : ownedZip.getFileSize());
        completionService.recompute(dto.getTeamId());
        return Result.ok(vo);
    }

    /**
     * 部门管理员判定（2026-10-19 组长拍板：部门管理员可代队伍提交材料，超级管理员不代提交）。
     * 口径与各 Controller 的 @RequireRole 中 DEPT_ADMIN 取值一致。
     */
    private static boolean isDeptAdmin(CurrentUser user) {
        return user != null && "DEPT_ADMIN".equals(user.getRole());
    }

    /** 从文件名提扩展名：小写、不含点；没有扩展名返回空串（与文件服务 FileServiceImpl 同款写法） */
    private String extractExt(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    /** 逗号分隔格式串拆成小写列表做白名单比对（管理员录入可能是大写，比对统一小写） */
    private List<String> splitFormats(String joined) {
        List<String> formats = new ArrayList<>();
        for (String part : joined.split(",")) {
            String trimmed = part.trim().toLowerCase();
            if (!trimmed.isEmpty()) {
                formats.add(trimmed);
            }
        }
        return formats;
    }
}
