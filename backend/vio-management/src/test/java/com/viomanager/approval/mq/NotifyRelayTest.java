package com.viomanager.approval.mq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 插入点③投递侧 relay 语义验证（方案 Step 5）：成功置 SENT / 失败退避 / 超 5 次进 DEAD /
 * 未到期不捞 / 熔断 false 不惩罚。
 * 生产者用 @MockitoBean 替身，直调包级 deliverDue()——上下文里 vio.mq-enabled 故意保持
 * false，让 @Scheduled 的 relay() 每轮空转，测试与调度互不踩脚。
 */
@SpringBootTest(properties = {
        "rocketmq.name-server=localhost:9876",
        "spring.datasource.hikari.maximum-pool-size=2"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotifyRelayTest {

    /** 测试行 biz_id 段，与真实审批雪花 ID 零碰撞，@AfterEach 按段清 */
    private static final long BIZ_ID = 940_000_001L;

    @MockitoBean
    private ApprovalMqProducer approvalMqProducer;

    @Resource
    private NotifyRelay notifyRelay;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM notify_outbox WHERE biz_id BETWEEN ? AND ?",
                BIZ_ID, BIZ_ID + 99);
    }

    @Test
    void sentOnSuccess() throws Exception {
        //1.插一条新事件，生产者替身返回"真发出"
        insertRow(BIZ_ID + 1, 0, null);
        when(approvalMqProducer.sendStatusEvent(anyString(), anyLong(), anyString()))
                .thenReturn(true);

        //2.投递：置 SENT + 记发出时间 + 清错误
        notifyRelay.deliverDue();
        Map<String, Object> row = row(BIZ_ID + 1);
        assertEquals("SENT", row.get("send_status"));
        assertNotNull(row.get("sent_at"));
        assertNull(row.get("error_message"));
        assertEquals(0, ((Number) row.get("retry_count")).intValue());
    }

    @Test
    void backoffOnFirstFailure() throws Exception {
        //1.新事件第一次投递失败
        insertRow(BIZ_ID + 11, 0, null);
        when(approvalMqProducer.sendStatusEvent(anyString(), anyLong(), anyString()))
                .thenThrow(new RuntimeException("relay-unit-mock"));

        //2.退避：retry_count=1，next_retry_at ≈ 1 分钟后（60s ± 10s 容差），仍是 PENDING
        LocalDateTime before = LocalDateTime.now();
        notifyRelay.deliverDue();
        Map<String, Object> row = row(BIZ_ID + 11);
        assertEquals("PENDING", row.get("send_status"));
        assertEquals(1, ((Number) row.get("retry_count")).intValue());
        //驱动差异：DATETIME 可能回 Timestamp 也可能回 LocalDateTime（PM 线同款坑）
        Object raw = row.get("next_retry_at");
        LocalDateTime next = raw instanceof java.sql.Timestamp ts
                ? ts.toLocalDateTime() : (LocalDateTime) raw;
        assertTrue(next.isAfter(before.plusSeconds(50)), "退避应约 1 分钟：" + next);
        assertTrue(next.isBefore(before.plusSeconds(70)), "退避应约 1 分钟：" + next);
        assertTrue(((String) row.get("error_message")).contains("relay-unit-mock"));
    }

    @Test
    void deadAfterFiveRetries() throws Exception {
        //1.已重试 5 次的事件再失败 → DEAD 留痕
        insertRow(BIZ_ID + 21, 5, null);
        when(approvalMqProducer.sendStatusEvent(anyString(), anyLong(), anyString()))
                .thenThrow(new RuntimeException("relay-unit-mock-dead"));

        notifyRelay.deliverDue();
        Map<String, Object> row = row(BIZ_ID + 21);
        assertEquals("DEAD", row.get("send_status"));
        assertNotNull(row.get("error_message"));
    }

    @Test
    void skipRowNotDueYet() throws Exception {
        //1.下次重试时间在 10 分钟后的行不该被捞
        insertRow(BIZ_ID + 31, 2, LocalDateTime.now().plusMinutes(10));
        when(approvalMqProducer.sendStatusEvent(anyString(), anyLong(), anyString()))
                .thenReturn(true);

        notifyRelay.deliverDue();
        Map<String, Object> row = row(BIZ_ID + 31);
        assertEquals("PENDING", row.get("send_status"));
        assertEquals(2, ((Number) row.get("retry_count")).intValue());
        verify(approvalMqProducer, never()).sendStatusEvent(anyString(), anyLong(), anyString());
    }

    @Test
    void falseMeansSkipNotFailure() throws Exception {
        //1.生产者返回 false（开关关 / 熔断窗口）：跳过且不惩罚——不退避、不计数
        insertRow(BIZ_ID + 41, 0, null);
        when(approvalMqProducer.sendStatusEvent(anyString(), anyLong(), anyString()))
                .thenReturn(false);

        notifyRelay.deliverDue();
        Map<String, Object> row = row(BIZ_ID + 41);
        assertEquals("PENDING", row.get("send_status"));
        assertEquals(0, ((Number) row.get("retry_count")).intValue());
        assertNull(row.get("next_retry_at"));
    }

    private void insertRow(long bizId, int retryCount, LocalDateTime nextRetryAt) {
        jdbcTemplate.update(
                "INSERT INTO notify_outbox (topic, biz_type, biz_id, payload_json, send_status, retry_count, next_retry_at)"
                        + " VALUES ('approval-status', 'RELAY_TEST', ?, '{\"memberIds\":[]}', 'PENDING', ?, ?)",
                bizId, retryCount, nextRetryAt);
    }

    private Map<String, Object> row(long bizId) {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM notify_outbox WHERE biz_id = ?", bizId);
    }
}
