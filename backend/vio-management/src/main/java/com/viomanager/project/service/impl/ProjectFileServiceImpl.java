package com.viomanager.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.ProjectErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.file.entity.FileObject;
import com.viomanager.file.service.IFileService;
import com.viomanager.file.vo.FileUploadVO;
import com.viomanager.material.support.MemberIdSupport;
import com.viomanager.project.entity.Project;
import com.viomanager.project.entity.ProjectFile;
import com.viomanager.project.mapper.ProjectFileMapper;
import com.viomanager.project.mapper.ProjectMapper;
import com.viomanager.project.service.ProjectFileService;
import com.viomanager.project.support.ProjectVisibilitySupport;
import com.viomanager.project.vo.ProjectFileVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 项目文件实现（PR25–PR28）。
 * 上传两段式（清单 §3.6）：骨架 F01（白名单/大小/ClamAV 强制检测，INFECTED 抛 1010）
 * → 拿 fileId 后建 project_file 关联；originalName / fileSize 以 file_object 权威值
 * 覆盖（loadOwnedFile 顺带校验了本人上传）。
 * 下载：先过项目可见性八条规则（7001/7003）再 streamFile（保留病毒闸门与计数，
 * 不暴露对象存储地址）；审计由 Controller 层 @AuditLog 记（操作人/IP/时间全覆盖）。
 */
@Slf4j
@Service
public class ProjectFileServiceImpl implements ProjectFileService {

    /** 文件分类合法值（清单 §3.6） */
    private static final Set<String> FILE_CATEGORIES = Set.of("DOC", "IMAGE", "CODE", "OTHER");

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ProjectFileMapper fileMapper;

    @Resource
    private ProjectVisibilitySupport visibilitySupport;

    @Resource
    private MemberIdSupport memberIdSupport;

    @Resource
    private IFileService fileService;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public Result<List<ProjectFileVO>> list(Long projectId) {
        CurrentUser user = UserHolder.getUser();
        Long viewerId = memberIdSupport.currentMemberId(user);
        visibilitySupport.requireVisibleProject(projectId, user, viewerId);
        List<ProjectFileVO> vos = fileMapper.selectList(
                        new QueryWrapper<ProjectFile>().eq("project_id", projectId)
                                .orderByDesc("created_at").orderByDesc("id"))
                .stream().map(this::toVO).toList();
        return Result.ok(vos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<ProjectFileVO> upload(Long projectId, MultipartFile file, String fileCategory,
                                        String remark) {
        CurrentUser user = UserHolder.getUser();
        Long uploaderId = memberIdSupport.currentMemberId(user);
        Project project = requireProject(projectId);
        //上传权限比其他写口宽：所有人与项目成员都可传（指导教师挂名只读，清单字面）
        requireOwnerOrMember(project, uploaderId);
        String category = StringUtils.hasText(fileCategory) ? fileCategory : "DOC";
        if (!FILE_CATEGORIES.contains(category)) {
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }
        //1.骨架 F01：格式/大小校验 + MinIO 落盘 + ClamAV 强制检测（INFECTED 抛 1010 拒绝）
        Result<FileUploadVO> uploaded = fileService.upload(file, "PROJECT_FILE", projectId);
        if (uploaded.getCode() != 0 || uploaded.getData() == null) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR);
        }
        Long fileId = uploaded.getData().getFileId();
        //2.权威值覆盖：originalName / fileSize 以 file_object 为准（防假文件名/假大小落库）
        FileObject authoritative = fileService.loadOwnedFile(fileId, user.getUserId());
        //3.建关联
        ProjectFile record = new ProjectFile();
        record.setProjectId(projectId);
        record.setFileId(fileId);
        record.setFileName(authoritative.getOriginalName());
        record.setFileSize(authoritative.getFileSize());
        record.setFileCategory(category);
        record.setScanStatus(uploaded.getData().getScanStatus());
        record.setUploadedBy(uploaderId);
        record.setUploadedByName(user.getRealName());
        record.setRemark(remark);
        fileMapper.insert(record);
        return Result.ok(toVO(record));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(Long projectId, Long fileRecordId) {
        CurrentUser user = UserHolder.getUser();
        Long memberId = memberIdSupport.currentMemberId(user);
        Project project = requireProject(projectId);
        ProjectFile record = fileMapper.selectById(fileRecordId);
        if (record == null || !projectId.equals(record.getProjectId())) {
            throw new BizException(CommonErrorCode.NOT_FOUND);
        }
        boolean owner = memberId != null && memberId.equals(project.getOwnerMemberId());
        //所有人删任何；成员仅删自己上传的；其余 7002
        if (!owner && !(memberId != null && memberId.equals(record.getUploadedBy())
                && isMember(projectId, memberId))) {
            throw new BizException(ProjectErrorCode.NOT_OWNER);
        }
        //逻辑删关联，文件对象保留（对象存储与 file_object 记录不动）
        fileMapper.deleteById(fileRecordId);
        return Result.ok();
    }

    @Override
    public void download(Long fileRecordId, HttpServletResponse response) {
        CurrentUser user = UserHolder.getUser();
        Long viewerId = memberIdSupport.currentMemberId(user);
        ProjectFile record = fileMapper.selectById(fileRecordId);
        if (record == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND);
        }
        //§2 项目可见性先于文件（7001/7003）——见得到的文件才能下载
        visibilitySupport.requireVisibleProject(record.getProjectId(), user, viewerId);
        //业务校验后的流式转发：保留病毒闸门与下载计数，不暴露对象地址
        fileService.streamFile(record.getFileId(), response);
    }

    // ---------------------------------------------------------------- 支撑方法

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(ProjectErrorCode.NOT_FOUND);
        }
        return project;
    }

    /** 上传门槛：所有人或项目成员（PR26 口径；成员判定查 project_member 含 OWNER 行） */
    private void requireOwnerOrMember(Project project, Long memberId) {
        if (memberId == null) {
            throw new BizException(ProjectErrorCode.NOT_OWNER);
        }
        if (memberId.equals(project.getOwnerMemberId()) || isMember(project.getId(), memberId)) {
            return;
        }
        throw new BizException(ProjectErrorCode.NOT_OWNER);
    }

    private boolean isMember(Long projectId, Long memberId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project_member WHERE project_id = ? AND member_id = ? "
                        + "AND deleted = 0",
                Integer.class, projectId, memberId);
        return count != null && count > 0;
    }

    private ProjectFileVO toVO(ProjectFile record) {
        ProjectFileVO vo = new ProjectFileVO();
        vo.setId(record.getId());
        vo.setFileName(record.getFileName());
        vo.setFileSize(record.getFileSize());
        vo.setFileCategory(record.getFileCategory());
        vo.setScanStatus(record.getScanStatus());
        vo.setUploadedByName(record.getUploadedByName());
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }
}
