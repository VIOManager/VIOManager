package com.viomanager.material.dto;

import lombok.Data;

/**
 * zip 批量上传入参（MS03）。
 * fileId 为先经文件服务 /api/files 上传压缩包得到的 ID，匹配到的多条要求共用它；
 * 匹配两层：优先真实解压条目名，对象存储不可用时以 fileName 本身做包含匹配（见 service 层 Javadoc）。
 */
@Data
public class MaterialBatchUploadDTO {

    /** 赛队 ID */
    private Long teamId;

    /** 材料任务 ID */
    private Long taskId;

    /** 压缩包文件对象 ID（多条要求共用同一个 fileId） */
    private Long fileId;

    /** 压缩包文件名（须 .zip 结尾，忽略大小写） */
    private String fileName;
}
