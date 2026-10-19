package com.viomanager.approval.support;

import com.viomanager.approval.vo.UnreadCountVO;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 插入点②红点缓存（方案 §4.②）：`vio:approval:unread:{memberId}` Hash，todo/result 两字段，TTL 24 小时。
 * 读路径 cache-aside：命中直接返回，miss 现算（UnreadCounter）整写回填；
 * 写路径不增量而是整写重算——重算天然幂等、不会漂移，200 人量级成本可忽略（量大了再换增量+消费去重）。
 * 三个使用方共用本类保证键布局一致：P08 读路径、消费者（Step5 的状态事件）、10 分钟对账任务。
 * 降级三件套与 kb/OrgScopeSupport 同款：1s 命令超时（yml）+ 30s 熔断 + 直查库——
 * Redis 是纯优化不是依赖，挂了只影响性能不影响功能。
 */
@Slf4j
@Component
public class ApprovalUnreadCache {

    public static final String KEY_PREFIX = "vio:approval:unread:";
    private static final Duration TTL = Duration.ofHours(24);
    private static final long BREAKER_OPEN_MILLIS = 30_000L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 总开关（vio.cache.unread-enabled，默认 false）：关 = 完全绕过缓存直查库 */
    @Value("${vio.cache.unread-enabled:false}")
    private boolean enabled;

    /** 熔断截止时刻（epoch 毫秒）；0 = 闭合。写竞争无害：多写同一个近似值 */
    private final AtomicLong breakerOpenUntil = new AtomicLong(0);

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 读：命中返回缓存；开关关 / 熔断中 / miss / 脏数据 都落到 loader 现算并回填。
     * 业务计数（loader）刻意放在 Redis 的 try 之外：它的异常是业务异常，不能被当缓存故障吞掉。
     */
    public UnreadCountVO get(Long memberId, Supplier<UnreadCountVO> loader) {
        if (enabled) {
            UnreadCountVO cached = tryRead(memberId);
            if (cached != null) {
                return cached;
            }
        }
        UnreadCountVO fresh = loader.get();
        if (enabled) {
            tryWrite(memberId, fresh);
        }
        return fresh;
    }

    /** 写：消费者与对账任务共用——整写两字段 + 刷 TTL。Redis 故障由调用方决定降级口径 */
    public void write(Long memberId, UnreadCountVO counts) {
        Map<String, String> hash = new HashMap<>(4);
        hash.put("todo", String.valueOf(counts.getTodoCount()));
        hash.put("result", String.valueOf(counts.getResultCount()));
        stringRedisTemplate.opsForHash().putAll(KEY_PREFIX + memberId, hash);
        stringRedisTemplate.expire(KEY_PREFIX + memberId, TTL);
    }

    /** 对账用：只有已存在的键才可能脏（不存在的键下次读自然回填），存在才值得重算 */
    public boolean hasKey(Long memberId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_PREFIX + memberId));
    }

    private UnreadCountVO tryRead(Long memberId) {
        if (breakerOpen()) {
            return null;
        }
        try {
            Map<Object, Object> hash = stringRedisTemplate.opsForHash().entries(KEY_PREFIX + memberId);
            Object todo = hash.get("todo");
            Object result = hash.get("result");
            if (todo == null || result == null) {
                return null;
            }
            UnreadCountVO vo = new UnreadCountVO();
            vo.setTodoCount(Long.parseLong(todo.toString()));
            vo.setResultCount(Long.parseLong(result.toString()));
            return vo;
        } catch (DataAccessException | NumberFormatException e) {
            trip(e);
            return null;
        }
    }

    private void tryWrite(Long memberId, UnreadCountVO counts) {
        if (breakerOpen()) {
            return;
        }
        try {
            write(memberId, counts);
        } catch (DataAccessException e) {
            trip(e);
        }
    }

    private boolean breakerOpen() {
        return System.currentTimeMillis() < breakerOpenUntil.get();
    }

    /** 熔断：打开窗口并告警（窗口内只记这一条，不刷屏） */
    private void trip(Exception e) {
        breakerOpenUntil.set(System.currentTimeMillis() + BREAKER_OPEN_MILLIS);
        log.warn("Redis 不可用，{} 秒内红点缓存降级为直查库（影响性能不影响功能），原因：{}",
                BREAKER_OPEN_MILLIS / 1000, e.getMessage());
    }
}
