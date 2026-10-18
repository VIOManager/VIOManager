package com.viomanager.approval.mq;

import com.viomanager.approval.task.ApprovalExpireTask;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 过期延迟消息的消费者（插入点④）：到点把 PENDING 单条件更新转 EXPIRED。
 * ⚠️ 零装配的关键开关：starter 的容器注册器是无条件扫描 @RocketMQMessageListener Bean 的，
 * 不加这个条件注解，默认上下文（没配 rocketmq.name-server）启动即炸"nameServer is required"。
 * 配了 name-server（如环境变量 ROCKETMQ_NAME_SERVER=localhost:9876）才建 Bean、才连 MQ。
 * 幂等：expireOne 条件更新 0 行（已被管理员处理/早已过期）直接当成功；
 * 消息重复投递（MQ 至少一次语义）也无副作用。
 */
@Slf4j
@Component
@ConditionalOnProperty("rocketmq.name-server")
@RocketMQMessageListener(topic = ApprovalMqProducer.EXPIRE_TOPIC,
        consumerGroup = "vio-mgmt-approval-expire")
public class ApprovalExpireListener implements RocketMQListener<String> {

    @Resource
    private ApprovalExpireTask approvalExpireTask;

    @Override
    public void onMessage(String approvalId) {
        boolean expired = approvalExpireTask.expireOne(Long.parseLong(approvalId.trim()));
        if (!expired) {
            //0 行 = 延迟消息与管理员处理撞车的正常分支，幂等确认即可
            log.info("过期消息到达但单据已被处理，幂等确认：approvalId={}", approvalId);
        }
    }
}
