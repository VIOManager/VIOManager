package com.viomanager.approval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.viomanager.approval.dto.ApprovalQueryDTO;
import com.viomanager.approval.dto.ApprovalReadDTO;
import com.viomanager.approval.entity.Approval;
import com.viomanager.approval.entity.ApprovalRecord;
import com.viomanager.approval.mapper.ApprovalMapper;
import com.viomanager.approval.mapper.ApprovalRecordMapper;
import com.viomanager.approval.service.IApprovalQueryService;
import com.viomanager.approval.vo.ApprovalDetailVO;
import com.viomanager.approval.vo.ApprovalRecordVO;
import com.viomanager.approval.vo.ApprovalVO;
import com.viomanager.approval.vo.UnreadCountVO;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.ApprovalErrorCode;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.PageResult;
import com.viomanager.common.result.Result;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 审批查询实现。
 * ⚠️ ID 口径：applicant_id / target_member_id 是人员档案 ID，当前用户的对应值
 * 要回 sys_user 查 member_id（token 里没带，绑定关系变更也能实时生效）。
 */
@Slf4j
@Service
public class ApprovalQueryServiceImpl implements IApprovalQueryService {

    @Resource
    private ApprovalMapper approvalMapper;

    @Resource
    private ApprovalRecordMapper approvalRecordMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public Result<PageResult<ApprovalVO>> list(ApprovalQueryDTO dto) {
        CurrentUser user = UserHolder.getUser();
        //1.按视角拼过滤：TODO 是管理员待办（学生调用直接 403）；MINE / TARGET 按人员 ID
        QueryWrapper<Approval> wrapper = new QueryWrapper<>();
        if ("TODO".equals(dto.getView())) {
            if (!"SUPER_ADMIN".equals(user.getRole()) && !"DEPT_ADMIN".equals(user.getRole())) {
                throw new BizException(CommonErrorCode.FORBIDDEN, "待办视角仅管理员可用");
            }
            if (!"SUPER_ADMIN".equals(user.getRole())) {
                List<Long> subtreeOrgIds = querySubtreeOrgIds(user.getOrgId());
                wrapper.in("approver_org_id", subtreeOrgIds);
            }
        } else if ("MINE".equals(dto.getView())) {
            wrapper.eq("applicant_id", requireMemberId(user));
        } else if ("TARGET".equals(dto.getView())) {
            wrapper.eq("target_member_id", requireMemberId(user));
        } else {
            throw new BizException(CommonErrorCode.PARAM_INVALID,
                    "view 取值不合法：" + dto.getView());
        }
        //2.可选筛选与排序
        if (dto.getStatus() != null && !dto.getStatus().isEmpty()) {
            wrapper.eq("status", dto.getStatus());
        }
        if (dto.getBizType() != null && !dto.getBizType().isEmpty()) {
            wrapper.eq("biz_type", dto.getBizType());
        }
        wrapper.orderByDesc("created_at");
        //3.分页查询转 VO
        Page<Approval> page = approvalMapper.selectPage(
                new Page<>(dto.getPage(), dto.getSize()), wrapper);
        List<ApprovalVO> voList = new ArrayList<>();
        for (Approval approval : page.getRecords()) {
            voList.add(toVO(approval, new ApprovalVO()));
        }
        return Result.ok(PageResult.of(voList, page.getTotal(), dto.getPage(), dto.getSize()));
    }

    @Override
    public Result<ApprovalDetailVO> detail(Long id) {
        //1.查单
        Approval approval = approvalMapper.selectById(id);
        if (approval == null) {
            throw new BizException(ApprovalErrorCode.NOT_FOUND_OR_HANDLED);
        }
        //2.看详情的资格：超管 / 发起人 / 目标人 / 审批组织子树内的管理员
        checkViewPermission(approval);
        //3.组装详情：列表字段 + 快照 + 流水（时间正序）
        ApprovalDetailVO vo = (ApprovalDetailVO) toVO(approval, new ApprovalDetailVO());
        vo.setBizPayload(approval.getBizPayload());
        List<ApprovalRecord> records = approvalRecordMapper.selectList(
                new QueryWrapper<ApprovalRecord>().eq("approval_id", id).orderByAsc("created_at"));
        List<ApprovalRecordVO> recordVoList = new ArrayList<>();
        for (ApprovalRecord record : records) {
            ApprovalRecordVO recordVO = new ApprovalRecordVO();
            recordVO.setAction(record.getAction());
            recordVO.setOperatorName(record.getOperatorName());
            recordVO.setOperatorRole(record.getOperatorRole());
            recordVO.setCommentText(record.getCommentText());
            recordVO.setCreatedAt(record.getCreatedAt());
            recordVoList.add(recordVO);
        }
        vo.setRecords(recordVoList);
        return Result.ok(vo);
    }

    @Override
    public Result<Void> markRead(ApprovalReadDTO dto) {
        CurrentUser user = UserHolder.getUser();
        //1.按 asRole 决定置哪个已读位、限定谁的单子
        UpdateWrapper<Approval> update = new UpdateWrapper<>();
        if ("APPLICANT".equals(dto.getAsRole())) {
            update.eq("applicant_id", requireMemberId(user)).set("applicant_read", 1);
        } else if ("TARGET".equals(dto.getAsRole())) {
            update.eq("target_member_id", requireMemberId(user)).set("target_read", 1);
        } else if ("APPROVER".equals(dto.getAsRole())) {
            if (!"SUPER_ADMIN".equals(user.getRole()) && !"DEPT_ADMIN".equals(user.getRole())) {
                throw new BizException(ApprovalErrorCode.NO_PERMISSION);
            }
            if (!"SUPER_ADMIN".equals(user.getRole())) {
                update.in("approver_org_id", querySubtreeOrgIds(user.getOrgId()));
            }
            update.set("approver_read", 1);
        } else {
            throw new BizException(CommonErrorCode.PARAM_INVALID,
                    "asRole 取值不合法：" + dto.getAsRole());
        }
        //2.只动自己名下的这些单子
        update.in("id", dto.getIds());
        approvalMapper.update(null, update);
        return Result.ok();
    }

