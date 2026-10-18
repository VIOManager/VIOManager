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
import com.viomanager.project.dto.MilestoneCreateDTO;
import com.viomanager.project.dto.MilestoneUpdateDTO;
import com.viomanager.project.entity.MilestoneNode;
import com.viomanager.project.mapper.MilestoneNodeMapper;
import com.viomanager.project.service.MilestoneService;
import com.viomanager.project.vo.MilestoneVO;
import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 时间节点实现（PR16–PR20）。
 * 状态机（清单 §3.4）：DRAFT → PENDING → APPROVED / REJECTED；REJECTED → PENDING 可重新提交；
 * 编辑仅 DRAFT/REJECTED（7009）、删除仅 DRAFT（7009）——已进入审批流的节点内容冻结。
 * 管理范围：超管全部；部门管理员限本子树比赛（contest.org_id 在其子树内）。
 */
@Slf4j
@Service
public class MilestoneServiceImpl implements MilestoneService {

    /** 节点类型合法值（清单 §3.4 四种） */
    private static final Set<String> NODE_TYPES =
            Set.of("REGISTER_START", "REGISTER_END", "WORK_SUBMIT", "OTHER");

    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private MilestoneNodeMapper milestoneMapper;

    @Resource
    private ApprovalClient approvalClient;

    @Resource
    private MemberIdSupport memberIdSupport;

    @Resource
    private com.viomanager.project.support.PmQueryCache queryCache;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public Result<List<MilestoneVO>> list(Long contestId, String status, String nodeType) {
        CurrentUser user = UserHolder.getUser();
        boolean manager = "SUPER_ADMIN".equals(user.getRole()) || "DEPT_ADMIN".equals(user.getRole());
        //学生视图是用户无关共享读（contest 维度 APPROVED 全量）——接 PmQueryCache，
        //筛选（status 恒 APPROVED / nodeType）在内存做，缓存不按筛选维度裂变
        if (!manager) {
            return Result.ok(studentApprovedList(contestId, nodeType));
        }
        QueryWrapper<MilestoneNode> qw = new QueryWrapper<>();
        qw.eq("contest_id", contestId)
                .eq(StringUtils.hasText(status), "status", status)
                .eq(StringUtils.hasText(nodeType), "node_type", nodeType)
                .orderByAsc("node_time").orderByAsc("id");
        //部门管理员限本子树比赛
        if ("DEPT_ADMIN".equals(user.getRole())) {
            List<Long> subtree = subtreeContestIds(user.getOrgId(), contestId);
            if (subtree.isEmpty()) {
                return Result.ok(List.of());
            }
            qw.in("contest_id", subtree);
        }
        List<MilestoneVO> vos = milestoneMapper.selectList(qw).stream()
                .map(this::toVO).toList();
        return Result.ok(vos);
    }

    /** 学生视图：APPROVED 全量（缓存）+ nodeType 内存筛选 */
    private List<MilestoneVO> studentApprovedList(Long contestId, String nodeType) {
        String cacheKey = "milestone:approved:" + contestId;
        List<MilestoneVO> all;
        String cached = queryCache.get(cacheKey);
        if (cached != null) {
            all = parseCached(cached);
        } else {
            all = milestoneMapper.selectList(new QueryWrapper<MilestoneNode>()
                            .eq("contest_id", contestId).eq("status", "APPROVED")
                            .orderByAsc("node_time").orderByAsc("id"))
                    .stream().map(this::toVO).toList();
            try {
                queryCache.put(cacheKey, JSON.writeValueAsString(all));
            } catch (Exception ignored) {
                //序列化失败只是缓存不命中，业务照常
            }
        }
        if (!StringUtils.hasText(nodeType)) {
            return all;
        }
        return all.stream().filter(vo -> nodeType.equals(vo.getNodeType())).toList();
    }

