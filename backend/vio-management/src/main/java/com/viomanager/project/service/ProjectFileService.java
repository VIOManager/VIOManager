package com.viomanager.project.service;

import com.viomanager.common.result.Result;
import com.viomanager.project.vo.ProjectFileVO;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 项目文件（PR25–PR28）：上传走骨架文件服务（F01：格式/大小/ClamAV 强制检测），
 * 下载先过项目可见性再 streamFile 流式转发（对齐 C 线 MS04 口径，不暴露对象地址）。
 */
public interface ProjectFileService {

    /** PR25 文件列表：项目可见者可看 */
    Result<List<ProjectFileVO>> list(Long projectId);

    /** PR26 上传：所有人与项目成员可传；先走骨架 F01 拿 fileId 再建关联 */
    Result<ProjectFileVO> upload(Long projectId, MultipartFile file, String fileCategory,
                                 String remark);

    /** PR27 删除（逻辑删关联，文件对象保留）：所有人删任何；成员仅删自己上传的 */
    Result<Void> delete(Long projectId, Long fileRecordId);

    /** PR28 下载：先过 §2 可见性校验再流式转发；每次下载写审计（@AuditLog 在 Controller） */
    void download(Long fileRecordId, HttpServletResponse response);
}
