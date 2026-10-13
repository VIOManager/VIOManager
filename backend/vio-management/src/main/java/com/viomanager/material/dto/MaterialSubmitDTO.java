package com.viomanager.material.dto;

import lombok.Data;

/**
 * 材料单份提交入参（MS02）。requirementId 以路径参数为权威来源，body 内同名字段仅作前端回显冗余。
 * fileId 来自文件服务 /api/files 上传返回；覆盖上传不需要额外参数，换新 fileId 再次提交即可
 * （旧版本 is_current 翻 0，版本号 +1）。
 */
@Data
public class MaterialSubmitDTO {

    /** 材料要求 ID（冗余镜像：以路径参数为准，服务端不读它） */
    private Long requirementId;

    /** 赛队 ID */
    private Long teamId;

    /** 文件对象 ID（文件服务 /api/files 的 fileId） */
    private Long fileId;

    /** 文件名（含扩展名，用于白名单校验与列表展示） */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;
}