    @Override
    public Result<UnreadCountVO> unreadCount() {
        CurrentUser user = UserHolder.getUser();
        UnreadCountVO vo = new UnreadCountVO();
        //1.当前用户没绑人员档案（纯管理账号）→ 两类红点都是 0
        Long memberId = currentMemberId(user);
        if (memberId == null) {
            vo.setTodoCount(0);
            vo.setResultCount(0);
            return Result.ok(vo);
        }
        //2.待处理数：发给我的邀请 / 解散通知（PENDING 且未读）
        Long todoCount = approvalMapper.selectCount(new QueryWrapper<Approval>()
                .eq("target_member_id", memberId).eq("status", "PENDING").eq("target_read", 0));
        //3.结果数：我发起的申请已出结果但我没看
        Long resultCount = approvalMapper.selectCount(new QueryWrapper<Approval>()
                .eq("applicant_id", memberId).ne("status", "PENDING").eq("applicant_read", 0));
        vo.setTodoCount(todoCount == null ? 0 : todoCount);
        vo.setResultCount(resultCount == null ? 0 : resultCount);
        return Result.ok(vo);
    }

    /** 详情可见性：超管 / 发起人 / 目标人 / 审批组织子树内管理员，其余 6002 */
    private void checkViewPermission(Approval approval) {
        CurrentUser user = UserHolder.getUser();
        if ("SUPER_ADMIN".equals(user.getRole())) {
            return;
        }
        Long memberId = currentMemberId(user);
        if (memberId != null && (memberId.equals(approval.getApplicantId())
                || memberId.equals(approval.getTargetMemberId()))) {
            return;
        }
        if ("DEPT_ADMIN".equals(user.getRole()) && isOrgInSubtree(user.getOrgId(), approval.getApproverOrgId())) {
            return;
        }
        throw new BizException(ApprovalErrorCode.NO_PERMISSION);
    }

    /** 当前用户的人员档案 ID；没绑定返回 null（require 版本直接 403） */
    private Long requireMemberId(CurrentUser user) {
        Long memberId = currentMemberId(user);
        if (memberId == null) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "当前账号未绑定人员档案");
        }
        return memberId;
    }

    private Long currentMemberId(CurrentUser user) {
        if (user == null || user.getUserId() == null) {
            return null;
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT member_id FROM sys_user WHERE id = ? AND deleted = 0",
                Long.class, user.getUserId());
        if (ids.isEmpty() || ids.get(0) == null) {
            return null;
        }
        return ids.get(0);
    }

    /** 管理员组织子树内的组织 ID 列表（sys_org.path 前缀匹配） */
    private List<Long> querySubtreeOrgIds(Long orgId) {
        if (orgId == null) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "部门管理员未配置所属组织");
        }
        try {
            String path = jdbcTemplate.queryForObject(
                    "SELECT path FROM sys_org WHERE id = ? AND deleted = 0", String.class, orgId);
            if (path == null) {
                throw new BizException(CommonErrorCode.FORBIDDEN, "组织不存在或已删除");
            }
            return jdbcTemplate.queryForList(
                    "SELECT id FROM sys_org WHERE path LIKE CONCAT(?, '%') AND deleted = 0",
                    Long.class, path);
        } catch (BizException e) {
            throw e;
        } catch (DataAccessException e) {
            log.warn("子树组织查询失败，按无权限处理：{}", e.getMessage());
            throw new BizException(CommonErrorCode.FORBIDDEN);
        }
    }

    /** 判断组织是否在管理员子树内（path 前缀匹配） */
    private boolean isOrgInSubtree(Long adminOrgId, Long orgId) {
        if (adminOrgId == null || orgId == null) {
            return false;
        }
        try {
            String adminPath = jdbcTemplate.queryForObject(
                    "SELECT path FROM sys_org WHERE id = ? AND deleted = 0", String.class, adminOrgId);
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_org WHERE id = ? AND deleted = 0 AND path LIKE CONCAT(?, '%')",
                    Integer.class, orgId, adminPath);
            return adminPath != null && count != null && count > 0;
        } catch (DataAccessException e) {
            log.warn("子树校验查询失败，按不在子树处理：{}", e.getMessage());
            return false;
        }
    }

    /** 实体转 VO（详情 VO 复用同一套列表字段） */
    private ApprovalVO toVO(Approval approval, ApprovalVO vo) {
        vo.setId(approval.getId());
        vo.setBizType(approval.getBizType());
        vo.setBizTitle(approval.getBizTitle());
        vo.setStatus(approval.getStatus());
        vo.setApplicantName(approval.getApplicantName());
        vo.setTargetMemberName(approval.getTargetMemberName());
        vo.setApplyReason(approval.getApplyReason());
        vo.setHandleComment(approval.getHandleComment());
        vo.setHandledAt(approval.getHandledAt());
        vo.setCreatedAt(approval.getCreatedAt());
        return vo;
    }
}
