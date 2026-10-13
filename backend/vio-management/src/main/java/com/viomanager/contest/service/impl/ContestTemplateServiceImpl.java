package com.viomanager.contest.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.TeamErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.common.util.ParamsJsonUtil;
import com.viomanager.contest.dto.TemplateCreateDTO;
import com.viomanager.contest.entity.ContestTemplate;
import com.viomanager.contest.mapper.ContestTemplateMapper;
import com.viomanager.contest.mapper.TemplateListRow;
import com.viomanager.contest.service.IContestTemplateService;
import com.viomanager.contest.support.ContestCommandSupport;
import com.viomanager.contest.support.ContestRuleSupport;
import com.viomanager.contest.vo.TemplateVO;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 比赛模板服务实现（T01–T05）。
 * 列表/详情走 JdbcTemplate 显式列名 + refCount 子查询；CRUD 走 BaseMapper。
 * defaultRules Map ↔ JSON 字符串转换用 ParamsJsonUtil / ContestRuleSupport.parseJson。
 */
@Slf4j
@Service
public class ContestTemplateServiceImpl implements IContestTemplateService {

    private static final String SELECT_COLS =
            "id, name, code, category, description, default_rules, status, sort_no";

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private ContestTemplateMapper templateMapper;
    @Resource
    private ContestCommandSupport cmdSupport;
    @Resource
    private ContestRuleSupport ruleSupport;

    // ────────────────────────── T01 列表 ──────────────────────────

    @Override
    public Result<List<TemplateVO>> listTemplates(String category) {
        StringBuilder sql = new StringBuilder()
                .append("SELECT ").append(SELECT_COLS)
                .append(", (SELECT COUNT(*) FROM contest WHERE template_id = ct.id AND deleted = 0) AS ref_count")
                .append(" FROM contest_template ct WHERE ct.deleted = 0");
        List<Object> params = new ArrayList<>();
        if (category != null && !category.isBlank()) {
            sql.append(" AND ct.category = ?");
            params.add(category);
        }
        sql.append(" ORDER BY ct.sort_no, ct.created_at");
        List<TemplateListRow> rows = jdbcTemplate.query(sql.toString(), (rs, n) -> {
            TemplateListRow r = new TemplateListRow();
            r.setId(rs.getLong("id"));
            r.setName(rs.getString("name"));
            r.setCode(rs.getString("code"));
            r.setCategory(rs.getString("category"));
            r.setDescription(rs.getString("description"));
            r.setDefaultRules(rs.getString("default_rules"));
            r.setStatus(rs.getInt("status"));
            r.setSortNo(rs.getInt("sort_no"));
            r.setRefCount(rs.getInt("ref_count"));
            return r;
        }, params.toArray());
        List<TemplateVO> voList = new ArrayList<>(rows.size());
        for (TemplateListRow row : rows) {
            voList.add(toVO(row));
        }
        return Result.ok(voList);
    }

    // ────────────────────────── T02 创建 ──────────────────────────

    @Override
    @Transactional
    public Result<TemplateVO> createTemplate(TemplateCreateDTO dto) {
        ensureNameUnique(dto.getName(), null);
        ContestTemplate tpl = new ContestTemplate();
        tpl.setName(dto.getName());
        tpl.setCode(dto.getCode());
        tpl.setCategory(dto.getCategory());
        tpl.setDescription(dto.getDescription());
        tpl.setDefaultRules(toJson(dto.getDefaultRules()));
        tpl.setStatus(1);
        tpl.setSortNo(0);
        templateMapper.insert(tpl);
        TemplateVO vo = toVO(tpl, 0);
        return Result.ok(vo);
    }

    // ────────────────────────── T03 详情 ──────────────────────────

    @Override
    public Result<TemplateVO> getTemplate(Long id) {
        TemplateListRow row = loadTemplateRow(id);
        return Result.ok(toVO(row));
    }

    // ────────────────────────── T04 编辑 ──────────────────────────

