package com.viomanager.team.support;

import com.viomanager.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 赛队锁降级行为：锁场景宁拒不放行（与缓存降级方向相反）。
 * 死端口验证懒创建失败 → 4013，且 30 秒熔断期内快速失败不再等连接超时。
 */
class TeamLockSupportTest {

    @Test
    void redis不可用时按4013拒绝且熔断快速失败() {
        TeamLockSupport support = new TeamLockSupport();
        ReflectionTestUtils.setField(support, "redisHost", "127.0.0.1");
        ReflectionTestUtils.setField(support, "redisPort", 16399);
        BizException first = assertThrows(BizException.class, () -> support.lockForAdd(1L));
        assertEquals(4013, first.getCode());
        long start = System.currentTimeMillis();
        BizException second = assertThrows(BizException.class, () -> support.lockForConfirm(2L));
        assertEquals(4013, second.getCode());
        assertTrue(System.currentTimeMillis() - start < 500, "熔断期内应快速失败，不再付连接超时的成本");
    }

    @Test
    void release空锁安全() {
        TeamLockSupport support = new TeamLockSupport();
        assertDoesNotThrow(() -> support.release(null));
    }
}
