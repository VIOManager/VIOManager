package com.viomanager.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.viomanager.approval.support.ApprovalUnreadCache;
import com.viomanager.approval.vo.UnreadCountVO;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 插入点②降级验证（方案 Step 6，OrgScopeCacheDegradeTest 同款手法）：
 * Redis 全挂时读路径透明回退直查库——业务返回不受影响，loader 照常执行；
 * 回写失败只损失缓存不炸调用方。
 * @DirtiesContext：@MockitoBean 的模板不能污染共享的缓存上下文。
 */
@SpringBootTest(properties = {
        "vio.cache.unread-enabled=true",
        "spring.datasource.hikari.maximum-pool-size=2"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApprovalUnreadCacheDegradeTest {

    private static final long MEMBER = 96_000_000L;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ApprovalUnreadCache approvalUnreadCache;

    @Test
    @SuppressWarnings("unchecked")
    void redisDownFallsBackToLoader() {
        //1.所有 Redis 命令都抛连接异常（读与写一起挂）
        HashOperations<String, Object, Object> hashOps =
                org.mockito.Mockito.mock(HashOperations.class);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(anyString()))
                .thenThrow(new DataAccessResourceFailureException("mock redis down"));
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("mock redis down"))
                .when(hashOps).putAll(anyString(), org.mockito.ArgumentMatchers.anyMap());
        when(stringRedisTemplate.expire(anyString(), org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenThrow(new DataAccessResourceFailureException("mock redis down"));

        //2.两次读：都直查库拿到 loader 值，熔断窗口内不重复撞 Redis
        AtomicInteger loaderCalls = new AtomicInteger();
        UnreadCountVO first = approvalUnreadCache.get(MEMBER, () -> {
            loaderCalls.incrementAndGet();
            return vo(2, 4);
        });
        UnreadCountVO second = approvalUnreadCache.get(MEMBER, () -> {
            loaderCalls.incrementAndGet();
            return vo(2, 4);
        });
        assertEquals(2, first.getTodoCount());
        assertEquals(4, second.getResultCount());
        assertEquals(2, loaderCalls.get());
    }

    private UnreadCountVO vo(int todo, int result) {
        UnreadCountVO vo = new UnreadCountVO();
        vo.setTodoCount(todo);
        vo.setResultCount(result);
        return vo;
    }
}
