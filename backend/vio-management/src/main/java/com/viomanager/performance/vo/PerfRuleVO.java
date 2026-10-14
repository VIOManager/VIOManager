package com.viomanager.performance.vo;

import java.math.BigDecimal;
import lombok.Data;

/**
 * PF11 打分规则维度出参（前端 PerfRule 消费形状）。
 */
@Data
public class PerfRuleVO {

    /** 维度键（大写蛇形，如 TECH_OUTPUT） */
    private String dimensionKey;

    /** 维度名（如「技术产出」） */
    private String dimensionName;

    /** 权重 */
    private BigDecimal weight;

    /** 满分 */
    private BigDecimal maxScore;

    /** 是否启用 */
    private Boolean enabled;

    /** 排序号 */
    private Integer sortNo;
}
