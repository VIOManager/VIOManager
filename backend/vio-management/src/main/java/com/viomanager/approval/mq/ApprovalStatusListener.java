package com.viomanager.approval.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.approval.support.ApprovalUnreadCache;
import com.viomanager.approval.support.UnreadCounter;
import com.viomanager.approval.vo.UnreadCountVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * 插入点③的消费者（本期唯一订阅方）：状态变更事件到达后按人重算红点写 Redis。
 * 写路径不增量而是整写重算——重算天然幂等、不会漂移（方案 §4.② 的取舍记录）。
 * 降级口径：Redis 故障只丢这一次刷新（warn 留痕照常 ACK），红点由 TTL 到期后的
 * 读路径重算与 10 分钟对账兜底；开关 vio.cache.unread-enabled=false 时完全不写
 * （三开关全关 = 零外部副作用的验收口径），消息照常 ACK。
 * 后续 WebSocket 通知中心直接加新订阅方，生产侧不动。
 */
@Slf4j
@Component
@ConditionalOnProperty("rocketmq.name-server")
@RocketMQMessageListener(topic = ApprovalMqProducer.STATUS_TOPIC,
        consumerGroup = "vio-mgmt-approval-status")
public class ApprovalStatusListener implements RocketMQListener<String> {

    @Resource
    private UnreadCounter unreadCounter;

    @Resource
    private ApprovalUnreadCache approvalUnreadCache;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public void onMessage(String payloadJson) {
        //1.红点缓存关着就什么都不写
        if (!approvalUnreadCache.isEnabled()) {
            return;
        }
        //2.解析受影响人（payload 由 ApprovalOutboxSupport 构造）
        JsonNode memberIds;
        try {
            memberIds = objectMapper.readTree(payloadJson).path("memberIds");
        } catch (Exception e) {
            log.error("outbox 事件 payload 解析失败，事件被丢弃：{}", payloadJson, e);
            return;
        }
        if (!memberIds.isArray()) {
            return;
        }
        //3.逐人重算整写；单人 Redis 故障不拦整批
        for (JsonNode node : memberIds) {
            long memberId = node.asLong();
            UnreadCountVO counts = unreadCounter.countFor(memberId);
            try {
                approvalUnreadCache.write(memberId, counts);
            } catch (DataAccessException e) {
                log.warn("红点回写 Redis 失败（memberId={}），由 TTL 到期重算与对账兜底：{}",
                        memberId, e.getMessage());
            }
        }
    }
}
