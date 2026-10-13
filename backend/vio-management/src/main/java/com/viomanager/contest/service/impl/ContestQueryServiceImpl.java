package com.viomanager.contest.service.impl;

import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.contest.mapper.ContestListRow;
import com.viomanager.contest.mapper.TrackRow;
import com.viomanager.contest.service.IContestQueryService;
import com.viomanager.contest.support.ContestCommandSupport;
import com.viomanager.contest.support.ContestRuleSupport;
import com.viomanager.contest.support.ContestRuleSupport.RuleBundle;
import com.viomanager.contest.vo.ContestDetailVO;
import com.viomanager.contest.vo.ContestListItemVO;
import com.viomanager.contest.vo.EffectiveRuleVO;
import com.viomanager.contest.vo.TrackVO;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * 比赛查询服务实现（C01 列表 / C02 详情）。
 * 数据范围：超管全部 / 部门管理员本组织子树 / 学生仅 PUBLISHED 及之后状态（DRAFT 不可见）。
 * 列表与详情走 JdbcTemplate 显式列名（禁 SELECT *），规则三级合并委托 {@link ContestRuleSupport}。
 */
@Slf4j
@Service
public class ContestQueryServiceImpl implements IContestQueryService {

    private static final String CONTEST_SELECT =
            "SELECT c.id, c.name, c.semester_id, s.name AS semester_name, c.template_id, c.org_id, "
                    + "c.status, c.register_start_at, c.register_end_at, c.contest_start_at, c.contest_end_at, "
                    + "c.contact_name, c.contact_phone, c.description, c.override_rules, c.source_type, "
                    + "(SELECT COUNT(*) FROM contest_track WHERE contest_id = c.id AND deleted = 0) AS track_count, "
                    + "(SELECT COUNT(*) FROM team WHERE contest_id = c.id AND deleted = 0 AND status = 'ACTIVE') AS team_count "
                    + "FROM contest c "
                    + "LEFT JOIN semester s ON c.semester_id = s.id AND s.deleted = 0 "
                    + "WHERE c.deleted = 0";

    /** 学生可见状态：PUBLISHED 及之后（不含 DRAFT） */
    private static final List<String> STUDENT_VISIBLE =
            List.of("PUBLISHED", "REGISTERING", "RUNNING", "FINISHED", "ARCHIVED");

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private ContestCommandSupport cmdSupport;
    @Resource
    private ContestRuleSupport ruleSupport;

    // ────────────────────────── C01 列表 ──────────────────────────

    @Override
    public Result<List<ContestListItemVO>> listContests(Long semesterId, String status) {
        CurrentUser user = UserHolder.getUser();
        String role = user.getRole();
        StringBuilder sql = new StringBuilder(CONTEST_SELECT);
        List<Object> params = new ArrayList<>();
        if ("SUPER_ADMIN".equals(role)) {
            // 超管全部，无额外条件
        } else if ("DEPT_ADMIN".equals(role)) {
            List<Long> orgIds = cmdSupport.querySubtreeOrgIds(user.getOrgId());
            if (orgIds.isEmpty()) {
                return Result.ok(Collections.emptyList());
            }
            sql.append(" AND c.org_id IN (");
            for (int i = 0; i < orgIds.size(); i++) {
                sql.append(i > 0 ? ",?" : "?");
            }
            sql.append(")");
            params.addAll(orgIds);
        } else {
            // 学生：仅 PUBLISHED 及之后，DRAFT 不可见
            sql.append(" AND c.status IN (");
            for (int i = 0; i < STUDENT_VISIBLE.size(); i++) {
                sql.append(i > 0 ? ",?" : "?");
            }
            sql.append(")");
            params.addAll(STUDENT_VISIBLE);
        }
        if (semesterId != null) {
            sql.append(" AND c.semester_id = ?");
            params.add(semesterId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND c.status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY c.created_at DESC");
        List<ContestListRow> rows = jdbcTemplate.query(sql.toString(), contestRowMapper(), params.toArray());
        List<ContestListItemVO> voList = new ArrayList<>(rows.size());
        for (ContestListRow row : rows) {
            voList.add(toListItemVO(row));
        }
        return Result.ok(voList);
    }

    // ────────────────────────── C02 详情 ──────────────────────────

    @Override
    public Result<ContestDetailVO> getContestDetail(Long id) {
        ContestListRow row = loadContestRow(id);
        CurrentUser user = UserHolder.getUser();
        if ("STUDENT".equals(user.getRole()) && "DRAFT".equals(row.getStatus())) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "比赛不存在");
        }
        RuleBundle contestBundle = ruleSupport.loadContestRules(id);
        ContestDetailVO vo = new ContestDetailVO();
        vo.setId(row.getId());
        vo.setName(row.getName());
        vo.setSemesterId(row.getSemesterId());
        vo.setSemesterName(row.getSemesterName());
        vo.setTemplateId(row.getTemplateId());
        vo.setTemplateName(loadTemplateName(row.getTemplateId()));
        vo.setStatus(row.getStatus());
        vo.setSignupStart(row.getRegisterStartAt());
        vo.setSignupEnd(row.getRegisterEndAt());
        vo.setContestStart(row.getContestStartAt());
        vo.setContestEnd(row.getContestEndAt());
        vo.setContact(buildContact(row.getContactName(), row.getContactPhone()));
        vo.setDescription(row.getDescription());
        vo.setOverrideRules(ruleSupport.parseJson(row.getOverrideRules()));
        vo.setRules(toEffectiveRules(contestBundle));
        vo.setTracks(loadTracks(id, contestBundle));
        return Result.ok(vo);
    }

