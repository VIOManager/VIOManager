package com.viomanager.contest.vo;

import lombok.Data;

/**
 * 生效规则项出参（C02 详情内嵌）。
 * 三级继承合并结果 + 来源标注：TEMPLATE 模板默认 / CONTEST 比赛覆盖 / TRACK 赛道覆盖。
 * 前端据此显示"是、否、跟随比赛规则"。
 */
@Data
public class EffectiveRuleVO {
    /** 规则键名，见 §0 */
    private String key;
    /** 规则值：数值型(Integer)或布尔型(Boolean) */
    private Object value;
    /** 来源：TEMPLATE / CONTEST / TRACK */
    private String source;
}
