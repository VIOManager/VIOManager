package com.viomanager.file.service;

import com.viomanager.common.result.Result;
import com.viomanager.file.entity.FileObject;
import com.viomanager.file.vo.FileUploadVO;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
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

    /**
     * 业务层已自行完成权限校验后的流式下载（MS04 单文件下载等场景）：
     * 保留病毒闸门（INFECTED 拒绝）与下载计数，不做角色矩阵——
     * 调用方必须先做完自己的业务权限判定（如本队成员校验）再调这里。
     */
    void streamFile(Long fileId, HttpServletResponse response);

    /**
     * 打开已过病毒闸门的对象流（MS05 全队打包等场景）：返回 MinIO 对象流，调用方负责 close。
     * 文件不存在抛 1004；染毒抛 1006（调用方决定跳过还是整包失败）。
     */
    InputStream openCheckedStream(Long fileId);
}
