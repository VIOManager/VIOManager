package com.viomanager.ai.limit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.viomanager.ai.config.AiProperties;
import com.viomanager.ai.limit.AiRateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * 按人滑动窗口限流纯单测（不拉 Spring 上下文，可控时钟推窗口）。
 * 拍板口径：每人 6 次/分（接口清单 §7）。
 */
class AiRateLimiterTest {

    /** 可手动推进的时钟：窗口滑动全靠它 */
    private static final class SteppingClock extends Clock {
        private Instant now = Instant.ofEpochMilli(1_700_000_000_000L);

        void advanceMillis(long millis) {
            now = now.plusMillis(millis);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final SteppingClock clock = new SteppingClock();

    private AiRateLimiter limiter() {
        AiProperties properties = new AiProperties();
        properties.setRateLimitPerMinute(6);
        return new AiRateLimiter(properties, clock);
    }

    @Test
    void allowsSixPerMinuteAndRejectsSeventh() {
        AiRateLimiter limiter = limiter();
        for (int i = 0; i < 6; i++) {
            assertTrue(limiter.tryAcquire(1L), "前 6 次应全放行，第 " + (i + 1) + " 次被拒");
        }
        assertFalse(limiter.tryAcquire(1L), "窗口内第 7 次应拒绝（9102）");
    }

    @Test
    void windowSlidesAfterSixtySeconds() {
        AiRateLimiter limiter = limiter();
        for (int i = 0; i < 6; i++) {
            limiter.tryAcquire(1L);
        }
        assertFalse(limiter.tryAcquire(1L));
        clock.advanceMillis(60_001L);
        assertTrue(limiter.tryAcquire(1L), "超过 60s 窗口后应恢复放行");
    }

    @Test
    void remainingCountsDownAndRecovers() {
        AiRateLimiter limiter = limiter();
        assertEquals(6, limiter.remaining(1L));
        limiter.tryAcquire(1L);
        limiter.tryAcquire(1L);
        assertEquals(4, limiter.remaining(1L));
        clock.advanceMillis(60_001L);
        assertEquals(6, limiter.remaining(1L), "窗口滑出后余量应回满");
    }

    @Test
    void usersAreIsolated() {
        AiRateLimiter limiter = limiter();
        for (int i = 0; i < 6; i++) {
            limiter.tryAcquire(1L);
        }
        assertFalse(limiter.tryAcquire(1L));
        assertTrue(limiter.tryAcquire(2L), "甲触发限流不应影响乙");
        assertEquals(5, limiter.remaining(2L));
    }

    @Test
    void remainingIsReadOnly() {
        AiRateLimiter limiter = limiter();
        for (int i = 0; i < 3; i++) {
            assertEquals(6 - i, limiter.remaining(1L));
            limiter.tryAcquire(1L);
        }
        assertEquals(3, limiter.remaining(1L), "探针查余量本身不消耗配额");
    }
}
