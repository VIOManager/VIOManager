package com.viomanager.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.ProjectErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.material.support.MemberIdSupport;
import com.viomanager.project.dto.ProjectAdvisorAddDTO;
import com.viomanager.project.dto.ProjectMemberAddDTO;
import com.viomanager.project.entity.Project;
import com.viomanager.project.entity.ProjectAdvisor;
import com.viomanager.project.entity.ProjectMember;
import com.viomanager.project.mapper.ProjectAdvisorMapper;
import com.viomanager.project.mapper.ProjectMemberMapper;
import com.viomanager.project.service.ProjectMemberService;
import com.viomanager.project.support.ProjectVisibilitySupport;
import com.viomanager.project.vo.ProjectMemberVO;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目成员与指导教师实现（PR06–PR10）。
 * 防重口径：先查后插给业务码（7005），uk_project_member / uk_project_advisor
 * 撞键兜底转同一业务码——并发双插时不回放，与 7004 同款纪律。
 * 冗余口径：member_name / member_org_id / advisor_name 建行时从 A 线 member 快照。
 */
@Slf4j
@Service
public class ProjectMemberServiceImpl implements ProjectMemberService {

    @Resource
    private ProjectMemberMapper memberMapper;

    @Resource
    private ProjectAdvisorMapper advisorMapper;

    @Resource
    private ProjectVisibilitySupport visibilitySupport;

    @Resource
    private MemberIdSupport memberIdSupport;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public Result<List<ProjectMemberVO>> listMembers(Long projectId) {
        CurrentUser user = UserHolder.getUser();
        Long viewerId = memberIdSupport.currentMemberId(user);
        //项目可见者可看（7001/7003 由八条规则收口）
        visibilitySupport.requireVisibleProject(projectId, user, viewerId);
        List<ProjectMember> rows = memberMapper.selectList(
                new QueryWrapper<ProjectMember>().eq("project_id", projectId)
                        .orderByAsc("join_at").orderByAsc("id"));
        List<ProjectMemberVO> vos = rows.stream().map(row -> {
            ProjectMemberVO vo = new ProjectMemberVO();
            vo.setMemberId(row.getMemberId());
            vo.setMemberName(row.getMemberName());
            vo.setProjectRole(row.getProjectRole());
            vo.setJoinAt(row.getJoinAt());
            return vo;
        }).toList();
        return Result.ok(vos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> addMember(Long projectId, ProjectMemberAddDTO dto) {
        Project project = requireOwnedProject(projectId);
        //目标人员档案（A 线同库直查）：不存在 1004
        Map<String, Object> member = requireMember(dto.getMemberId());
        //已在项目（含 OWNER 行）→ 7005；先查给业务码，撞键兜底在 catch
        if (memberExists(projectId, dto.getMemberId())) {
            throw new BizException(ProjectErrorCode.ALREADY_MEMBER);
        }
        ProjectMember row = new ProjectMember();
        row.setProjectId(projectId);
        row.setMemberId(dto.getMemberId());
        row.setMemberName((String) member.get("name"));
        row.setMemberOrgId(member.get("org_id") == null ? null
                : ((Number) member.get("org_id")).longValue());
        row.setProjectRole("MEMBER");
        try {
            memberMapper.insert(row);
        } catch (DuplicateKeyException e) {
            //uk_project_member(project_id, member_id, deleted)：并发双插兜底
            throw new BizException(ProjectErrorCode.ALREADY_MEMBER);
        }
        return Result.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> removeMember(Long projectId, Long memberId) {
        requireOwnedProject(projectId);
        ProjectMember row = memberMapper.selectOne(
                new QueryWrapper<ProjectMember>().eq("project_id", projectId)
                        .eq("member_id", memberId));
        if (row == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND);
        }
        //OWNER 行不可移除（7002）：所有人只能整项目删除，不能自我摘除
        if ("OWNER".equals(row.getProjectRole())) {
            throw new BizException(ProjectErrorCode.NOT_OWNER);
        }
        memberMapper.deleteById(row.getId());
        return Result.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> addAdvisor(Long projectId, ProjectAdvisorAddDTO dto) {
        requireOwnedProject(projectId);
        //教师类型校验（7006）：不存在的人显然不是教师，同码从严（清单只设此码）
        Map<String, Object> member = requireMember(dto.getMemberId());
        if (!"TEACHER".equals(member.get("member_type"))) {
            throw new BizException(ProjectErrorCode.ADVISOR_NOT_TEACHER);
        }
        //已在任 → 7005（教师挂名同为"已在项目"语义）
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project_advisor WHERE project_id = ? AND member_id = ? "
                        + "AND deleted = 0",
                Integer.class, projectId, dto.getMemberId());
        if (count != null && count > 0) {
            throw new BizException(ProjectErrorCode.ALREADY_MEMBER);
        }
        ProjectAdvisor row = new ProjectAdvisor();
        row.setProjectId(projectId);
        row.setMemberId(dto.getMemberId());
        row.setAdvisorName((String) member.get("name"));
        row.setTitle(dto.getTitle());
        row.setStatus("ACTIVE");
        try {
            advisorMapper.insert(row);
        } catch (DuplicateKeyException e) {
            //uk_project_advisor(project_id, member_id, deleted)：并发双插兜底
            throw new BizException(ProjectErrorCode.ALREADY_MEMBER);
        }
        return Result.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> removeAdvisor(Long projectId, Long memberId) {
        requireOwnedProject(projectId);
        ProjectAdvisor row = advisorMapper.selectOne(
                new QueryWrapper<ProjectAdvisor>().eq("project_id", projectId)
                        .eq("member_id", memberId));
        if (row == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND);
        }
        advisorMapper.deleteById(row.getId());
        return Result.ok();
    }

    /** 加载项目（7001）+ 管理权校验（7002，所有人 or 管理员）——PR07-10 写口共用 */
    private Project requireOwnedProject(Long projectId) {
        CurrentUser user = UserHolder.getUser();
        Long operatorId = memberIdSupport.currentMemberId(user);
        return visibilitySupport.requireManageableProject(projectId, user, operatorId);
    }

    /** A 线人员档案：同库直查（只读），不存在 1004 */
    private Map<String, Object> requireMember(Long memberId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, name, org_id, member_type FROM member WHERE id = ? AND deleted = 0",
                memberId);
        if (rows.isEmpty()) {
            throw new BizException(CommonErrorCode.NOT_FOUND);
        }
        return rows.get(0);
    }

    private boolean memberExists(Long projectId, Long memberId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project_member WHERE project_id = ? AND member_id = ? "
                        + "AND deleted = 0",
                Integer.class, projectId, memberId);
        return count != null && count > 0;
    }
}
