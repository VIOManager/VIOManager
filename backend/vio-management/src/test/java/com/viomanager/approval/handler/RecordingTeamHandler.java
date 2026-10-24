package com.viomanager.approval.handler;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 测试用合成回调：注册 TEAM_CREATE 的 Handler，静态计数器记录回调次数与收到的 approvalId，
 * 测试据此断言"批准后回调真的被路由执行、参数口径正确、返回值会被回填 biz_id"。
 * TEAM_CREATE 正式 Handler 已随队长直建拍板退役（2026-10-24），本类成为该类型的
 * 唯一注册者——纯合成测试载体，不再有顶位/冲突问题。只存在于测试 classpath，不影响生产。
 */
@Component
public class RecordingTeamHandler implements ApprovalHandler {

    /** onApproved 被调次数 */
    public static final AtomicLong APPROVED_COUNT = new AtomicLong();

    /** onRejected 被调次数 */
    public static final AtomicLong REJECTED_COUNT = new AtomicLong();

    /** 最近一次批准回调收到的审批单 ID（验证 SPI 带了 approvalId） */
    public static final AtomicLong LAST_CALLBACK_APPROVAL_ID = new AtomicLong();

    /** 模拟"建出来的队伍 ID"：onApproved 返回它，骨架应回填到 approval.biz_id */
    public static final long FAKE_CREATED_TEAM_ID = 46001L;

    /** 每个测试前清零 */
    public static void reset() {
        APPROVED_COUNT.set(0);
        REJECTED_COUNT.set(0);
        LAST_CALLBACK_APPROVAL_ID.set(0);
    }

    @Override
    public String bizType() {
        return "TEAM_CREATE";
    }

    @Override
    public Long onApproved(Long approvalId, Long bizId, String payloadJson) {
        APPROVED_COUNT.incrementAndGet();
        LAST_CALLBACK_APPROVAL_ID.set(approvalId == null ? 0 : approvalId);
        return FAKE_CREATED_TEAM_ID;
    }

    @Override
    public void onRejected(Long approvalId, Long bizId, String payloadJson) {
        REJECTED_COUNT.incrementAndGet();
    }
}
