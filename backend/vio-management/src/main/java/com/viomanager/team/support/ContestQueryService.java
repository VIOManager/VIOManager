package com.viomanager.team.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 比赛只读供数（赛队校验的最小依赖，不写比赛数据）。
 * 显式列举列名查询，禁用 SELECT *。规则三级继承：
 *  模板 default_rules ← 比赛 override_rules ← 赛道 override_rules（后覆盖先，只存被覆盖的键）。
 * 待比赛 CRUD 模块（T/C/P 系列）落地后，可被正式 ContestService 替换。
 */
@Slf4j
@Service
public class ContestQueryService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    public ContestQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 加载比赛上下文：状态、学期、组织、模板、生效规则。
     * 比赛不存在抛 1004；赛道不存在（传了 trackId 但查不到/不属于该比赛）抛 1004。
     */
    public ContestContext loadContext(Long contestId, Long trackId) {
        // 1. 查比赛（显式列名）
        Map<String, Object> contestRow;
        try {
            contestRow = jdbcTemplate.queryForMap(
                    "SELECT id, name, template_id, semester_id, org_id, status, override_rules "
                            + "FROM contest WHERE id = ? AND deleted = 0",
                    contestId);
        } catch (EmptyResultDataAccessException e) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "比赛不存在");
        }
        ContestContext ctx = new ContestContext();
        ctx.setContestId(contestId);
        ctx.setContestName((String) contestRow.get("name"));
        ctx.setStatus((String) contestRow.get("status"));
        ctx.setSemesterId(toLong(contestRow.get("semester_id")));
        ctx.setOrgId(toLong(contestRow.get("org_id")));
        ctx.setTemplateId(toLong(contestRow.get("template_id")));

        // 2. 合并规则：模板默认 → 比赛覆盖 → 赛道覆盖
        Map<String, Object> rules = new HashMap<>();
        if (ctx.getTemplateId() != null) {
            loadTemplate(ctx, rules);
        }
        mergeOverlay(rules, parseJson(contestRow.get("override_rules")));
        if (trackId != null) {
            loadTrack(ctx, contestId, trackId, rules);
        }
        ctx.setEffectiveRules(rules);
        return ctx;
    }

    /** 查模板默认规则 + 名称，作为规则基线 */
    private void loadTemplate(ContestContext ctx, Map<String, Object> rules) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT name, default_rules FROM contest_template WHERE id = ? AND deleted = 0",
                    ctx.getTemplateId());
            ctx.setTemplateName((String) row.get("name"));
            mergeOverlay(rules, parseJson(row.get("default_rules")));
        } catch (EmptyResultDataAccessException e) {
            log.warn("比赛模板 {} 不存在，规则基线为空", ctx.getTemplateId());
        }
    }

    /** 查赛道覆盖规则 + 名称（校验赛道属于该比赛） */
    private void loadTrack(ContestContext ctx, Long contestId, Long trackId, Map<String, Object> rules) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT name, override_rules FROM contest_track "
                            + "WHERE id = ? AND contest_id = ? AND deleted = 0",
                    trackId, contestId);
            ctx.setTrackName((String) row.get("name"));
            mergeOverlay(rules, parseJson(row.get("override_rules")));
        } catch (EmptyResultDataAccessException e) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "赛道不存在");
        }
    }

    /** 把一段规则 JSON 合并进目标 Map（后覆盖先） */
    private void mergeOverlay(Map<String, Object> target, Map<String, Object> overlay) {
        if (overlay != null) {
            target.putAll(overlay);
        }
    }

    /** 解析 JSON 规则列；空值或解析失败返回 null（按"无覆盖"处理） */
    private Map<String, Object> parseJson(Object json) {
        if (json == null) {
            return null;
        }
        String text = json.toString();
        if (text.isBlank() || "null".equals(text)) {
            return null;
        }
        try {
            return JSON.readValue(text, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("规则 JSON 解析失败，按无覆盖处理：{}", text);
            return null;
        }
    }

    /** Object → Long（BigDecimal/Number 统一收口） */
    private Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        try {
            return Long.valueOf(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
