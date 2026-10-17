package com.viomanager.kb.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.kb.vo.KbDocVO;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * KB03 推荐位缓存——Redis 版（2026-10-15 组长拍板：缓存落本模块，接本机 Redis）。
 * 口径不变：TTL 60 秒兜底 + 管理写操作（发布/编辑/删除/置顶/上下架）主动失效——
 * 置顶最迟 1 分钟对全员生效，可接受（方案文档决策项 4）。
 * 相比进程内版的新增收益：缓存跨服务重启存活；键按 limit 分开（修掉不同条数共用一份快照的旧瑕疵）。
 * 降级原则：Redis 是纯优化不是依赖——任何故障直接查库重建，服务没有 Redis 也必须能跑；
 * 熔断 30 秒避免故障期每请求都等命令超时（yml 里命令超时压到 1s）。
 */
@Slf4j
@Component
public class KbRecommendCache {

    private static final String KEY_PREFIX = "vio:kb:recommend:";
    private static final Duration TTL = Duration.ofSeconds(60);
    /** limit 上限与查询口一致（QuerySupport.recommend 里 Math.min(limit, 10)） */
    private static final int MAX_LIMIT = 10;
    private static final long BREAKER_OPEN_MILLIS = 30_000L;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /** 熔断截止时刻（epoch 毫秒）；0 = 闭合。写竞争无害：多写同一个近似值 */
    private final AtomicLong breakerOpenUntil = new AtomicLong(0);

    public KbRecommendCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /** 读：命中未过期键直接返回；未命中执行 loader 重建并回填；Redis 故障透明降级为直查库 */
    public List<KbDocVO> get(int limit, Supplier<List<KbDocVO>> loader) {
        String key = KEY_PREFIX + limit;
        String cached = tryGet(key);
        if (cached != null) {
            List<KbDocVO> items = deserialize(cached);
            if (items != null) {
                return items;
            }
        }
        //业务查询刻意放在 Redis 的 try 之外：它的异常是业务异常，不能被当成缓存故障
        List<KbDocVO> fresh = loader.get();
        trySet(key, serialize(fresh));
        return fresh;
    }

    /** 失效：管理写操作后调用，清全部条数档位的键（1..10） */
    public void invalidate() {
        if (breakerOpen()) {
            return;
        }
        try {
            for (int n = 1; n <= MAX_LIMIT; n++) {
                redis.delete(KEY_PREFIX + n);
            }
        } catch (DataAccessException e) {
            trip(e);
        }
    }

    private String tryGet(String key) {
        if (breakerOpen()) {
            return null;
        }
        try {
            return redis.opsForValue().get(key);
        } catch (DataAccessException e) {
            trip(e);
            return null;
        }
    }

    private void trySet(String key, String value) {
        if (value == null || breakerOpen()) {
            return;
        }
        try {
            redis.opsForValue().set(key, value, TTL);
        } catch (DataAccessException e) {
            trip(e);
        }
    }

    private String serialize(List<KbDocVO> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            //序列化失败只损失缓存，不影响业务返回
            return null;
        }
    }

    private List<KbDocVO> deserialize(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<KbDocVO>>() {
            });
        } catch (Exception e) {
            //脏数据/版本不兼容：当未命中处理，重建
            return null;
        }
    }

    private boolean breakerOpen() {
        return System.currentTimeMillis() < breakerOpenUntil.get();
    }

    /** 熔断：打开窗口并告警（窗口内只记这一条，不刷屏） */
    private void trip(DataAccessException e) {
        breakerOpenUntil.set(System.currentTimeMillis() + BREAKER_OPEN_MILLIS);
        log.warn("Redis 不可用，{} 秒内推荐位缓存降级为直查库（影响性能不影响功能），原因：{}",
                BREAKER_OPEN_MILLIS / 1000, e.getMessage());
    }
}
