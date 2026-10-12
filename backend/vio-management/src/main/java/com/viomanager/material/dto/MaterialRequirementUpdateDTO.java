package com.viomanager.material.dto;

import java.util.List;
import lombok.Data;

/**
 * 编辑材料要求入参（MR02）：id 走路径参数，body 字段集与创建一致。
 * PUT 全量替换语义——六个字段按请求体原样落库，maxSizeMb / namingRule 未传即置空
 * （分别恢复全局默认 / 清空），前端编辑页须回填全部字段再提交。
 * taskId / contestId / systemGenerated 不在修改范围（ taskId、contestId 锁死在原值，
 * 系统生成项整个接口直接 403 拒绝）。
 */
@Data
public class MaterialRequirementUpdateDTO {

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
