package com.viomanager.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.approval.dto.ApprovalCreateDTO;
import com.viomanager.approval.service.ApprovalClient;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.enums.ProjectErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.material.support.MemberIdSupport;
import com.viomanager.project.dto.ViewRequestCreateDTO;
import com.viomanager.project.entity.Project;
import com.viomanager.project.entity.ProjectViewGrant;
import com.viomanager.project.mapper.ProjectMapper;
import com.viomanager.project.mapper.ProjectViewGrantMapper;
import com.viomanager.project.service.ViewGrantService;
import com.viomanager.project.vo.ViewGrantVO;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 查看申请与授权实现（PR29–PR31）。
 * 生命周期：PENDING（申请）→ 审批通过 → ACTIVE →（ONCE 详情消耗 → EXPIRED /
 * LIMITED 到期视失效 / 撤销 → REVOKED）；驳回 → EXPIRED（清单口径"置失效"）。
 * 已可见者申请不拦（无害，审批照走）；已有 PENDING 拒绝（7011）防重复提单。
 */
@Slf4j
@Service
public class ViewGrantServiceImpl implements ViewGrantService {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ProjectViewGrantMapper grantMapper;

    @Resource
    private ApprovalClient approvalClient;

    @Resource
    private MemberIdSupport memberIdSupport;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Long> createRequest(Long projectId, ViewRequestCreateDTO dto) {
        CurrentUser user = UserHolder.getUser();
        Long applicantId = memberIdSupport.currentMemberId(user);
        if (applicantId == null) {
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(ProjectErrorCode.NOT_FOUND);
        }
        String grantType = dto.getGrantType();
        boolean limited = "LIMITED".equals(grantType);
        if (!limited && !"ONCE".equals(grantType)) {
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }
        //LIMITED 必填到期且须晚于现在；ONCE 忽略该字段
        if (limited && (dto.getExpireAt() == null
                || !dto.getExpireAt().isAfter(LocalDateTime.now()))) {
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }
        //已有 PENDING → 7011（防重复提单；先查给业务码）
        Integer pending = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project_view_grant WHERE project_id = ? AND applicant_id = ? "
                        + "AND status = 'PENDING' AND deleted = 0",
                Integer.class, projectId, applicantId);
        if (pending != null && pending > 0) {
            throw new BizException(ProjectErrorCode.PENDING_REQUEST_EXISTS);
        }
        ProjectViewGrant grant = new ProjectViewGrant();
        grant.setProjectId(projectId);
        grant.setApplicantId(applicantId);
        //姓名冗余从 A 线 member 表快照（与 member_name/advisor_name 口径一致，非 JWT realName）
        List<String> names = jdbcTemplate.queryForList(
                "SELECT name FROM member WHERE id = ? AND deleted = 0", String.class, applicantId);
        grant.setApplicantName(names.isEmpty() ? user.getRealName() : names.get(0));
        grant.setApplicantOrgId(user.getOrgId());
        grant.setGrantType(grantType);
        grant.setExpireAt(limited ? dto.getExpireAt() : null);
        grant.setUsedCount(0);
        grant.setStatus("PENDING");
        grantMapper.insert(grant);
        //审批责任组织=项目所属组织（清单 PR29）
        ApprovalCreateDTO approval = new ApprovalCreateDTO();
        approval.setBizType("PROJECT_VIEW");
        approval.setBizId(grant.getId());
        approval.setBizTitle("项目查看申请：" + project.getName());
        approval.setPayloadJson(payloadOf(project, grant));
        approval.setApplicantMemberId(applicantId);
        approval.setApplicantName(user.getRealName());
        approval.setApproverOrgId(project.getOrgId());
        approval.setContestId(project.getContestId());
        approval.setSemesterId(project.getSemesterId());
        approvalClient.createApproval(approval);
        return Result.ok(grant.getId());
    }

    @Override
    public Result<List<ViewGrantVO>> listGrants(Long projectId) {
        requireOwnerOrManager(projectId);
        List<ViewGrantVO> vos = grantMapper.selectList(
                        new QueryWrapper<ProjectViewGrant>().eq("project_id", projectId)
                                .orderByDesc("created_at").orderByDesc("id"))
                .stream().map(this::toVO).toList();
        return Result.ok(vos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> revoke(Long grantId) {
        //先做行级归属校验（授权行→项目），再做人员门槛
        ProjectViewGrant grant = grantMapper.selectById(grantId);
        if (grant == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND);
        }
        requireOwnerOrManager(grant.getProjectId());
        //CAS：PENDING/ACTIVE → REVOKED；终态（EXPIRED/REVOKED）再撤 → 1006
        int rows = jdbcTemplate.update(
                "UPDATE project_view_grant SET status = 'REVOKED', updated_at = updated_at "
                        + "WHERE id = ? AND status IN ('PENDING', 'ACTIVE') AND deleted = 0", grantId);
        if (rows == 0) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED);
        }
        return Result.ok();
    }

    /** PR30/PR31 门槛：项目所有人或管理员（超管/项目组织子树内部门管理员） */
    private void requireOwnerOrManager(Long projectId) {
        CurrentUser user = UserHolder.getUser();
        Long memberId = memberIdSupport.currentMemberId(user);
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(ProjectErrorCode.NOT_FOUND);
        }
        if (memberId != null && memberId.equals(project.getOwnerMemberId())) {
            return;
        }
        if ("SUPER_ADMIN".equals(user.getRole())) {
            return;
        }
        if ("DEPT_ADMIN".equals(user.getRole()) && user.getOrgId() != null) {
            List<String> paths = jdbcTemplate.queryForList(
                    "SELECT path FROM sys_org WHERE id = ? AND deleted = 0",
                    String.class, user.getOrgId());
            if (!paths.isEmpty()) {
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM sys_org WHERE id = ? AND deleted = 0 "
                                + "AND path LIKE CONCAT(?, '%')",
                        Integer.class, project.getOrgId(), paths.get(0));
                if (count != null && count > 0) {
                    return;
                }
            }
        }
        throw new BizException(CommonErrorCode.FORBIDDEN);
    }

    private String payloadOf(Project project, ProjectViewGrant grant) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("grantId", grant.getId());
        payload.put("projectId", project.getId());
        payload.put("applicantId", grant.getApplicantId());
        payload.put("grantType", grant.getGrantType());
        payload.put("expireAt", grant.getExpireAt() == null ? null : grant.getExpireAt().toString());
        try {
            return JSON.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    private ViewGrantVO toVO(ProjectViewGrant grant) {
        ViewGrantVO vo = new ViewGrantVO();
        vo.setId(grant.getId());
        vo.setApplicantName(grant.getApplicantName());
        vo.setApplicantOrgId(grant.getApplicantOrgId());
        vo.setGrantType(grant.getGrantType());
        vo.setStatus(grant.getStatus());
        vo.setExpireAt(grant.getExpireAt());
        vo.setUsedCount(grant.getUsedCount());
        vo.setCreatedAt(grant.getCreatedAt());
        return vo;
    }
}
