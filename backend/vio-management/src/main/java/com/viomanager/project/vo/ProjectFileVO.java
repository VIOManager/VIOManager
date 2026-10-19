package com.viomanager.project.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 项目文件列表项（PR25）：清单口径字段——文件名/大小/分类/检测状态/上传人/时间。
 */
@Data
public class ProjectFileVO {

    /** project_file 关联行 ID（删除接口用，区别于 fileId） */
    private Long id;

    /** 文件名冗余 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 分类：DOC / IMAGE / CODE / OTHER */
    private String fileCategory;

    /** 病毒检测状态：CLEAN / INFECTED / UNAVAILABLE */
    private String scanStatus;

    /** 上传人姓名 */
    private String uploadedByName;

    /** 上传时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
