package com.viomanager.contest.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 比赛规则三级继承合并 + 来源标注（§0 键名表）。
 * 模板 default_rules → 比赛 override_rules → 赛道 override_rules（后覆盖先，只存被覆盖的键）。
 * 来源标注：TEMPLATE / CONTEST / TRACK，供 C02 详情前端显示"是、否、跟随比赛规则"。
 * 显式列名查询，禁 SELECT *。
 */
@Slf4j
@Component
public class ContestRuleSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    public ContestRuleSupport(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 加载比赛级生效规则（模板默认 ← 比赛覆盖）+ 每项来源。
     * 不含赛道覆盖（赛道级规则在 C02 详情按赛道单独计算）。
     */
    public RuleBundle loadContestRules(Long contestId) {
        Map<String, Object> contestRow;
        try {
            contestRow = jdbcTemplate.queryForMap(
                    "SELECT id, name, template_id, override_rules "
                            + "FROM contest WHERE id = ? AND deleted = 0", contestId);
        } catch (EmptyResultDataAccessException e) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "比赛不存在");
        }
        Map<String, Object> rules = new LinkedHashMap<>();
        Map<String, String> sources = new HashMap<>();
        Long templateId = toLong(contestRow.get("template_id"));
        if (templateId != null) {
            try {
                Map<String, Object> tpl = jdbcTemplate.queryForMap(
                        "SELECT name, default_rules FROM contest_template WHERE id = ? AND deleted = 0",
                        templateId);
                Map<String, Object> def = parseJson(tpl.get("default_rules"));
                if (def != null) {
                    def.forEach((k, v) -> { rules.put(k, v); sources.put(k, "TEMPLATE"); });
                }
            } catch (EmptyResultDataAccessException e) {
                log.warn("模板 {} 不存在，规则基线为空", templateId);
            }
        }
        Map<String, Object> overlay = parseJson(contestRow.get("override_rules"));
        if (overlay != null) {
            overlay.forEach((k, v) -> { rules.put(k, v); sources.put(k, "CONTEST"); });
        }
        return new RuleBundle(rules, sources);
    }

    /**
     * 在比赛级规则之上叠加赛道覆盖，返回赛道级生效规则 + 来源。
     * 调用方须先 loadContestRules 得到比赛级 bundle，再传入赛道 override_rules。
     */
    public RuleBundle loadTrackRules(RuleBundle contestBundle, String trackOverrideJson) {
        Map<String, Object> rules = new LinkedHashMap<>(contestBundle.getRules());
        Map<String, String> sources = new HashMap<>(contestBundle.getSources());
        Map<String, Object> overlay = parseJson(trackOverrideJson);
        if (overlay != null) {
            overlay.forEach((k, v) -> { rules.put(k, v); sources.put(k, "TRACK"); });
        }
        return new RuleBundle(rules, sources);
    }

    /** 解析 JSON 规则列；空值或解析失败返回 null */
    public Map<String, Object> parseJson(Object json) {
        if (json == null) return null;
        String text = json.toString();
        if (text.isBlank() || "null".equals(text)) return null;
        try {
            return JSON.readValue(text, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("规则 JSON 解析失败，按无覆盖处理：{}", text);
            return null;
        }
    }

    /** Object → Long */
    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    /** 规则合并结果：生效规则 Map + 每键来源标注 */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class RuleBundle {
        private Map<String, Object> rules;
        private Map<String, String> sources;
    }
}
