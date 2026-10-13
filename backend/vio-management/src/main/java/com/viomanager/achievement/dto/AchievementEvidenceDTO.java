package com.viomanager.achievement.dto;

import lombok.Data;

/**
 * 佐证上传/覆盖入参（A06）：fileId 来自文件服务 /api/files 上传返回，单文件覆盖式。
 * （清单外补充文件：A06 的 body { fileId, fileName } 无既有 DTO 可承载，经组长批准新建。）
 */
@Data
public class AchievementEvidenceDTO {

    /** 文件对象 ID（文件服务 /api/files 的 fileId） */
    private Long fileId;

    /** 文件名 */
    private String fileName;
}
