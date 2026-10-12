package com.viomanager.material.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 材料要求出参（MR01 创建返回），字段口径 = C 线接口清单 §2 MR01。
 * 与实体形态的两处差异：
 *  - allowedFormats：库里是逗号分隔串，出参转回 List&lt;String&gt;（null / 空串 → 空 List）；
 *  - materialTypeLabel：materialType 的中文展示名（MaterialTypeEnum.labelOf）。
 */
@Data
public class MaterialRequirementVO {

    /** 要求 ID（JSON 序列化为字符串） */
    private Long id;

    /** 所属材料任务 ID */
    private Long taskId;

    /** 所属比赛 ID（冗余列，创建时随任务带出） */
    private Long contestId;

    /** 材料类型：12 种预设枚举之一 */
    private String materialType;

    /** 材料类型中文名（前端展示用） */
    private String materialTypeLabel;

    /** 要求名称 */
    private String name;

    /** 是否必传：1 必传 0 选传 */
    private Integer requiredFlag;

    /** 允许格式列表（库内逗号串拆回） */
    private List<String> allowedFormats;

    /** 单文件大小上限（MB），null = 用全局配置 */
    private Integer maxSizeMb;

    /** 命名规则（可选） */
    private String namingRule;

    /** 是否系统自动追加：1 是（成果佐证，管理端不可改删）0 否 */
    private Integer systemGenerated;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
