package com.viomanager.team.service.impl;

import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.TeamErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.team.mapper.AdvisorRow;
import com.viomanager.team.mapper.MemberRow;
import com.viomanager.team.mapper.SearchRow;
import com.viomanager.team.mapper.TeamListRow;
import com.viomanager.team.service.ITeamQueryService;
import com.viomanager.team.support.TeamQuerySupport;
import com.viomanager.team.vo.TeamAdvisorVO;
import com.viomanager.team.vo.TeamDetailVO;
import com.viomanager.team.vo.TeamListItemVO;
import com.viomanager.team.vo.TeamMemberSearchVO;
import com.viomanager.team.vo.TeamMemberVO;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 赛队查询实现。数据范围：超管全部 / 部门管理员本组织子树 / 学生仅 member_id 命中的队伍。
 * 字段裁剪：LEADER/ADVISOR/ADMIN 见成员全量；MEMBER 仅本人全量、他人只 memberId/name/role/status。
 * SQL 走 JdbcTemplate 显式列名（禁 SELECT *），数据访问与组织子树辅助委托 {@link TeamQuerySupport}。
 */
@Slf4j
@Service
public class TeamQueryServiceImpl implements ITeamQueryService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private TeamQuerySupport support;

    // ────────────────────────── TE01 列表 ──────────────────────────

    @Override
    public Result<List<TeamListItemVO>> listTeams(Long contestId, String keyword) {
        CurrentUser user = UserHolder.getUser();
        StringBuilder sql = new StringBuilder(TeamQuerySupport.TEAM_SELECT).append(" WHERE t.deleted = 0");
        List<Object> params = new ArrayList<>();
        String role = user.getRole();
        if ("SUPER_ADMIN".equals(role)) {
            // 全部，无额外条件
        } else if ("DEPT_ADMIN".equals(role)) {
            List<Long> orgIds = support.querySubtreeOrgIds(user.getOrgId());
            if (orgIds.isEmpty()) {
                return Result.ok(Collections.emptyList());
            }
            sql.append(" AND t.org_id IN (");
            for (int i = 0; i < orgIds.size(); i++) {
                sql.append(i > 0 ? ",?" : "?");
            }
            sql.append(")");
            params.addAll(orgIds);
        } else {
            Long memberId = support.currentMemberId(user);
            if (memberId == null) {
                return Result.ok(Collections.emptyList());
            }
            sql.append(" AND EXISTS (SELECT 1 FROM team_member tm WHERE tm.team_id = t.id")
                    .append(" AND tm.deleted = 0 AND tm.member_id = ?")
                    .append(" AND tm.status IN ('UNCONFIRMED','CONFIRMED'))");
            params.add(memberId);
        }
        if (contestId != null) {
            sql.append(" AND t.contest_id = ?");
            params.add(contestId);
        }
        if (keyword != null && !keyword.isBlank()) {
            // 关键词同时匹配队名与项目名（2026-10-23 修复：此前仅队名，按项目名搜不到）
            sql.append(" AND (t.name LIKE CONCAT('%', ?, '%') OR t.project_name LIKE CONCAT('%', ?, '%'))");
            params.add(keyword);
            params.add(keyword);
        }
        sql.append(" ORDER BY t.created_at DESC");
        List<TeamListRow> rows = support.queryTeams(sql.toString(), params.toArray());
        List<TeamListItemVO> voList = new ArrayList<>(rows.size());
        for (TeamListRow row : rows) {
            TeamListItemVO vo = new TeamListItemVO();
            vo.setId(row.getId());
            vo.setName(row.getName());
            vo.setProjectName(row.getProjectName());
            vo.setContestId(row.getContestId());
            vo.setContestName(row.getContestName());
            vo.setTrackName(row.getTrackName());
            vo.setLeaderName(row.getLeaderName());
            vo.setMemberCount(row.getMemberCount());
            vo.setAdvisorCount(row.getAdvisorCount());
            vo.setStatus(row.getStatus());
            vo.setMaterialStatus(row.getMaterialStatus());
            vo.setMaterialCompleteRate(row.getMaterialCompleteRate());
            voList.add(vo);
        }
        return Result.ok(voList);
    }

    // ────────────────────────── TE02 详情 ──────────────────────────

    @Override
    public Result<TeamDetailVO> detail(Long teamId) {
        TeamListRow row = support.loadTeam(teamId);
        CurrentUser user = UserHolder.getUser();
        String viewerRole = resolveViewerRole(user, row, teamId);
        TeamDetailVO vo = toDetailVO(row, viewerRole);
        List<MemberRow> members = support.queryMembers(teamId);
        List<AdvisorRow> advisors = support.queryAdvisors(teamId);
        vo.setMembers(toMemberVOs(members, viewerRole, support.currentMemberId(user)));
        vo.setAdvisors(toAdvisorVOs(advisors));
        return Result.ok(vo);
    }

    // ────────────────────────── TE05 搜索 ──────────────────────────

    @Override
    public Result<List<TeamMemberSearchVO>> searchMembers(Long teamId, String keyword) {
        TeamListRow row = support.loadTeam(teamId);
        resolveViewerRole(UserHolder.getUser(), row, teamId); // 仅校验可见性
        String kw = (keyword == null || keyword.isBlank()) ? "" : keyword;
        List<SearchRow> rows = jdbcTemplate.query(
                "SELECT m.id AS member_id, m.name, m.student_no, m.college, "
                        + "EXISTS(SELECT 1 FROM team_member tm WHERE tm.member_id = m.id AND tm.team_id = ? "
                        + "  AND tm.deleted = 0 AND tm.status IN ('UNCONFIRMED','CONFIRMED')) AS in_team "
                        + "FROM member m WHERE m.deleted = 0 AND m.status = 1 "
                        + "AND m.member_type <> 'TEACHER' AND m.name LIKE CONCAT('%', ?, '%') "
                        + "ORDER BY m.name LIMIT 20",
                (rs, n) -> {
                    SearchRow r = new SearchRow();
                    r.setMemberId(rs.getLong("member_id"));
                    r.setName(rs.getString("name"));
                    r.setStudentNo(rs.getString("student_no"));
                    r.setCollege(rs.getString("college"));
                    r.setInTeam(rs.getBoolean("in_team"));
                    return r;
                }, teamId, kw);
        List<TeamMemberSearchVO> voList = new ArrayList<>(rows.size());
        for (SearchRow r : rows) {
            TeamMemberSearchVO vo = new TeamMemberSearchVO();
            vo.setMemberId(r.getMemberId());
            vo.setName(r.getName());
            vo.setStudentNo(r.getStudentNo());
            vo.setCollege(r.getCollege());
            vo.setInTeam(r.getInTeam());
            voList.add(vo);
        }
        return Result.ok(voList);
    }

    // ────────────────────────── TE11 成员列表 ──────────────────────────

    @Override
    public Result<List<TeamMemberVO>> listMembers(Long teamId) {
        TeamListRow row = support.loadTeam(teamId);
        CurrentUser user = UserHolder.getUser();
        String viewerRole = resolveViewerRole(user, row, teamId);
        List<MemberRow> members = support.queryMembers(teamId);
        return Result.ok(toMemberVOs(members, viewerRole, support.currentMemberId(user)));
    }

    // ────────────────────────── 内部辅助 ──────────────────────────

    /** 判定 viewerRole：超管/部门管理员(本子树)=ADMIN；学生按队长/指导教师/队员=LEADER/ADVISOR/MEMBER，否则 4008 */
    private String resolveViewerRole(CurrentUser user, TeamListRow team, Long teamId) {
        String role = user.getRole();
        if ("SUPER_ADMIN".equals(role)) {
            return "ADMIN";
        }
        if ("DEPT_ADMIN".equals(role)) {
            if (support.isOrgInSubtree(user.getOrgId(), team.getOrgId())) {
                return "ADMIN";
            }
            throw new BizException(TeamErrorCode.NOT_TEAM_MEMBER);
        }
        Long memberId = support.currentMemberId(user);
        if (memberId == null) {
            throw new BizException(TeamErrorCode.NOT_TEAM_MEMBER);
        }
        if (support.isAdvisorOfTeam(teamId, memberId)) {
            return "ADVISOR";
        }
        Integer isLeader = support.queryMemberFlag(teamId, memberId);
        if (isLeader == null) {
            throw new BizException(TeamErrorCode.NOT_TEAM_MEMBER);
        }
        return isLeader == 1 ? "LEADER" : "MEMBER";
    }

    private TeamDetailVO toDetailVO(TeamListRow row, String viewerRole) {
        TeamDetailVO vo = new TeamDetailVO();
        vo.setId(row.getId());
        vo.setName(row.getName());
        vo.setProjectName(row.getProjectName());
        vo.setContestId(row.getContestId());
        vo.setContestName(row.getContestName());
        vo.setTrackId(row.getTrackId());
        vo.setTrackName(row.getTrackName());
        vo.setLeaderId(row.getLeaderMemberId());
        vo.setLeaderName(row.getLeaderName());
        vo.setStatus(row.getStatus());
        vo.setMaterialStatus(row.getMaterialStatus());
        vo.setMaterialCompleteRate(row.getMaterialCompleteRate());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setViewerRole(viewerRole);
        return vo;
    }

    private List<TeamMemberVO> toMemberVOs(List<MemberRow> members, String viewerRole, Long currMemberId) {
        boolean seeAll = "ADMIN".equals(viewerRole) || "LEADER".equals(viewerRole) || "ADVISOR".equals(viewerRole);
        List<TeamMemberVO> voList = new ArrayList<>(members.size());
        for (MemberRow m : members) {
            TeamMemberVO vo = new TeamMemberVO();
            vo.setMemberId(m.getMemberId());
            vo.setName(m.getName());
            vo.setRole(m.getIsLeader() != null && m.getIsLeader() == 1 ? "LEADER" : "MEMBER");
            vo.setStatus(m.getStatus());
            if (seeAll || (currMemberId != null && currMemberId.equals(m.getMemberId()))) {
                vo.setCollege(m.getCollege());
                vo.setClassName(m.getClassName());
                vo.setPhone(m.getPhone());
                vo.setIdCardLast4(m.getIdCardLast4());
            }
            voList.add(vo);
        }
        return voList;
    }

    private List<TeamAdvisorVO> toAdvisorVOs(List<AdvisorRow> advisors) {
        List<TeamAdvisorVO> voList = new ArrayList<>(advisors.size());
        for (AdvisorRow a : advisors) {
            TeamAdvisorVO vo = new TeamAdvisorVO();
            vo.setMemberId(a.getMemberId());
            vo.setName(a.getAdvisorName());
            vo.setCollege(a.getCollege());
            voList.add(vo);
        }
        return voList;
    }
}
