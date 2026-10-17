package com.viomanager.approval.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.viomanager.approval.dto.ApprovalActionDTO;
import com.viomanager.approval.entity.Approval;
import com.viomanager.approval.entity.ApprovalRecord;
import com.viomanager.approval.handler.ApprovalHandler;
import com.viomanager.approval.handler.ApprovalHandlerRegistry;
import com.viomanager.approval.mapper.ApprovalMapper;
import com.viomanager.approval.mapper.ApprovalRecordMapper;
import com.viomanager.approval.service.IApprovalActionService;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.ApprovalErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.util.IpUtil;
import com.viomanager.datascop.OrgScopeSupport;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审批处理实现：五个动作共用一条处理链（查单 → 状态检查 → 权限 → 条件更新 → 流水 → 回调）。
 * ⚠️ 并发防重：状态更新用"WHERE status='PENDING'"条件更新，两个管理员同时批同一张单，
 * 后到的更新 0 行按 6001（不存在或已处理）拒绝——审批结果与回调绝无二次执行。
 */
@Slf4j
@Service
public class ApprovalActionServiceImpl implements IApprovalActionService {

    @Resource
    private ApprovalMapper approvalMapper;

    @Resource
    private ApprovalRecordMapper approvalRecordMapper;

    @Resource
    private ApprovalHandlerRegistry handlerRegistry;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private OrgScopeSupport orgScopeSupport;

    @Override
    @Transactional
    public void approve(Long id, ApprovalActionDTO dto) {
        handle(id, "APPROVE", "APPROVED", commentOf(dto), "APPROVER");
    }

    @Override
    @Transactional
    public void reject(Long id, ApprovalActionDTO dto) {
        handle(id, "REJECT", "REJECTED", commentOf(dto), "APPROVER");
    }

    @Override
    @Transactional
    public void cancel(Long id, ApprovalActionDTO dto) {
        // 取消不触发业务回调：正式数据还没建，无需善后
        handle(id, "CANCEL", "CANCELLED", commentOf(dto), "APPLICANT");
    }

    @Override
    @Transactional
    public void agree(Long id, ApprovalActionDTO dto) {
        handle(id, "AGREE", "APPROVED", commentOf(dto), "TARGET");
    }

    @Override
    @Transactional
    public void refuse(Long id, ApprovalActionDTO dto) {
        handle(id, "REFUSE", "REJECTED", commentOf(dto), "TARGET");
    }

    /**
     * 共用处理链：action 流水动作 / toStatus 目标状态 / operatorRole 操作者身份。
     */
    private void handle(Long id, String action, String toStatus, String comment, String operatorRole) {
        CurrentUser user = UserHolder.getUser();
        //1.查单：不存在 → 6001
        Approval approval = approvalMapper.selectById(id);
        if (approval == null) {
            throw new BizException(ApprovalErrorCode.NOT_FOUND_OR_HANDLED);
        }
        //2.状态：只有 PENDING 可处理 → 6003
        if (!"PENDING".equals(approval.getStatus())) {
            throw new BizException(ApprovalErrorCode.STATE_NOT_ALLOWED);
        }
        //3.权限按操作者身份：审批人看组织子树，发起人 / 目标人按人员档案 ID
        if ("APPROVER".equals(operatorRole)) {
            checkApprover(user, approval);
        } else if ("TARGET".equals(operatorRole)) {
            Long memberId = currentMemberId(user);
            if (memberId == null || !memberId.equals(approval.getTargetMemberId())) {
                throw new BizException(ApprovalErrorCode.NO_PERMISSION);
            }
        } else {
            Long memberId = currentMemberId(user);
            if (memberId == null || !memberId.equals(approval.getApplicantId())) {
                throw new BizException(ApprovalErrorCode.NO_PERMISSION);
            }
        }
        //4.条件更新（防并发双批）：WHERE status='PENDING'，0 行说明并发对手先处理完 → 6001
        UpdateWrapper<Approval> update = new UpdateWrapper<>();
        update.eq("id", approval.getId()).eq("status", "PENDING")
                .set("status", toStatus)
                .set("handle_comment", comment)
                .set("handled_at", LocalDateTime.now());
        if ("APPROVER".equals(operatorRole)) {
            update.set("approver_id", user.getUserId())
                    .set("approver_name", user.getRealName())
                    .set("approver_read", 1);
        } else if ("TARGET".equals(operatorRole)) {
            update.set("target_read", 1);
        } else {
            update.set("applicant_read", 1);
        }
        int rows = approvalMapper.update(null, update);
        if (rows == 0) {
            throw new BizException(ApprovalErrorCode.NOT_FOUND_OR_HANDLED);
        }
        //5.记流水
        ApprovalRecord record = new ApprovalRecord();
        record.setApprovalId(approval.getId());
        record.setAction(action);
        record.setOperatorId(user.getUserId());
        record.setOperatorName(user.getRealName());
        record.setOperatorRole(operatorRole);
        record.setCommentText(comment);
        record.setFromStatus("PENDING");
        record.setToStatus(toStatus);
        record.setIp(currentIp());
        record.setCreatedAt(LocalDateTime.now());
        approvalRecordMapper.insert(record);
        //6.回调路由：批准 / 驳回落到业务线 Handler（未注册用兜底，记日志不动作）；取消不回调。
        //   创建型审批的 bizId 此刻是 null（对象还没建），onApproved 返回新建对象 ID 后回填单据
        ApprovalHandler handler = handlerRegistry.get(approval.getBizType());
        if ("APPROVED".equals(toStatus)) {
            Long createdBizId = handler.onApproved(
                    approval.getId(), approval.getBizId(), approval.getBizPayload());
            if (createdBizId != null && approval.getBizId() == null) {
                UpdateWrapper<Approval> backfill = new UpdateWrapper<>();
                backfill.eq("id", approval.getId()).set("biz_id", createdBizId);
                approvalMapper.update(null, backfill);
            }
        } else if ("REJECTED".equals(toStatus)) {
            handler.onRejected(approval.getId(), approval.getBizId(), approval.getBizPayload());
        }
    }

    /** 审批人资格：超管全放行；部门管理员须审批责任组织在自己子树内（判定收口到 OrgScopeSupport，插入点①）；
     *  学生到不了这（@RequireRole 挡） */
    private void checkApprover(CurrentUser user, Approval approval) {
        if ("SUPER_ADMIN".equals(user.getRole())) {
            return;
        }
        if (!"DEPT_ADMIN".equals(user.getRole()) || user.getOrgId() == null
                || approval.getApproverOrgId() == null
                || !orgScopeSupport.inSubtree(user.getOrgId(), approval.getApproverOrgId())) {
            throw new BizException(ApprovalErrorCode.NO_PERMISSION);
        }
    }

    /** 当前用户的人员档案 ID（没绑定返回 null） */
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

    private String commentOf(ApprovalActionDTO dto) {
        return dto == null ? null : dto.getComment();
    }

    /** 当前请求的客户端 IP；非 Web 环境返回 null */
    private String currentIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        return IpUtil.resolveClientIp(request);
    }
}