    @Override
    @Transactional
    public Result<TemplateVO> updateTemplate(Long id, TemplateCreateDTO dto) {
        ContestTemplate tpl = loadTemplateEntity(id);
        ensureNameUnique(dto.getName(), id);
        UpdateWrapper<ContestTemplate> uw = new UpdateWrapper<>();
        uw.eq("id", id).eq("deleted", 0)
                .set("name", dto.getName())
                .set("code", dto.getCode())
                .set("category", dto.getCategory())
                .set("description", dto.getDescription())
                .set("default_rules", toJson(dto.getDefaultRules()));
        templateMapper.update(null, uw);
        tpl.setName(dto.getName());
        tpl.setCode(dto.getCode());
        tpl.setCategory(dto.getCategory());
        tpl.setDescription(dto.getDescription());
        tpl.setDefaultRules(toJson(dto.getDefaultRules()));
        return Result.ok(toVO(tpl, cmdSupport.countTemplateRefs(id)));
    }

    // ────────────────────────── T05 删除 ──────────────────────────

    @Override
    @Transactional
    public Result<Void> deleteTemplate(Long id) {
        loadTemplateEntity(id);
        if (cmdSupport.countTemplateRefs(id) > 0) {
            throw new BizException(TeamErrorCode.TEMPLATE_REFERENCED);
        }
        templateMapper.deleteById(id);
        return Result.ok();
    }

    // ────────────────────────── 内部辅助 ──────────────────────────

    private void ensureNameUnique(String name, Long excludeId) {
        String sql = "SELECT COUNT(*) FROM contest_template WHERE name = ? AND deleted = 0"
                + (excludeId != null ? " AND id <> ?" : "");
        Integer c = excludeId != null
                ? jdbcTemplate.queryForObject(sql, Integer.class, name, excludeId)
                : jdbcTemplate.queryForObject(sql, Integer.class, name);
        if (c != null && c > 0) {
            throw new BizException(CommonErrorCode.DUPLICATE_SUBMIT, "模板名已存在");
        }
    }

    private TemplateListRow loadTemplateRow(Long id) {
        try {
            String sql = "SELECT " + SELECT_COLS
                    + ", (SELECT COUNT(*) FROM contest WHERE template_id = ct.id AND deleted = 0) AS ref_count"
                    + " FROM contest_template ct WHERE ct.id = ? AND ct.deleted = 0";
            return jdbcTemplate.queryForObject(sql, (rs, n) -> {
                TemplateListRow r = new TemplateListRow();
                r.setId(rs.getLong("id"));
                r.setName(rs.getString("name"));
                r.setCode(rs.getString("code"));
                r.setCategory(rs.getString("category"));
                r.setDescription(rs.getString("description"));
                r.setDefaultRules(rs.getString("default_rules"));
                r.setStatus(rs.getInt("status"));
                r.setSortNo(rs.getInt("sort_no"));
                r.setRefCount(rs.getInt("ref_count"));
                return r;
            }, id);
        } catch (EmptyResultDataAccessException e) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "模板不存在");
        }
    }

    private ContestTemplate loadTemplateEntity(Long id) {
        ContestTemplate tpl = templateMapper.selectById(id);
        if (tpl == null || (tpl.getDeleted() != null && tpl.getDeleted() == 1)) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "模板不存在");
        }
        return tpl;
    }

    private String toJson(Map<String, Object> rules) {
        return rules == null ? null : ParamsJsonUtil.toJson(rules);
    }

    private TemplateVO toVO(TemplateListRow row) {
        TemplateVO vo = new TemplateVO();
        vo.setId(row.getId());
        vo.setName(row.getName());
        vo.setCode(row.getCode());
        vo.setCategory(row.getCategory());
        vo.setDescription(row.getDescription());
        vo.setDefaultRules(ruleSupport.parseJson(row.getDefaultRules()));
        vo.setEnabled(row.getStatus() != null && row.getStatus() == 1);
        vo.setRefCount(row.getRefCount());
        return vo;
    }

    private TemplateVO toVO(ContestTemplate tpl, int refCount) {
        TemplateVO vo = new TemplateVO();
        vo.setId(tpl.getId());
        vo.setName(tpl.getName());
        vo.setCode(tpl.getCode());
        vo.setCategory(tpl.getCategory());
        vo.setDescription(tpl.getDescription());
        vo.setDefaultRules(ruleSupport.parseJson(tpl.getDefaultRules()));
        vo.setEnabled(tpl.getStatus() != null && tpl.getStatus() == 1);
        vo.setRefCount(refCount);
        return vo;
    }
}
