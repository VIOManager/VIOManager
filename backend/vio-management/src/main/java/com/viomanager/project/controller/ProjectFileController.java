package com.viomanager.project.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.auth.RequireRole;
import com.viomanager.common.result.Result;
import com.viomanager.project.service.ProjectFileService;
import com.viomanager.project.vo.ProjectFileVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 项目文件接口（PR25–PR28），口径以 docs/api/项目管理-接口清单.md §3.6 为准。
 * 上传走骨架文件服务拿 fileId（F01：格式/大小/ClamAV 强制检测）；
 * 下载先过项目可见性再流式转发（MS04 口径），每次下载 @AuditLog 留痕（操作人/IP/时间）。
 * @Idempotent 暂不挂（同全站口径：前端未带 X-Idempotency-Key 头）。
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectFileController {

    @Resource
    private ProjectFileService fileService;

    /** PR25 文件列表：项目可见者可看 */
    @GetMapping("/{id}/files")
    public Result<List<ProjectFileVO>> list(@PathVariable("id") Long id) {
        return fileService.list(id);
    }

    /** PR26 上传：所有人与项目成员可传；multipart：file + fileCategory + remark */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @AuditLog(bizType = "PROJECT_FILE_UPLOAD", label = "上传项目文件",
            summary = "'项目' + #id + ' 上传文件：' + #file.originalFilename")
    @PostMapping("/{id}/files")
    public Result<ProjectFileVO> upload(@PathVariable("id") Long id,
                                        @RequestParam("file") MultipartFile file,
                                        @RequestParam(value = "fileCategory", required = false) String fileCategory,
                                        @RequestParam(value = "remark", required = false) String remark) {
        return fileService.upload(id, file, fileCategory, remark);
    }

    /** PR27 删除（逻辑删关联，文件对象保留）：所有人删任何；成员仅删自己上传的 */
    @RequireRole({"SUPER_ADMIN", "DEPT_ADMIN", "STUDENT"})
    @AuditLog(bizType = "PROJECT_FILE_DELETE", label = "删除项目文件",
            summary = "'项目' + #id + ' 删除文件记录 ' + #fileRecordId")
    @DeleteMapping("/{id}/files/{fileRecordId}")
    public Result<Void> delete(@PathVariable("id") Long id,
                               @PathVariable("fileRecordId") Long fileRecordId) {
        return fileService.delete(id, fileRecordId);
    }

    /** PR28 下载：先过项目可见性校验再流式转发；每次下载写审计 */
    @AuditLog(bizType = "PROJECT_FILE_DOWNLOAD", label = "下载项目文件",
            summary = "'下载项目文件记录 ' + #fileRecordId")
    @GetMapping("/files/{fileRecordId}/download")
    public void download(@PathVariable("fileRecordId") Long fileRecordId,
                         HttpServletResponse response) {
        fileService.download(fileRecordId, response);
    }
}
