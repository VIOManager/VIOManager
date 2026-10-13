package com.viomanager.material.vo;

import lombok.Data;

/**
 * 单个要求在某队下的提交状态行（MS01 概览）。
 * 未提交口径：status=NOT_SUBMITTED、version=0、fileName / uploadedAt 为空串。
 */
@Data
public class MaterialSubmitInfoVO {

    /** 材料要求 ID（JSON 序列化为字符串） */
    private Long requirementId;

    /** 要求名称 */
    private String requirementName;

    /** 材料类型（12 种预设枚举之一） */
    private String materialType;

    /** 材料类型中文名（MaterialTypeEnum.labelOf） */
    private String materialTypeLabel;

    /** 提交状态：SUBMITTED 已有当前版 / NOT_SUBMITTED 未提交 */
    private String status;

    /** 当前版本号；未提交为 0 */
    private Integer version;

    /** 当前版文件名；未提交为空串 */
    private String fileName;

    /** 当前版提交时间（yyyy-MM-dd HH:mm:ss）；未提交为空串 */
    private String uploadedAt;
}
