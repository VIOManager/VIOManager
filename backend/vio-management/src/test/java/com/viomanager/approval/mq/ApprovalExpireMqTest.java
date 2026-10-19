package com.viomanager.approval.mq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.viomanager.approval.dto.ApprovalCreateDTO;
import com.viomanager.approval.service.ApprovalClient;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 插入点④端到端验证（方案 Step 4）：真连本机 RocketMQ。
 * 链路：创建单（expire-days=0 → 创建即到期）→ afterCommit 发延迟消息（最小 1 秒）
 *      → 消费者条件更新转 EXPIRED → EXPIRE 流水落库 → 重复消费幂等。
 * 刻意不 @Transactional：afterCommit 发送只在真实提交后发生，回滚型测试永远等不到消息；
 * 数据自清（@AfterEach 按 id 删单与流水）。
 * 兜底轮询 10 分钟一跑，20 秒窗口内不可能触发——单子转 EXPIRED 只可能来自 MQ 链路。
 */
@SpringBootTest(properties = {
        "vio.mq-enabled=true",
        "rocketmq.name-server=localhost:9876",
        "vio.approval.expire-days=0",
        "spring.datasource.hikari.maximum-pool-size=2"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApprovalExpireMqTest {

    @Resource
    private ApprovalClient approvalClient;

    @Resource
    private ApprovalExpireListener approvalExpireListener;

    @Resource
    private JdbcTemplate jdbcTemplate;

    private Long approvalId;

    @AfterEach
    void cleanUp() {
        if (approvalId != null) {
            jdbcTemplate.update("DELETE FROM approval_record WHERE approval_id = ?", approvalId);
            //过期也是状态变更（Step5 起同事务写 outbox），一并清掉
            jdbcTemplate.update("DELETE FROM notify_outbox WHERE biz_id = ?", approvalId);
            jdbcTemplate.update("DELETE FROM approval WHERE id = ?", approvalId);
        }
    }

    @Test
    void delayedMessageExpiresApprovalAndIsIdempotent() throws Exception {
        //1.建一张创建即到期的单（事务提交后 afterCommit 发出 1 秒延迟消息）
        long sentAt = System.currentTimeMillis();
        approvalId = approvalClient.createApproval(buildDto());
        //2.轮询等消费者处理（最长 40 秒：1 秒最小延迟 + 时间轮秒级出队 + 消费者重平衡间隔
        //  默认 20 秒——套件负载下这些环节都会拉长，窗口必须全兜住）
        String status = null;
        long deadline = System.currentTimeMillis() + 40_000;
        while (System.currentTimeMillis() < deadline) {
            status = jdbcTemplate.queryForObject(
                    "SELECT status FROM approval WHERE id = ?", String.class, approvalId);
            if ("EXPIRED".equals(status)) {
                break;
            }
            Thread.sleep(500);
        }
        assertEquals("EXPIRED", status);
        //3.延迟语义冒烟：5.x 时间轮不会提前投递（1 秒延迟的消息不该亚秒到达；宽松下限防时钟毛刺）
        assertTrue(System.currentTimeMillis() - sentAt >= 500,
                "消息疑似未走延迟通道（亚秒到达）");
        //4.EXPIRE 流水恰好一条
        assertEquals(1, countExpireRecords());
        //5.同一条消息重复消费：条件更新 0 行，状态与流水都不变（幂等）
        approvalExpireListener.onMessage(String.valueOf(approvalId));
        assertEquals("EXPIRED", jdbcTemplate.queryForObject(
                "SELECT status FROM approval WHERE id = ?", String.class, approvalId));
        assertEquals(1, countExpireRecords());
    }

    private ApprovalCreateDTO buildDto() {
        ApprovalCreateDTO dto = new ApprovalCreateDTO();
        dto.setBizType("MQ_EXPIRE_TEST");
        dto.setBizTitle("MQ 过期链路端到端测试");
        dto.setApplicantMemberId(938801L);
        dto.setApplicantName("端到端测试");
        dto.setApproverOrgId(938801L);
        return dto;
    }

    private int countExpireRecords() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval_record WHERE approval_id = ? AND action = 'EXPIRE'",
                Integer.class, approvalId);
        return count == null ? 0 : count;
    }
}
