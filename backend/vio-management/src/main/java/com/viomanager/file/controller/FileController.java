package com.viomanager.file.controller;

import com.viomanager.common.audit.AuditLog;
import com.viomanager.common.idempotent.Idempotent;
import com.viomanager.common.result.Result;
import com.viomanager.file.service.IFileService;
import com.viomanager.file.vo.FileUploadVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件接口：上传与鉴权下载。接口口径以骨架接口清单 F01/F02 为准。
 * 登录即可调（细粒度权限在 service 层按角色判断）。
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    @Resource
    private IFileService fileService;

    /**
     * 上传：multipart 的 file + bizType + bizId（可空，先传后关联的场景）。
     * 带幂等键防重复上传；审计记"谁传了什么文件"（文件内容不进审计）。
     */
    @Idempotent(bizType = "FILE_UPLOAD")
    @AuditLog(bizType = "FILE_UPLOAD", label = "上传文件",
            summary = "'上传文件：' + #file.originalFilename + '，业务：' + #bizType")
    @PostMapping
    public Result<FileUploadVO> upload(@RequestParam("file") MultipartFile file,
                                       @RequestParam("bizType") String bizType,
                                       @RequestParam(value = "bizId", required = false) Long bizId) {
        return fileService.upload(file, bizType, bizId);
    }

    /**
     * 下载：鉴权流式回写，不暴露对象存储地址；每次下载写审计并累加下载计数。
     */
    @AuditLog(bizType = "FILE_DOWNLOAD", label = "下载文件", summary = "'下载文件：' + #fileId")
    @GetMapping("/{fileId}/download")
    public void download(@PathVariable("fileId") Long fileId, HttpServletResponse response) {
        fileService.download(fileId, response);
    }
}
