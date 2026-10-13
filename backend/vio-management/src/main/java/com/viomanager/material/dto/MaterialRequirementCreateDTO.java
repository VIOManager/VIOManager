package com.viomanager.material.dto;

import java.util.List;
import lombok.Data;

/**
 * 添加材料要求入参（MR01）：taskId 走路径参数，不在 body。
 * 校验在 service 层手写（每条文案口径照接口清单）：materialType 限 12 种预设、name 非空、
 * allowedFormats 至少一个、maxSizeMb 给了就须在 1–200 之间；
 * requiredFlag 可空（默认 1 必传，与表默认对齐），只接受 0 / 1。
 */
@Data
public class MaterialRequirementCreateDTO {

    /** 材料类型：12 种预设枚举之一（MaterialTypeEnum） */
    private String materialType;

    /** 要求名称 */
    private String name;

    /** 是否必传：1 必传 0 选传；空 = 默认 1（必传项决定齐全率分母） */
    private Integer requiredFlag;

    /** 允许格式列表，如 ["pdf","docx"]；落库时转逗号分隔串 */
    private List<String> allowedFormats;

    /** 单文件大小上限（MB）；空 = 用全局配置 vio.file.max-size-mb */
    private Integer maxSizeMb;

    /** 命名规则（可选），前端校验提示与 zip 批量匹配用 */
    private String namingRule;
}
