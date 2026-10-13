package com.viomanager.contest.vo;

import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 赛道出参（C02 详情内嵌 / C07/C08 返回）。
 * rules 为该赛道生效规则（含赛道覆盖），overrideRules 为原始赛道覆盖键。
 */
@Data
public class TrackVO {
    private Long id;
    private String name;
    private String code;
    private String description;
    /** 原始赛道覆盖规则 */
    private Map<String, Object> overrideRules;
    /** 该赛道生效规则（三级合并结果 + 来源标注） */
    private List<EffectiveRuleVO> rules;
    private Integer teamCount;
}
