package com.viomanager.approval.service.impl;

import com.viomanager.approval.config.ApprovalProperties;
import com.viomanager.approval.dto.ApprovalCreateDTO;
import com.viomanager.approval.entity.Approval;
import com.viomanager.approval.entity.ApprovalRecord;
import com.viomanager.approval.mapper.ApprovalMapper;
import com.viomanager.approval.mapper.ApprovalRecordMapper;
import com.viomanager.approval.mq.ApprovalMqProducer;
import com.viomanager.approval.service.ApprovalClient;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.util.IpUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审批单创建实现：各业务线调它发起审批，不用关心流水与过期时间怎么落。
 * 过期主路径是延迟消息（插入点④）：提交后发 approval-expire 延迟消息到点触发，
 * 兜底轮询每 10 分钟对账——两个触发源共用同一段条件更新逻辑。
 */
@Service
public class ApprovalClientImpl implements ApprovalClient {

    @Resource
    private ApprovalMapper approvalMapper;

    @Resource
    private ApprovalRecordMapper approvalRecordMapper;

    @Resource
    private ApprovalProperties approvalProperties;

    @Resource
    private ApprovalMqProducer approvalMqProducer;

    @Override
    @Transactional
    public Long createApproval(ApprovalCreateDTO dto) {
        //1.落审批单：PENDING + 有效期 + 归属组织取审批责任组织
        Approval approval = new Approval();
        approval.setBizType(dto.getBizType());
        approval.setBizId(dto.getBizId());
        approval.setBizTitle(dto.getBizTitle());
        approval.setBizPayload(dto.getPayloadJson());
        approval.setStatus("PENDING");
        approval.setApplicantId(dto.getApplicantMemberId());
        approval.setApplicantName(dto.getApplicantName());
        approval.setTargetMemberId(dto.getTargetMemberId());
        approval.setTargetMemberName(dto.getTargetMemberName());
        approval.setApproverOrgId(dto.getApproverOrgId());
        approval.setTeamId(dto.getTeamId());
        approval.setContestId(dto.getContestId());
        approval.setSemesterId(dto.getSemesterId());
        approval.setOrgId(dto.getApproverOrgId());
        approval.setApplyReason(dto.getApplyReason());
        approval.setExpireAt(LocalDateTime.now().plusDays(approvalProperties.getExpireDays()));
        approval.setApplicantRead(0);
        approval.setTargetRead(0);
        approval.setApproverRead(0);
        approvalMapper.insert(approval);
        //2.记 SUBMIT 流水：操作人取当前登录人（业务线通常在用户请求里调用；内部任务调用时为空）
        CurrentUser user = UserHolder.getUser();
        ApprovalRecord record = new ApprovalRecord();
        record.setApprovalId(approval.getId());
        record.setAction("SUBMIT");
        record.setOperatorId(user == null ? null : user.getUserId());
        record.setOperatorName(dto.getApplicantName());
        record.setOperatorRole("APPLICANT");
        record.setToStatus("PENDING");
        record.setIp(currentIp());
        record.setCreatedAt(LocalDateTime.now());
        approvalRecordMapper.insert(record);
        //3.过期延迟消息（插入点④）：挂 afterCommit 再发——事务内就发的话，回滚的单子
        //  也会收到过期消息（消费者条件更新能兜住，但白扫一趟）；消息丢了由兜底轮询接住
        sendExpireMessageAfterCommit(approval);
        return approval.getId();
    }

    /** 提交后发过期延迟消息；无事务环境（防御分支）直接发 */
    private void sendExpireMessageAfterCommit(Approval approval) {
        Long approvalId = approval.getId();
        LocalDateTime expireAt = approval.getExpireAt();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    approvalMqProducer.sendExpireDelay(approvalId, expireAt);
                }
            });
        } else {
            approvalMqProducer.sendExpireDelay(approvalId, expireAt);
        }
    }

    /** 取当前请求的客户端 IP；不在 Web 环境（定时任务等）返回 null */
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
