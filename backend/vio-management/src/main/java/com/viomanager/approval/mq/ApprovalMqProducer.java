package com.viomanager.approval.mq;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 审批中心的 MQ 生产口（插入点④延迟消息）。
 * 口径：vio.mq-enabled=false 或未配 rocketmq.name-server 时一个消息都不发，
 * 过期全部交给降频轮询兜底——MQ 是纯旁路，挂了不影响任何业务语义。
 * 消息体只放 approvalId：消费侧条件更新转 EXPIRED，0 行说明已被管理员处理，天然幂等；
 * 消息丢失由 10 分钟兜底轮询接住，最多晚过期十分钟。
 */
@Slf4j
@Component
public class ApprovalMqProducer {

    static final String EXPIRE_TOPIC = "approval-expire";

    /** 插入点③状态事件 topic；注解与 relay 都引用这里，单一来源 */
    public static final String STATUS_TOPIC = "approval-status";

    /** 发送失败熔断窗口：窗口内不再尝试（每张单白等一轮 3 秒发送超时太亏），全靠兜底轮询 */
    private static final long SEND_FAIL_BREAK_MILLIS = 60_000L;

    /**
     * 时间轮延迟上限（broker timerMaxDelay 默认 259200000ms = 3 天）。
     * "5.x 支持任意延迟"是有天花板的——超限消息 broker 直接拒收（CODE 13），
     * 还会触发本类的发送熔断，把 outbox 状态事件的发送也连带挡住（Step5 E2E 实测踩中）。
     * 超限的过期不钳到上限发送（那会让 7 天的单第 3 天被提前过期），交给兜底轮询。
     */
    private static final long MAX_TIMER_DELAY_MILLIS = 259_200_000L;

    private final ObjectProvider<RocketMQTemplate> templateProvider;

    /** 总开关（vio.mq-enabled，默认 false）：关 = 完全不发，行为与现状一致 */
    @Value("${vio.mq-enabled:false}")
    private boolean mqEnabled;

    /** 发送失败熔断截止时刻；0 = 闭合 */
    private final AtomicLong sendFailUntil = new AtomicLong(0);

    /** 缺 name-server 配置只告警一次（配置问题不随时间自愈，刷屏没意义） */
    private final AtomicBoolean missingConfigWarned = new AtomicBoolean(false);

    /** ObjectProvider 注入：rocketmq.name-server 未配置时 RocketMQTemplate 这个 Bean 不存在，强注入会炸上下文 */
    public ApprovalMqProducer(ObjectProvider<RocketMQTemplate> templateProvider) {
        this.templateProvider = templateProvider;
    }

    /**
     * 创建审批单后（afterCommit）调用：发一条延迟消息，到点由消费者把单转 EXPIRED。
     * 延迟值 = expireAt 距今。选 5.x 的原因：时间轮支持秒级任意延迟（4.x 只有 18 个固定
     * 等级且最长 2 小时）；但"任意"有天花板——timerMaxDelay 默认 3 天，超限不发走兜底轮询（见下）。
     * 任何失败只记日志：延迟消息是优化项，兜底轮询保证过期最终一定发生。
     */
    public void sendExpireDelay(Long approvalId, LocalDateTime expireAt) {
        //1.开关关：一个不发
        if (!mqEnabled) {
            return;
        }
        //2.发送失败熔断中：本窗口内的新单全靠兜底轮询
        if (System.currentTimeMillis() < sendFailUntil.get()) {
            return;
        }
        //3.开了开关但 starter 没装配（缺 rocketmq.name-server）：告警一次后静默走轮询
        RocketMQTemplate template = templateProvider.getIfAvailable();
        if (template == null) {
            if (missingConfigWarned.compareAndSet(false, true)) {
                log.error("vio.mq-enabled=true 但 RocketMQTemplate 未装配（需同时配 rocketmq.name-server），"
                        + "过期延迟消息全部未发，由兜底轮询接管");
            }
            return;
        }
        //4.算延迟：已到期/临界的单至少给 1 秒，避免立即投递撞上创建事务的尾巴
        long delayMs = ChronoUnit.MILLIS.between(LocalDateTime.now(), expireAt);
        if (delayMs < 1000) {
            delayMs = 1000;
        }
        //5.超过时间轮上限（默认审批时效 7 天 > 3 天上限）：不发也不算失败，
        //   过期交给 10 分钟兜底轮询——对 7 天量级的时效，最多晚十分钟无感
        if (delayMs > MAX_TIMER_DELAY_MILLIS) {
            log.info("审批单距到期超过时间轮上限 3 天，过期由兜底轮询接管：approvalId={}", approvalId);
            return;
        }
        try {
            //刻意走原生 Message 而不是 RocketMQTemplate.syncSend 的 delayLevel 参数——
            //那是 4.x 的 18 个固定等级；任意延迟要靠 5.x 的 TIMER_DELAY_MS 属性（setDelayTimeMs）
            Message message = new Message(EXPIRE_TOPIC, "EXPIRE",
                    String.valueOf(approvalId),
                    String.valueOf(approvalId).getBytes(StandardCharsets.UTF_8));
            message.setDelayTimeMs(delayMs);
            template.getProducer().send(message);
        } catch (Exception e) {
            sendFailUntil.set(System.currentTimeMillis() + SEND_FAIL_BREAK_MILLIS);
            log.error("过期延迟消息发送失败（approvalId={}），{} 秒内新单暂不发 MQ 由兜底轮询接管，原因：{}",
                    approvalId, SEND_FAIL_BREAK_MILLIS / 1000, e.getMessage());
        }
    }

    /**
     * relay 投递用（插入点③）：同步发一条状态变更事件，返回是否真的发出。
     * 与 sendExpireDelay 的契约差别：expire 是"优化项、丢了有兜底"（内部吞异常），
     * outbox 事件由 relay 持久化重试管理——发送失败必须抛给调用方，
     * 由 relay 记 retry_count / 退避 / DEAD；返回 false = 没发但不惩罚（开关关 / 熔断窗口 / 未装配）。
     */
    public boolean sendStatusEvent(String bizType, Long approvalId, String payloadJson) throws Exception {
        //1.开关关或发送失败熔断中：不算失败，relay 本轮跳过即可
        if (!mqEnabled || System.currentTimeMillis() < sendFailUntil.get()) {
            return false;
        }
        RocketMQTemplate template = templateProvider.getIfAvailable();
        if (template == null) {
            if (missingConfigWarned.compareAndSet(false, true)) {
                log.error("vio.mq-enabled=true 但 RocketMQTemplate 未装配（需同时配 rocketmq.name-server），"
                        + "outbox 状态事件未发，积压等待配置补齐后由 relay 补投");
            }
            return false;
        }
        Message message = new Message(STATUS_TOPIC, bizType, String.valueOf(approvalId),
                payloadJson.getBytes(StandardCharsets.UTF_8));
        try {
            template.getProducer().send(message);
            return true;
        } catch (Exception e) {
            sendFailUntil.set(System.currentTimeMillis() + SEND_FAIL_BREAK_MILLIS);
            throw e;
        }
    }
}
