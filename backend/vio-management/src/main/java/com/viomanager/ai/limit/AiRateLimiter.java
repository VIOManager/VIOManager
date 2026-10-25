package com.viomanager.ai.limit;

import com.viomanager.ai.config.AiProperties;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * AI 助手限流器（接口清单 §7，2026-10-24 拍板三数值）：
 * 按人每分钟 6 次（滑动窗口）+ 全局并发 5（信号量闸门）。
 * 滑窗取时间戳队列而非经典令牌桶：拍板口径是"每分钟 6 次"，
 * 滑窗语义精确（任意连续 60s 内至多 6 次），实现也只需一个 Deque。
 * 并发闸门与 chat 线程池同宽（池 fixed 5，信号量先挡第 6 个并发请求快速失败，
 * 不让 SSE 长任务在池队列里无声积压）。单机内存版：100-200 人规模不引 Redis。
 */
@Component
public class AiRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final AiProperties properties;

    private final Clock clock;

    /** 每人窗口内的时间戳队列；锁对象与队列绑在同一条记录里，避免两次 map 查询间竞态 */
    private final ConcurrentHashMap<Long, UserWindow> windows = new ConcurrentHashMap<>();

    /** 全局并发闸门： permits = 拍板的全局并发上限 */
    private final Semaphore globalSlots;

    @Autowired
    public AiRateLimiter(AiProperties properties) {
        this(properties, Clock.systemDefaultZone());
    }

    /** 测试口：注入可控时钟推窗口 */
    AiRateLimiter(AiProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.globalSlots = new Semaphore(properties.getMaxConcurrent());
    }

    /**
     * 是否放行本次 chat：窗口内不足配额则记录本次时间戳并放行，否则拒绝（9102）。
     */
    public boolean tryAcquire(long userId) {
        long now = clock.millis();
        UserWindow window = windows.computeIfAbsent(userId, k -> new UserWindow());
        synchronized (window) {
            evictExpired(window, now);
            if (window.timestamps.size() >= properties.getRateLimitPerMinute()) {
                return false;
            }
            window.timestamps.addLast(now);
            return true;
        }
    }

    /**
     * 触发按人限流后的建议重试秒数（Retry-After 头）：窗口内最早一条过期尚需多久，向上取整。
     */
    public long retryAfterSeconds(long userId) {
        long now = clock.millis();
        UserWindow window = windows.get(userId);
        if (window == null) {
            return 1;
        }
        synchronized (window) {
            evictExpired(window, now);
            Long earliest = window.timestamps.peekFirst();
            if (earliest == null) {
                return 1;
            }
            long waitMillis = WINDOW_MILLIS - (now - earliest);
            return Math.max(1, (waitMillis + 999) / 1000);
        }
    }

    /**
     * 当前窗口剩余次数（AI02 探针展示用）：只读不清，不占配额。
     */
    public int remaining(long userId) {
        long now = clock.millis();
        UserWindow window = windows.get(userId);
        if (window == null) {
            return properties.getRateLimitPerMinute();
        }
        synchronized (window) {
            evictExpired(window, now);
            return properties.getRateLimitPerMinute() - window.timestamps.size();
        }
    }

    /** 全局并发闸门：非阻塞拿槽；拿不到 = 并发已满，9102 快速失败 */
    public boolean tryAcquireGlobalSlot() {
        return globalSlots.tryAcquire();
    }

    /** 生成结束（含中断/失败）释放并发槽；重复释放由 Semaphore 自身容忍（不抛） */
    public void releaseGlobalSlot() {
        globalSlots.release();
    }

    /** 滑出 60s 窗口外的旧记录；调用方须持有 window 锁 */
    private void evictExpired(UserWindow window, long now) {
        while (!window.timestamps.isEmpty() && now - window.timestamps.peekFirst() >= WINDOW_MILLIS) {
            window.timestamps.removeFirst();
        }
    }

    /** 单人窗口：时间戳队列 + 与之绑定的锁对象 */
    private static final class UserWindow {
        private final Deque<Long> timestamps = new ArrayDeque<>();
    }
}
