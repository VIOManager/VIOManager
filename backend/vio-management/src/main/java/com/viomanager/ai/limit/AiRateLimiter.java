package com.viomanager.ai.limit;

import com.viomanager.ai.config.AiProperties;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * AI 助手按人限流（接口清单 §7，2026-10-24 拍板：每人 6 次/分）。
 * 实现取滑动窗口时间戳队列而非经典令牌桶：拍板口径是"每分钟 6 次"，
 * 滑窗语义精确（任意连续 60s 内至多 6 次），实现也只需一个 Deque。
 * 单机内存版：100-200 人规模一个 ConcurrentHashMap 足够，不引 Redis。
 * 进程内锁粒度到人（每 userId 一把锁对象），互不排队。
 */
@Component
public class AiRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final AiProperties properties;

    private final Clock clock;

    /** 每人窗口内的时间戳队列；锁对象与队列绑在同一条记录里，避免两次 map 查询间竞态 */
    private final ConcurrentHashMap<Long, UserWindow> windows = new ConcurrentHashMap<>();

    @Autowired
    public AiRateLimiter(AiProperties properties) {
        this(properties, Clock.systemDefaultZone());
    }

    /** 测试口：注入可控时钟推窗口 */
    AiRateLimiter(AiProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
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
