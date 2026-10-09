package com.viomanager.approval.handler;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 测试用回调：注册 TEAM_CREATE 的 Handler，静态计数器记录回调次数，
 * 测试据此断言"批准后回调真的被路由执行"（B 线还没实现真 Handler，用它在测试里顶位）。
 * 只存在于测试 classpath，不影响生产。
 */
@Component
public class RecordingTeamHandler implements ApprovalHandler {

    /** onApproved 被调次数 */
    public static final AtomicLong APPROVED_COUNT = new AtomicLong();

    /** onRejected 被调次数 */
    public static final AtomicLong REJECTED_COUNT = new AtomicLong();

    /** 最近一次批准回调拿到的 bizId */
    public static final AtomicLong LAST_APPROVED_BIZ_ID = new AtomicLong();

    /** 每个测试前清零 */
    public static void reset() {
        APPROVED_COUNT.set(0);
        REJECTED_COUNT.set(0);
        LAST_APPROVED_BIZ_ID.set(0);
    }

    @Override
    public String bizType() {
        return "TEAM_CREATE";
    }

    @Override
    public void onApproved(Long bizId, String payloadJson) {
        APPROVED_COUNT.incrementAndGet();
        LAST_APPROVED_BIZ_ID.set(bizId == null ? 0 : bizId);
    }

    @Override
    public void onRejected(Long bizId, String payloadJson) {
        REJECTED_COUNT.incrementAndGet();
    }
}
