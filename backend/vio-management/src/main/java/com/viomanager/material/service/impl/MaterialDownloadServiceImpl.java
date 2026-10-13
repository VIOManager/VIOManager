package com.viomanager.material.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.MaterialErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.file.service.IFileService;
import com.viomanager.material.entity.MaterialRequirement;
import com.viomanager.material.entity.MaterialSubmission;
import com.viomanager.material.mapper.MaterialRequirementMapper;
import com.viomanager.material.mapper.MaterialSubmissionMapper;
import com.viomanager.material.service.MaterialDownloadService;
import com.viomanager.material.support.MaterialTeamQuerySupport;
import com.viomanager.material.support.MaterialTeamQuerySupport.TeamRow;
import com.viomanager.material.support.MemberIdSupport;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 材料下载实现（MS04/MS05）：
 * 权限口径——管理端（SUPER_ADMIN/DEPT_ADMIN）放行，学生须为该队在册成员，否则 5009；
 * 文件流一律走骨架 IFileService（病毒闸门、计数、对象地址隔离都在那边），本类不碰 MinIO。
 */
@Slf4j
@Service
public class MaterialDownloadServiceImpl implements MaterialDownloadService {

    @Resource
    private MaterialSubmissionMapper submissionMapper;

    @Resource
    private MaterialRequirementMapper requirementMapper;

    @Resource
    private MaterialTeamQuerySupport teamQuery;

    @Resource
    private MemberIdSupport memberIdSupport;

    @Resource
    private IFileService fileService;

    @Override
    public void downloadSubmission(Long submissionId, HttpServletResponse response) {
        //1.提交记录必须存在（1004）
        MaterialSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "提交记录不存在");
        }
        //2.权限：管理端或本队在册成员 → 5009
        requireTeamReadable(submission.getTeamId());
        //3.骨架口子流式下载（病毒闸门与计数在文件服务内）
        fileService.streamFile(submission.getFileId(), response);
    }

    @Override
    public void packageTeam(Long teamId, HttpServletResponse response) {
        //1.队伍必须存在（1004）
        TeamRow team = teamQuery.loadTeam(teamId);
        if (team == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "赛队不存在");
        }
        //2.权限：同 MS04 → 5009
        requireTeamReadable(teamId);
        //3.收集全部当前版本提交（按要求、版本排序保证 zip 条目顺序稳定）
        List<MaterialSubmission> submissions = submissionMapper.selectList(
                new QueryWrapper<MaterialSubmission>().eq("team_id", teamId)
                        .eq("is_current", 1).orderByAsc("requirement_id", "version_no"));
        if (submissions.isEmpty()) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "该队暂无可打包的材料");
        }
        //4.要求名 → 目录（批量查免 N+1）
        Map<Long, String> requirementNames = loadRequirementNames(submissions);
        //5.zip 流式回写：entry = 要求名/文件名，重名追加序号；染毒文件跳过留痕
        try {
            response.setContentType("application/zip");
            String encodedName = URLEncoder.encode(team.name() + "-材料打包.zip", StandardCharsets.UTF_8)
                    .replace("+", "%20");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);
            Set<String> usedEntryNames = new HashSet<>();
            try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream())) {
                for (MaterialSubmission submission : submissions) {
                    String entryName = uniqueEntryName(usedEntryNames,
                            folderName(requirementNames.get(submission.getRequirementId()))
                                    + "/" + submission.getFileName());
                    writeEntry(zip, entryName, submission.getFileId());
                }
            }
        } catch (IOException e) {
            log.error("全队材料打包失败，teamId={}：{}", teamId, e.getMessage());
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "材料打包失败");
        }
    }

    /** 下载权限：管理端放行；学生须为该队在册成员（与 MS02 同口径）→ 5009 */
    private void requireTeamReadable(Long teamId) {
        CurrentUser user = UserHolder.getUser();
        if (user != null && ("SUPER_ADMIN".equals(user.getRole())
                || "DEPT_ADMIN".equals(user.getRole()))) {
            return;
        }
        Long memberId = memberIdSupport.currentMemberId(user);
        if (memberId == null || !teamQuery.isTeamMember(teamId, memberId)) {
            throw new BizException(MaterialErrorCode.FILE_DOWNLOAD_FORBIDDEN);
        }
    }

    /** 批量查要求名（id → name），避免逐条 N+1 */
    private Map<Long, String> loadRequirementNames(List<MaterialSubmission> submissions) {
        Set<Long> ids = new HashSet<>();
        for (MaterialSubmission submission : submissions) {
            ids.add(submission.getRequirementId());
        }
        Map<Long, String> names = new HashMap<>();
        for (MaterialRequirement requirement : requirementMapper.selectBatchIds(ids)) {
            names.put(requirement.getId(), requirement.getName());
        }
        return names;
    }

    /** 写一个 zip 条目：染毒文件（病毒闸门 1006）跳过留痕，其余异常（存储故障等）整包失败 */
    private void writeEntry(ZipOutputStream zip, String entryName, Long fileId) throws IOException {
        try (InputStream in = fileService.openCheckedStream(fileId)) {
            zip.putNextEntry(new ZipEntry(entryName));
            byte[] buffer = new byte[8192];
            int len = in.read(buffer);
            while (len != -1) {
                zip.write(buffer, 0, len);
                len = in.read(buffer);
            }
            zip.closeEntry();
        } catch (BizException e) {
            if (e.getCode() == CommonErrorCode.STATE_NOT_ALLOWED.getCode()) {
                log.warn("全队打包跳过染毒文件：fileId={}", fileId);
                return;
            }
            throw e;
        }
    }

    /** 要求名转 zip 目录名：去掉路径分隔符等非法字符；空名兜底 */
    private String folderName(String requirementName) {
        if (requirementName == null || requirementName.isBlank()) {
            return "未命名要求";
        }
        return requirementName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    /** zip 条目名去重：重名在扩展名前追加 -2 -3，避免 ZipOutputStream 同名冲突 */
    private String uniqueEntryName(Set<String> used, String entryName) {
        if (used.add(entryName)) {
            return entryName;
        }
        int dot = entryName.lastIndexOf('.');
        String base = dot < 0 ? entryName : entryName.substring(0, dot);
        String ext = dot < 0 ? "" : entryName.substring(dot);
        int seq = 2;
        while (!used.add(base + "-" + seq + ext)) {
            seq++;
        }
        return base + "-" + seq + ext;
    }
}