    @SuppressWarnings("unchecked")
    private List<MilestoneVO> parseCached(String json) {
        try {
            return (List<MilestoneVO>) JSON.readValue(json,
                    JSON.getTypeFactory().constructCollectionType(List.class, MilestoneVO.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<MilestoneVO> create(Long contestId, MilestoneCreateDTO dto) {
        CurrentUser user = requireManager();
        if (!NODE_TYPES.contains(dto.getNodeType())) {
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }
        Map<String, Object> contest = requireContestInRange(user, contestId);
        MilestoneNode node = new MilestoneNode();
        node.setContestId(contestId);
        node.setSemesterId(longOf(contest.get("semester_id")));
        node.setOrgId(longOf(contest.get("org_id")));
        node.setNodeType(dto.getNodeType());
        node.setName(dto.getName().trim());
        node.setNodeTime(dto.getNodeTime());
        node.setDescription(dto.getDescription());
        node.setStatus("DRAFT");
        node.setSynced(0);
        milestoneMapper.insert(node);
        return Result.ok(toVO(node));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> update(Long nodeId, MilestoneUpdateDTO dto) {
        CurrentUser user = requireManager();
        MilestoneNode node = requireEditableNode(nodeId, user);
        //部分更新：null 不动；nodeType 传了必须合法
        if (StringUtils.hasText(dto.getNodeType())) {
            if (!NODE_TYPES.contains(dto.getNodeType())) {
                throw new BizException(CommonErrorCode.PARAM_INVALID);
            }
        }
        MilestoneNode patch = new MilestoneNode();
        patch.setId(nodeId);
        patch.setNodeType(dto.getNodeType());
        patch.setName(StringUtils.hasText(dto.getName()) ? dto.getName().trim() : null);
        patch.setNodeTime(dto.getNodeTime());
        patch.setDescription(dto.getDescription());
        milestoneMapper.updateById(patch);
        //REJECTED 编辑即清旧驳回原因（下一次提交是新审核轮次；updateById 置不了 NULL 用手写 SQL）
        if ("REJECTED".equals(node.getStatus())) {
            jdbcTemplate.update("UPDATE milestone_node SET reject_reason = NULL, "
                    + "updated_at = updated_at WHERE id = ?", nodeId);
        }
        return Result.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(Long nodeId) {
        CurrentUser user = requireManager();
        MilestoneNode node = requireNodeInRange(user, nodeId);
        //仅 DRAFT 可删（7009）：已进审批流的节点动不得（审批单还挂着 bizId 引用）
        if (!"DRAFT".equals(node.getStatus())) {
            throw new BizException(ProjectErrorCode.MILESTONE_STATE_NOT_ALLOWED);
        }
        milestoneMapper.deleteById(nodeId);
        return Result.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> submit(Long nodeId) {
        CurrentUser user = requireManager();
        MilestoneNode node = requireNodeInRange(user, nodeId);
        Long applicantId = memberIdSupport.currentMemberId(user);
        if (applicantId == null) {
            //审批单 applicant_id 表结构 NOT NULL：提交人必须落到具体人员（无档案超管 7014 明确提示，此前误报 1001）
            throw new BizException(ProjectErrorCode.MILESTONE_APPLICANT_NO_PROFILE);
        }
        //CAS：DRAFT/REJECTED → PENDING（0 行=状态已被并发改变，7009）
        int rows = jdbcTemplate.update(
                "UPDATE milestone_node SET status = 'PENDING', updated_at = updated_at "
                        + "WHERE id = ? AND status IN ('DRAFT', 'REJECTED') AND deleted = 0", nodeId);
        if (rows == 0) {
            throw new BizException(ProjectErrorCode.MILESTONE_STATE_NOT_ALLOWED);
        }
        //开 MILESTONE_APPROVE 审批单（同事务：提单失败连状态转移一起回滚）
        Map<String, Object> contest = requireContestInRange(user, node.getContestId());
        ApprovalCreateDTO approval = new ApprovalCreateDTO();
        approval.setBizType("MILESTONE_APPROVE");
        approval.setBizId(nodeId);
        approval.setBizTitle("时间节点审核：" + node.getName());
        approval.setPayloadJson(payloadOf(node));
        approval.setApplicantMemberId(applicantId);
        approval.setApplicantName(user.getRealName());
        //审批责任组织=比赛归属组织（该部门管理员待办；超管全放行可审，待确认②推荐口径）
        Long approverOrgId = longOf(contest.get("org_id"));
        approval.setApproverOrgId(approverOrgId == null ? user.getOrgId() : approverOrgId);
        approval.setContestId(node.getContestId());
        approval.setSemesterId(node.getSemesterId());
        approvalClient.createApproval(approval);
        return Result.ok();
    }

    // ---------------------------------------------------------------- 支撑方法

    /** PR17-20 写口门槛：仅部门管理员与超管（学生 1003） */
    private CurrentUser requireManager() {
        CurrentUser user = UserHolder.getUser();
        if (!"SUPER_ADMIN".equals(user.getRole()) && !"DEPT_ADMIN".equals(user.getRole())) {
            throw new BizException(CommonErrorCode.FORBIDDEN);
        }
        return user;
    }

    /** 比赛存在 + 在操作人管辖范围（超管全放行；部门管理员限本子树） */
    private Map<String, Object> requireContestInRange(CurrentUser user, Long contestId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, name, semester_id, org_id, status FROM contest "
                        + "WHERE id = ? AND deleted = 0", contestId);
        if (rows.isEmpty()) {
            throw new BizException(CommonErrorCode.NOT_FOUND);
        }
        Map<String, Object> contest = rows.get(0);
        if ("DEPT_ADMIN".equals(user.getRole())
                && !orgInSubtree(longOf(contest.get("org_id")), user.getOrgId())) {
            throw new BizException(CommonErrorCode.FORBIDDEN);
        }
        return contest;
    }

    /** 节点存在 + 范围校验（透过所属比赛判定） */
    private MilestoneNode requireNodeInRange(CurrentUser user, Long nodeId) {
        MilestoneNode node = milestoneMapper.selectById(nodeId);
        if (node == null) {
            throw new BizException(ProjectErrorCode.NOT_FOUND);
        }
        if ("DEPT_ADMIN".equals(user.getRole())) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT org_id FROM contest WHERE id = ? AND deleted = 0", node.getContestId());
            Long contestOrg = rows.isEmpty() ? null : longOf(rows.get(0).get("org_id"));
            if (!orgInSubtree(contestOrg, user.getOrgId())) {
                throw new BizException(CommonErrorCode.FORBIDDEN);
            }
        }
        return node;
    }

    /** 编辑前置：节点可编辑态（DRAFT/REJECTED，7009）+ 范围 */
    private MilestoneNode requireEditableNode(Long nodeId, CurrentUser user) {
        MilestoneNode node = requireNodeInRange(user, nodeId);
        if (!"DRAFT".equals(node.getStatus()) && !"REJECTED".equals(node.getStatus())) {
            throw new BizException(ProjectErrorCode.MILESTONE_STATE_NOT_ALLOWED);
        }
        return node;
    }

    /** 部门管理员子树内的指定比赛（不在子树返回空集=从严空列表） */
    private List<Long> subtreeContestIds(Long adminOrgId, Long contestId) {
        if (adminOrgId == null || contestId == null) {
            return List.of();
        }
        List<String> paths = jdbcTemplate.queryForList(
                "SELECT path FROM sys_org WHERE id = ? AND deleted = 0", String.class, adminOrgId);
        if (paths.isEmpty()) {
            return List.of();
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM contest WHERE id = ? AND deleted = 0 AND org_id IN "
                        + "(SELECT id FROM sys_org WHERE deleted = 0 AND path LIKE CONCAT(?, '%'))",
                Long.class, contestId, paths.get(0));
        return ids;
    }

    private boolean orgInSubtree(Long targetOrgId, Long adminOrgId) {
        if (targetOrgId == null || adminOrgId == null) {
            return false;
        }
        List<String> paths = jdbcTemplate.queryForList(
                "SELECT path FROM sys_org WHERE id = ? AND deleted = 0", String.class, adminOrgId);
        if (paths.isEmpty()) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_org WHERE id = ? AND deleted = 0 AND path LIKE CONCAT(?, '%')",
                Integer.class, targetOrgId, paths.get(0));
        return count != null && count > 0;
    }

    /** 审批快照：回调方按 bizId 查库为准，快照仅供排查追溯 */
    private String payloadOf(MilestoneNode node) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("nodeId", node.getId());
        payload.put("contestId", node.getContestId());
        payload.put("name", node.getName());
        payload.put("nodeTime", node.getNodeTime() == null ? null : node.getNodeTime().toString());
        payload.put("nodeType", node.getNodeType());
        try {
            return JSON.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    private MilestoneVO toVO(MilestoneNode node) {
        MilestoneVO vo = new MilestoneVO();
        vo.setId(node.getId());
        vo.setContestId(node.getContestId());
        vo.setNodeType(node.getNodeType());
        vo.setName(node.getName());
        vo.setNodeTime(node.getNodeTime());
        vo.setDescription(node.getDescription());
        vo.setStatus(node.getStatus());
        vo.setApprovedAt(node.getApprovedAt());
        vo.setRejectReason(node.getRejectReason());
        return vo;
    }

    private Long longOf(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
