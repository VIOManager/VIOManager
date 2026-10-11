package com.viomanager.material.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 材料要求实体，对应表 material_requirement。
 * 字段与 V1__init_52_tables.sql 第 418–438 行一一对应。
 *
 * ⚠️ 三个字段名与 Java 属性不同名，已用 @TableField 显式映射：
 *    required_flag     → required
 *    allowed_formats   → formats
 *    max_size_mb       → maxSizeMb
 * 另有 material_type → materialType 因驼峰转换规则可自动映射，但仍显式标注避免歧义。
 *
 * ⚠️ 12 种 materialType 取值以接口清单第 35 行为准：
 *    REGISTRATION_FORM 报名表 / PROJECT_PLAN 项目书 / COMMITMENT 承诺书 / ID_COPY 身份证复印件 /
 *    STUDENT_CARD_COPY 学生证复印件 / TEAM_PHOTO 团队合照 / DEFENSE_PPT 答辩PPT /
 *    RESEARCH_REPORT 研究报告 / SOURCE_CODE 源代码 / AWARD_CERT 获奖证书 / EVIDENCE 佐证材料 / OTHER 其他
 *    建议用 MaterialTypeEnum 常量或校验器约束，不要散落魔法字符串。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("material_requirement")
public class MaterialRequirement extends BaseEntity {

    /** 所属材料任务 ID */
    private Long taskId;

    /** 所属比赛 ID（冗余，专用于校验「赛队比赛匹配」，见 5004） */
    private Long contestId;

    /** 材料类型：12 种预设枚举之一 */
    @TableField("material_type")
    private String materialType;

    /** 要求名称，展示用（出参字段 name） */
    private String name;

    /** 是否必传：1 必传 0 选传。必传项决定齐全率分母 */
    @TableField("required_flag")
    private Integer requiredFlag;

    /** 允许格式，逗号分隔如 pdf,docx,jpg；为空表示不限 */
    @TableField("allowed_formats")
    private String formats;

    /** 单文件大小上限（MB），为空表示用全局配置 vio.file.max-size-mb=20 */
    @TableField("max_size_mb")
    private Integer maxSizeMb;

    /** 命名规则（可选），前端校验提示与 zip 批量匹配用 */
    private String namingRule;

    /**
     * 是否系统自动追加：1 是（成果佐证材料），0 否。
     * ⚠️ §5 联动会产生本字段为 1 的要求，管理端不可增删（MR02/MR03 必须拦截）。
     */
    @TableField("system_generated")
    private Integer systemGenerated;

    /** 排序号 */
    private Integer sortNo;
}
