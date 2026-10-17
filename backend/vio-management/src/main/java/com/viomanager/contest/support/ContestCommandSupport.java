package com.viomanager.contest.support;

import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.TeamErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.datascop.OrgScopeSupport;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 比赛写操作数据访问辅助：校验类 COUNT 查询 + 状态机判定 + 组织子树辅助。
 * 所有 SQL 显式列名，禁用 SELECT *。比赛状态机（4011）与模板引用检查（4012）在此收口。
 */
@Slf4j
@Component
public class ContestCommandSupport {

    private final JdbcTemplate jdbcTemplate;
    private final OrgScopeSupport orgScopeSupport;

    public ContestCommandSupport(JdbcTemplate jdbcTemplate, OrgScopeSupport orgScopeSupport) {
        this.jdbcTemplate = jdbcTemplate;
        this.orgScopeSupport = orgScopeSupport;
    }

    /** 模板被比赛引用计数（>0 时禁止删除 4012） */
    public int countTemplateRefs(Long templateId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contest WHERE template_id = ? AND deleted = 0",
                Integer.class, templateId);
        return c == null ? 0 : c;
    }

    /** 赛道下赛队数（C09 删除时拒绝有队赛道） */
    public int countTeamsOnTrack(Long trackId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team WHERE track_id = ? AND deleted = 0 AND status = 'ACTIVE'",
                Integer.class, trackId);
        return c == null ? 0 : c;
    }

    /** 比赛下赛队数（C01 列表展示） */
    public int countTeamsOfContest(Long contestId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team WHERE contest_id = ? AND deleted = 0 AND status = 'ACTIVE'",
                Integer.class, contestId);
        return c == null ? 0 : c;
    }

    /** 比赛下赛道数（C01 列表展示） */
    public int countTracksOfContest(Long contestId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contest_track WHERE contest_id = ? AND deleted = 0",
                Integer.class, contestId);
        return c == null ? 0 : c;
    }

    /** 学期存在性校验 */
    public void ensureSemesterExists(Long semesterId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM semester WHERE id = ? AND deleted = 0",
                Integer.class, semesterId);
        if (c == null || c == 0) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "学期不存在");
        }
    }

    /** 模板存在性校验（允许 null：手工创建比赛无模板） */
    public void ensureTemplateExists(Long templateId) {
        if (templateId == null) return;
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contest_template WHERE id = ? AND deleted = 0",
                Integer.class, templateId);
        if (c == null || c == 0) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "比赛模板不存在");
        }
    }

    /** 赛道属于比赛校验 */
    public void ensureTrackBelongsToContest(Long trackId, Long contestId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contest_track WHERE id = ? AND contest_id = ? AND deleted = 0",
                Integer.class, trackId, contestId);
        if (c == null || c == 0) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "赛道不存在或不属于该比赛");
        }
    }

    /** 赛道名同比赛唯一校验（排除自身） */
    public void ensureTrackNameUnique(Long contestId, String name, Long excludeTrackId) {
        String sql = "SELECT COUNT(*) FROM contest_track WHERE contest_id = ? AND name = ? AND deleted = 0"
                + (excludeTrackId != null ? " AND id <> ?" : "");
        Integer c = excludeTrackId != null
                ? jdbcTemplate.queryForObject(sql, Integer.class, contestId, name, excludeTrackId)
                : jdbcTemplate.queryForObject(sql, Integer.class, contestId, name);
        if (c != null && c > 0) {
            throw new BizException(CommonErrorCode.DUPLICATE_SUBMIT, "赛道名已存在");
        }
    }

    /** 管理员组织子树内的组织 ID 列表——判定收口到 OrgScopeSupport（插入点①，Redis cache-aside + 降级直查库） */
    public List<Long> querySubtreeOrgIds(Long orgId) {
        return orgScopeSupport.subtreeIds(orgId);
    }

    /** 判断组织是否在管理员子树内——判定收口到 OrgScopeSupport（插入点①） */
    public boolean isOrgInSubtree(Long adminOrgId, Long orgId) {
        return orgScopeSupport.inSubtree(adminOrgId, orgId);
    }

    /** 比赛状态机：按序流转不可跳转（4011） */
    public void ensureStatusTransition(String from, String to) {
        String[] order = {"DRAFT", "PUBLISHED", "REGISTERING", "RUNNING", "FINISHED", "ARCHIVED"};
        int fi = indexOf(order, from), ti = indexOf(order, to);
        if (fi < 0 || ti < 0 || ti != fi + 1) {
            throw new BizException(TeamErrorCode.STATUS_TRANSITION_INVALID);
        }
    }

    private int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(v)) return i;
        }
        return -1;
    }
}
