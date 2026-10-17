package com.viomanager.datascop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import jakarta.annotation.Resource;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * 插入点①的降级验证（方案验收标准：Redis 挂了接口不倒）。
 * mock 掉 StringRedisTemplate 让每次 Redis 命令都抛连接异常，
 * 组织子树判定必须原样工作（透明回退直查库），且熔断打开后不再反复撞 Redis。
 */
@SpringBootTest(properties = {"vio.cache.org-enabled=true",
        "spring.datasource.hikari.maximum-pool-size=2"})
@Transactional
class OrgScopeCacheDegradeTest {

    private static final long PARENT = 909211L;
    private static final long CHILD = 909212L;

    @Resource
    private OrgScopeSupport orgScopeSupport;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void seedOrgs() {
        jdbcTemplate.update("INSERT INTO sys_org (id, parent_id, name, org_type, path) "
                + "VALUES (?, 0, '降级测试部门Y', 'DEPARTMENT', '/0/909211/')", PARENT);
        jdbcTemplate.update("INSERT INTO sys_org (id, parent_id, name, org_type, path) "
                + "VALUES (?, ?, '降级测试子组织', 'SUB_ORG', '/0/909211/909212/')", CHILD, PARENT);
        //让一切 Redis 命令（读缓存/回写）都抛连接异常，模拟 Redis 宕机
        when(redisTemplate.opsForSet())
                .thenThrow(new RedisConnectionFailureException("redis down"));
    }

    @Test
    void redisDownFallsBackToDb() {
        //1.读路径：缓存层异常被吞并熔断，透明回退查库，结果与直查一致
        List<Long> subtree = orgScopeSupport.subtreeIds(PARENT);
        assertEquals(2, subtree.size());
        assertTrue(subtree.contains(PARENT));
        assertTrue(subtree.contains(CHILD));
        //2.单点判定同样降级正常
        assertTrue(orgScopeSupport.inSubtree(PARENT, CHILD));
        assertFalse(orgScopeSupport.inSubtree(CHILD, PARENT));
        //3.第二次调用：熔断窗口内连 Redis 都不再碰，依旧从库拿对
        List<Long> again = orgScopeSupport.subtreeIds(PARENT);
        assertEquals(2, again.size());
    }
}