    // ────────────────────────── 内部辅助 ──────────────────────────

    private RowMapper<ContestListRow> contestRowMapper() {
        return (rs, n) -> {
            ContestListRow r = new ContestListRow();
            r.setId(rs.getLong("id"));
            r.setName(rs.getString("name"));
            r.setSemesterId(rs.getObject("semester_id", Long.class));
            r.setSemesterName(rs.getString("semester_name"));
            r.setTemplateId(rs.getObject("template_id", Long.class));
            r.setOrgId(rs.getObject("org_id", Long.class));
            r.setStatus(rs.getString("status"));
            r.setRegisterStartAt(rs.getObject("register_start_at", LocalDateTime.class));
            r.setRegisterEndAt(rs.getObject("register_end_at", LocalDateTime.class));
            r.setContestStartAt(rs.getObject("contest_start_at", LocalDateTime.class));
            r.setContestEndAt(rs.getObject("contest_end_at", LocalDateTime.class));
            r.setContactName(rs.getString("contact_name"));
            r.setContactPhone(rs.getString("contact_phone"));
            r.setDescription(rs.getString("description"));
            r.setOverrideRules(rs.getString("override_rules"));
            r.setSourceType(rs.getString("source_type"));
            r.setTrackCount(rs.getInt("track_count"));
            r.setTeamCount(rs.getInt("team_count"));
            return r;
        };
    }

    private ContestListRow loadContestRow(Long id) {
        try {
            return jdbcTemplate.queryForObject(CONTEST_SELECT + " AND c.id = ?",
                    contestRowMapper(), id);
        } catch (EmptyResultDataAccessException e) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "比赛不存在");
        }
    }

    private String loadTemplateName(Long templateId) {
        if (templateId == null) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT name FROM contest_template WHERE id = ? AND deleted = 0",
                    String.class, templateId);
        } catch (EmptyResultDataAccessException e) {
            log.warn("模板 {} 不存在，templateName 置空", templateId);
            return null;
        }
    }

    private List<TrackVO> loadTracks(Long contestId, RuleBundle contestBundle) {
        String sql = "SELECT id, contest_id, name, code, override_rules, description, sort_no, status, "
                + "(SELECT COUNT(*) FROM team WHERE track_id = ct.id AND deleted = 0 AND status = 'ACTIVE') AS team_count "
                + "FROM contest_track ct WHERE ct.contest_id = ? AND ct.deleted = 0 ORDER BY ct.sort_no, ct.id";
        List<TrackRow> rows = jdbcTemplate.query(sql, (rs, n) -> {
            TrackRow r = new TrackRow();
            r.setId(rs.getLong("id"));
            r.setContestId(rs.getLong("contest_id"));
            r.setName(rs.getString("name"));
            r.setCode(rs.getString("code"));
            r.setOverrideRules(rs.getString("override_rules"));
            r.setDescription(rs.getString("description"));
            r.setSortNo(rs.getInt("sort_no"));
            r.setStatus(rs.getInt("status"));
            r.setTeamCount(rs.getInt("team_count"));
            return r;
        }, contestId);
        List<TrackVO> voList = new ArrayList<>(rows.size());
        for (TrackRow row : rows) {
            RuleBundle trackBundle = ruleSupport.loadTrackRules(contestBundle, row.getOverrideRules());
            TrackVO vo = new TrackVO();
            vo.setId(row.getId());
            vo.setName(row.getName());
            vo.setCode(row.getCode());
            vo.setDescription(row.getDescription());
            vo.setOverrideRules(ruleSupport.parseJson(row.getOverrideRules()));
            vo.setRules(toEffectiveRules(trackBundle));
            vo.setTeamCount(row.getTeamCount());
            voList.add(vo);
        }
        return voList;
    }

    private List<EffectiveRuleVO> toEffectiveRules(RuleBundle bundle) {
        List<EffectiveRuleVO> list = new ArrayList<>(bundle.getRules().size());
        bundle.getRules().forEach((k, v) -> {
            EffectiveRuleVO item = new EffectiveRuleVO();
            item.setKey(k);
            item.setValue(v);
            item.setSource(bundle.getSources().get(k));
            list.add(item);
        });
        return list;
    }

    private ContestListItemVO toListItemVO(ContestListRow row) {
        ContestListItemVO vo = new ContestListItemVO();
        vo.setId(row.getId());
        vo.setName(row.getName());
        vo.setSemesterId(row.getSemesterId());
        vo.setSemesterName(row.getSemesterName());
        vo.setStatus(row.getStatus());
        vo.setSignupStart(row.getRegisterStartAt());
        vo.setSignupEnd(row.getRegisterEndAt());
        vo.setContestStart(row.getContestStartAt());
        vo.setContestEnd(row.getContestEndAt());
        vo.setTrackCount(row.getTrackCount());
        vo.setTeamCount(row.getTeamCount());
        return vo;
    }

    private String buildContact(String name, String phone) {
        if (name == null && phone == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (name != null) {
            sb.append(name);
        }
        if (phone != null) {
            sb.append(' ').append(phone);
        }
        return sb.toString().trim();
    }
}
