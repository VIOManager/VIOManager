package com.viomanager.team.support;

import java.util.Map;
import lombok.Data;

/**
 * 比赛上下文（只读）：赛队校验所需的最小比赛信息 + 三级继承合并后的生效规则。
 * 规则键名见接口清单 §0：memberLimit / prefillLimit / allowRepeatJoin /
 * crossTeamLeader / maxAdvisorCount / crossTrackLimit。JSON 只存被覆盖的键，合并后即生效值。
 */
@Data
public class ContestContext {

    private Long contestId;
    private Long trackId;
    private String contestName;
    private String trackName;
    private String status;
    private Long semesterId;
    private Long orgId;
    private Long templateId;
    private String templateName;

    /** 生效规则（模板默认 ← 比赛覆盖 ← 赛道覆盖），值类型为 Integer / Boolean */
    private Map<String, Object> effectiveRules;

    /** 取数值型规则，缺失或类型不符返回默认值 */
    public int ruleInt(String key, int defVal) {
        if (effectiveRules == null) {
            return defVal;
        }
        Object v = effectiveRules.get(key);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        return defVal;
    }

    /** 取布尔型规则，缺失或类型不符返回默认值 */
    public boolean ruleBool(String key, boolean defVal) {
        if (effectiveRules == null) {
            return defVal;
        }
        Object v = effectiveRules.get(key);
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        return defVal;
    }
}
