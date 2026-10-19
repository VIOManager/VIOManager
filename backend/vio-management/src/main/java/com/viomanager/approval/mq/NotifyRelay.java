package com.viomanager.approval.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.viomanager.approval.entity.NotifyOutbox;
import com.viomanager.approval.mapper.NotifyOutboxMapper;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 插入点③的投递侧：本地消息表 relay——扫表把 PENDING 事件发到 MQ。
 * 退避 1/2/4/8/16 分钟，超 5 次进 DEAD 并 error 留痕（表里能查能补发，
 * 这就是选本地消息表而不是 RocketMQ 事务消息的原因——事务消息要实现回查接口且与业务耦合）。
 * 装配条件与两个消费者一致：rocketmq.name-server 未配置时本 Bean 不存在（零装配口径）；
 * 存在但 vio.mq-enabled=false 时每轮空转直接返回——outbox 只积累不投递，
 * 等开关打开后把积压事件补投出去。
 */
@Slf4j
@Component
@ConditionalOnProperty("rocketmq.name-server")
public class NotifyRelay {

    private static final long[] BACKOFF_MINUTES = {1, 2, 4, 8, 16};
    private static final int MAX_RETRY = 5;
    private static final int BATCH = 100;

    @Resource
    private NotifyOutboxMapper notifyOutboxMapper;

    @Resource
    private ApprovalMqProducer approvalMqProducer;

    @Value("${vio.mq-enabled:false}")
    private boolean mqEnabled;

    @Scheduled(fixedDelay = 2000)
    public void relay() {
        if (!mqEnabled) {
            return;
        }
        deliverDue();
    }

    /**
     * 捞一批到期的 PENDING 投递。包级可见：测试在开关关闭的上下文里直调，
     * 绕过 @Scheduled 与开关门槛只测投递语义本身。
     */
    void deliverDue() {
        List<NotifyOutbox> due = notifyOutboxMapper.selectList(new QueryWrapper<NotifyOutbox>()
                .eq("send_status", "PENDING")
                .and(w -> w.isNull("next_retry_at").or().le("next_retry_at", LocalDateTime.now()))
                .orderByAsc("id")
                .last("LIMIT " + BATCH));
        for (NotifyOutbox row : due) {
            try {
                //1.false = 开关关 / 熔断窗口 / 未装配（不是失败不退避）：
                //   本轮剩下的行也白搭，直接回等下一轮
                if (!approvalMqProducer.sendStatusEvent(
                        row.getBizType(), row.getBizId(), row.getPayloadJson())) {
                    return;
                }
                markSent(row);
            } catch (Exception e) {
                //2.真失败：退避重试，超过 5 次进 DEAD
                markFailure(row, e);
            }
        }
    }

    /** 条件更新置 SENT（eq PENDING 防与补发工具 / 未来多实例并发双发） */
    private void markSent(NotifyOutbox row) {
        notifyOutboxMapper.update(null, new UpdateWrapper<NotifyOutbox>()
                .eq("id", row.getId()).eq("send_status", "PENDING")
                .set("send_status", "SENT")
                .set("sent_at", LocalDateTime.now())
                .set("error_message", null));
    }

    private void markFailure(NotifyOutbox row, Exception e) {
        int retries = row.getRetryCount() == null ? 0 : row.getRetryCount();
        String reason = truncate(e.getMessage());
        if (retries >= MAX_RETRY) {
            notifyOutboxMapper.update(null, new UpdateWrapper<NotifyOutbox>()
                    .eq("id", row.getId()).eq("send_status", "PENDING")
                    .set("send_status", "DEAD")
                    .set("error_message", reason));
            log.error("outbox 事件投递失败超 {} 次进死信：id={} bizId={} 原因：{}",
                    MAX_RETRY, row.getId(), row.getBizId(), reason);
            return;
        }
        long backoff = BACKOFF_MINUTES[Math.min(retries, BACKOFF_MINUTES.length - 1)];
        notifyOutboxMapper.update(null, new UpdateWrapper<NotifyOutbox>()
                .eq("id", row.getId()).eq("send_status", "PENDING")
                .set("retry_count", retries + 1)
                .set("next_retry_at", LocalDateTime.now().plusMinutes(backoff))
                .set("error_message", reason));
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
