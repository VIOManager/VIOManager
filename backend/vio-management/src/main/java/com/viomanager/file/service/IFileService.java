package com.viomanager.file.service;

import com.viomanager.common.result.Result;
import com.viomanager.file.entity.FileObject;
import com.viomanager.file.vo.FileUploadVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务：上传（MinIO + 病毒扫描）与鉴权流式下载（骨架接口清单 F01/F02）。
 */
public interface IFileService {

    /**
     * 上传：白名单与大小校验 → MinIO 落盘 → 落库 → ClamAV 扫描。
     * CLEAN 放行返回 fileId；INFECTED 落库标记后抛 1010；扫描服务不可用记 UNAVAILABLE 抛 1011。
     */
    Result<FileUploadVO> upload(MultipartFile file, String bizType, Long bizId);

    /**
     * 下载：鉴权（超管全放行 / 部门管理员本部门子树 / 学生仅本人上传）→
     * 病毒检测非 CLEAN 拒绝 → MinIO 流式回写响应（不暴露对象地址）→ 计数 +1。
     */
    void download(Long fileId, HttpServletResponse response);

    /**
     * 业务侧文件归属校验（给材料/成果等业务模块用的口子）：fileId 必须存在且为当前登录人上传。
     * 不存在与不是本人上传统一报 1001（同文案，不给试探空间）；返回文件表权威记录，
     * 调用方应以 originalName / fileSize 覆盖客户端自报值，防止假文件名/假大小绕过校验落库。
     */
    FileObject loadOwnedFile(Long fileId, Long userId);
}
