package com.viomanager.approval.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.approval.entity.Approval;
import com.viomanager.approval.entity.NotifyOutbox;
import com.viomanager.approval.mapper.NotifyOutboxMapper;
import com.viomanager.approval.mq.ApprovalMqProducer;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 插入点③的写入侧：审批状态变更事件与业务同事务落本地消息表。
 * 两个调用位：五动作共用链 handle()（approve/reject/cancel/agree/refuse 一处收口）
 * 与 ApprovalExpireTask.expireOne()——全部在各自的事务里，事件与审批结果原子落库。
 * payload 只放消费方要用的字段：受影响 memberIds（发起人 + 目标人）与定位信息；
 * 不放 bizTitle——1000 字符列宽下长标题有截断风险，而重算红点根本不需要它。
 * 本方法失败会让整个审批动作回滚：宁可这次操作失败，也不能出现"审批已生效、事件丢失"
 * 的静默不一致——这正是选本地消息表而非事后补偿的原因。
 */
@Component
public class ApprovalOutboxSupport {

    @Resource
    private NotifyOutboxMapper notifyOutboxMapper;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 记一条状态变更事件。memberIds = 红点的受影响人：
     * 发起人（resultCount 变化）+ 目标人（todoCount 变化），去重去空。
     */
    public void recordStatusChange(Approval approval, String action, String toStatus) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approvalId", approval.getId());
        payload.put("bizType", approval.getBizType());
        payload.put("action", action);
        payload.put("toStatus", toStatus);
        payload.put("memberIds", affectedMemberIds(approval));
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            //纯 Map/字符串/数字的序列化失败只可能来自环境级故障：上抛回滚，不留半拉子状态
            throw new IllegalStateException("outbox payload 序列化失败", e);
        }
        NotifyOutbox row = new NotifyOutbox()
                .setTopic(ApprovalMqProducer.STATUS_TOPIC)
                .setBizType(approval.getBizType())
                .setBizId(approval.getId())
                .setPayloadJson(json)
                .setSendStatus("PENDING")
                .setRetryCount(0);
        notifyOutboxMapper.insert(row);
    }

    private List<Long> affectedMemberIds(Approval approval) {
        List<Long> ids = new ArrayList<>(2);
        if (approval.getApplicantId() != null && !ids.contains(approval.getApplicantId())) {
            ids.add(approval.getApplicantId());
        }
        if (approval.getTargetMemberId() != null
                && !ids.contains(approval.getTargetMemberId())) {
            ids.add(approval.getTargetMemberId());
        }
        return ids;
    }
}
