package com.viomanager.performance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 子组织绩效规则实体，对应表 performance_rule（V1__init_52_tables.sql 第 1045–1064 行）。
 * 规则匹配：优先「子组织+学期」特定规则，无则回退子组织默认（semester_id 为空）；
 * 子组织未配置任何 enabled 规则时无法打分（8046，实施顺序规则先行）。
 * UNIQUE KEY (sub_org_id, semester_id, dimension_key, deleted)，NULL 学期不去重由 service 兜底。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("performance_rule")
public class PerformanceRule extends BaseEntity {

    /** 子组织 ID */
    private Long subOrgId;

    /** 学期 ID，为空表示对所有学期生效 */
    private Long semesterId;

    /** 打分维度键，如 TECH_OUTPUT / COLLABORATION / ATTENDANCE */
    private String dimensionKey;

    /** 维度名称，如「技术产出」 */
    private String dimensionName;

    /** 权重，参与总分加权计算 */
    private BigDecimal weight;

    /** 该维度满分 */
    private BigDecimal maxScore;

    /** 维度说明，展示给打分人 */
    private String description;

    /** 排序号 */
    private Integer sortNo;

    /** 是否启用：1 启用 0 停用 */
    private Integer enabled;
}
