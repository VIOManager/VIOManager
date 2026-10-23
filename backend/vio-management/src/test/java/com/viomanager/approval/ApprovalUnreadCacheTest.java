package com.viomanager.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.viomanager.approval.support.ApprovalUnreadCache;
import com.viomanager.approval.vo.UnreadCountVO;
import jakarta.annotation.Resource;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 插入点②读路径缓存语义（方案 Step 6）：miss 现算回填 / 命中直接返回 / 旧值被整写覆盖。
 * 真 Redis；成员 ID 每轮 nanoTime 派生，防上一轮残留键（TTL 24 小时）干扰 miss 断言。
 */
@SpringBootTest(properties = {
        "vio.cache.unread-enabled=true",
        "spring.datasource.hikari.maximum-pool-size=2"})
class ApprovalUnreadCacheTest {

    private static final long MEMBER = 95_000_000L + System.nanoTime() % 4_000_000L;

    @Resource
    private ApprovalUnreadCache approvalUnreadCache;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @AfterEach
    void cleanUp() {
        stringRedisTemplate.delete(ApprovalUnreadCache.KEY_PREFIX + MEMBER);
    }

    @Test
    void missBackfillsThenHitServesCache() {
        String key = ApprovalUnreadCache.KEY_PREFIX + MEMBER;
        //1.miss：现算 (3,5) 返回并回填
        assertNull(hashGet(key, "todo"));
        UnreadCountVO first = approvalUnreadCache.get(MEMBER, () -> vo(3, 5));
        assertEquals(3, first.getTodoCount());
        assertEquals(5, first.getResultCount());
        assertEquals("3", hashGet(key, "todo"));
        assertEquals("5", hashGet(key, "result"));

        //2.命中：loader 不再被调用（计数器证明），返回的是缓存值
        AtomicInteger loaderCalls = new AtomicInteger();
        UnreadCountVO second = approvalUnreadCache.get(MEMBER, () -> {
            loaderCalls.incrementAndGet();
            return vo(99, 99);
        });
        assertEquals(3, second.getTodoCount());
        assertEquals(5, second.getResultCount());
        assertEquals(0, loaderCalls.get());
    }

    @Test
    void writeOverwritesStaleValue() {
        //1.先落一份旧值
        approvalUnreadCache.write(MEMBER, vo(7, 7));
        //2.write 整写覆盖（消费者与对账走的就是这个口）
        approvalUnreadCache.write(MEMBER, vo(1, 2));
        assertEquals("1", hashGet(ApprovalUnreadCache.KEY_PREFIX + MEMBER, "todo"));
        assertEquals("2", hashGet(ApprovalUnreadCache.KEY_PREFIX + MEMBER, "result"));
    }

    private UnreadCountVO vo(int todo, int result) {
        UnreadCountVO vo = new UnreadCountVO();
        vo.setTodoCount(todo);
        vo.setResultCount(result);
        return vo;
    }

    private String hashGet(String key, String field) {
        Object value = stringRedisTemplate.opsForHash().get(key, field);
        return value == null ? null : value.toString();
    }
}
