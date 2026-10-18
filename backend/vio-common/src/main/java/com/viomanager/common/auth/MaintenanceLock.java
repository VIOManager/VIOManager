package com.viomanager.common.auth;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 学期切换维护锁（PRD §3.4：切换执行期间全员禁访）。
 * 刻意用 JVM 内标志而不是落库：单实例部署 + 切换执行是单个事务，
 * JVM 崩溃 = 事务回滚 = 锁随进程消失，天然自愈；落库的锁反而要人工清理残留。
 * 超管放行由 JwtAuthFilter 判定（执行人需要在窗口期观察系统）。
 */
public final class MaintenanceLock {

    private MaintenanceLock() {
    }

    private static final AtomicBoolean LOCKED = new AtomicBoolean(false);

    /** 引擎执行前置位 */
    public static void lock() {
        LOCKED.set(true);
    }

    /** 引擎结束（无论成败）释放——必须放 finally */
    public static void unlock() {
        LOCKED.set(false);
    }

    public static boolean isLocked() {
        return LOCKED.get();
    }
}
