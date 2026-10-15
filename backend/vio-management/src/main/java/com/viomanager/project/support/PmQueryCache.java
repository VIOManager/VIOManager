package com.viomanager.project.support;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 项目管理共享读缓存（方案 §7.6，KbRecommendCache 同款模式泛化）。
 * 只缓存用户无关的共享读（当前：比赛维度 APPROVED 节点列表），个性化数据永不进缓存。
 *
 * 稳定性三件套：
 *  - 总开关 vio.pm.cache.enabled（测试关掉——@Transactional 回滚救不了已写入的缓存）
 *  - 超时 1s（yml spring.data.redis.timeout），Redis 卡死不拖垮接口
 *  - 简单熔断：连续失败 3 次开闸 30s 直查库，期间不再打扰 Redis
 * 写失效：状态变化方（审批通过）主动 evict，TTL 60s 兜底最终一致。
 */
@Slf4j
@Component
public class PmQueryCache {

    private static final String KEY_PREFIX = "vio:pm:";
    private static final int FAILURE_THRESHOLD = 3;
    private static final long BREAK_MILLIS = 30_000L;

    @Value("${vio.pm.cache.enabled:true}")
    private boolean enabled;

    @Value("${vio.pm.cache.ttl-seconds:60}")
    private long ttlSeconds;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 连续失败计数（>=阈值进入熔断） */
    private final AtomicLong failures = new AtomicLong();

    /** 熔断截止时间戳（System.currentTimeMillis，0=未熔断） */
    private volatile long breakUntil = 0L;

    /** 读缓存；未启用/熔断中/未命中/Redis 异常都返回 null（调用方直查库） */
    public String get(String key) {
        if (!enabled || breaking()) {
            return null;
        }
        try {
            String value = stringRedisTemplate.opsForValue().get(KEY_PREFIX + key);
            failures.set(0);
            return value;
        } catch (Exception e) {
            onFailure(e);
            return null;
        }
    }

    /** 写缓存；任何异常只记日志（缓存写失败不影响业务） */
    public void put(String key, String value) {
        if (!enabled || value == null || breaking()) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(KEY_PREFIX + key, value,
                    Duration.ofSeconds(ttlSeconds));
            failures.set(0);
        } catch (Exception e) {
            onFailure(e);
        }
    }

    /** 写失效：状态变化方调用；失败时靠 TTL 兜底（60s 最终一致） */
    public void evict(String key) {
        if (!enabled) {
            return;
        }
        try {
            stringRedisTemplate.delete(KEY_PREFIX + key);
        } catch (Exception e) {
            log.warn("缓存失效失败（靠 TTL 兜底）：key={}，原因：{}", key, e.getMessage());
        }
    }

    private boolean breaking() {
        return breakUntil > System.currentTimeMillis();
    }

    private void onFailure(Exception e) {
        long count = failures.incrementAndGet();
        if (count >= FAILURE_THRESHOLD) {
            breakUntil = System.currentTimeMillis() + BREAK_MILLIS;
            failures.set(0);
            log.warn("Redis 连续失败进入熔断 30s，期间直查库：{}", e.getMessage());
        }
    }
}
