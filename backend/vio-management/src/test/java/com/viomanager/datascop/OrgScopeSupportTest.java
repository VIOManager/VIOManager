package com.viomanager.datascop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.viomanager.common.exception.BizException;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 插入点①组织树缓存——语义与命中、失效验证（方案 Step 3）。
 * 组织树：部门X(909201) → 子组织(909202)。
 * 命中证明用"篡改法"：往 Redis 塞假 id，读回假值即证明这条读走的是缓存不是库。
 * 注意：Redis 写入不受 @Transactional 回滚保护，@AfterEach 必须手工清键。
 */
@SpringBootTest(properties = {"vio.cache.org-enabled=true",
        "spring.datasource.hikari.maximum-pool-size=2"})
@Transactional
class OrgScopeSupportTest {

    private static final long PARENT = 909201L;
    private static final long CHILD = 909202L;
    private static final String KEY_PREFIX = "vio:org:subtree:";

    @Resource
    private OrgScopeSupport orgScopeSupport;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void seedOrgs() {
        jdbcTemplate.update("INSERT INTO sys_org (id, parent_id, name, org_type, path) "
                + "VALUES (?, 0, '缓存测试部门X', 'DEPARTMENT', '/0/909201/')", PARENT);
        jdbcTemplate.update("INSERT INTO sys_org (id, parent_id, name, org_type, path) "
                + "VALUES (?, ?, '缓存测试子组织', 'SUB_ORG', '/0/909201/909202/')", CHILD, PARENT);
    }

    @AfterEach
    void cleanRedis() {
        Set<String> keys = stringRedisTemplate.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    void subtreeQueriesMatchSqlSemantics() {
        //1.严格口子：没配组织 / 组织不存在 → 1003（审批/赛队/比赛三处同款语义）
        BizException noOrg = assertThrows(BizException.class, () -> orgScopeSupport.subtreeIds(null));
        assertEquals(1003, noOrg.getCode());
        BizException missing = assertThrows(BizException.class, () -> orgScopeSupport.subtreeIds(9999999L));
        assertEquals(1003, missing.getCode());
        //2.子树含自己与子孙
        List<Long> subtree = orgScopeSupport.subtreeIds(PARENT);
        assertEquals(2, subtree.size());
        assertTrue(subtree.contains(PARENT));
        assertTrue(subtree.contains(CHILD));
        //3.单点判定：向下在子树内、向上不在、空端点/不存在一律不在
        assertTrue(orgScopeSupport.inSubtree(PARENT, CHILD));
        assertFalse(orgScopeSupport.inSubtree(CHILD, PARENT));
        assertFalse(orgScopeSupport.inSubtree(null, CHILD));
        assertFalse(orgScopeSupport.inSubtree(PARENT, null));
        assertFalse(orgScopeSupport.inSubtree(PARENT, 9999999L));
        //4.宽松口子：没配组织 / 组织不存在返回空列表（人员 M01 的现行为）
        assertTrue(orgScopeSupport.subtreeIdsOrEmpty(null).isEmpty());
        assertTrue(orgScopeSupport.subtreeIdsOrEmpty(9999999L).isEmpty());
        assertEquals(2, orgScopeSupport.subtreeIdsOrEmpty(PARENT).size());
    }

    @Test
    void cacheHitServesFromRedisNotDb() {
        //1.首读查库并回填缓存：键存在、成员正确、带 TTL
        orgScopeSupport.subtreeIds(PARENT);
        String key = KEY_PREFIX + PARENT;
        Set<String> members = stringRedisTemplate.opsForSet().members(key);
        assertTrue(members != null && members.contains(String.valueOf(PARENT)));
        assertTrue(stringRedisTemplate.getExpire(key) > 0);
        //2.篡改缓存再读：读回假值 = 这条读命中了缓存而不是查库
        stringRedisTemplate.delete(key);
        stringRedisTemplate.opsForSet().add(key, "999999");
        List<Long> cached = orgScopeSupport.subtreeIds(PARENT);
        assertEquals(1, cached.size());
        assertEquals(999999L, cached.get(0));
    }

    @Test
    void evictClearsWholeFamilyAndNextReadRebuilds() {
        //1.两个组织的子树集都进缓存
        orgScopeSupport.subtreeIds(PARENT);
        orgScopeSupport.subtreeIds(CHILD);
        assertFalse(stringRedisTemplate.opsForSet().members(KEY_PREFIX + PARENT).isEmpty());
        assertFalse(stringRedisTemplate.opsForSet().members(KEY_PREFIX + CHILD).isEmpty());
        //2.整族清空：键全消失
        orgScopeSupport.evictOrgCache();
        assertTrue(stringRedisTemplate.opsForSet().members(KEY_PREFIX + PARENT).isEmpty());
        assertTrue(stringRedisTemplate.opsForSet().members(KEY_PREFIX + CHILD).isEmpty());
        //3.下一读从库重建回真值
        List<Long> rebuilt = orgScopeSupport.subtreeIds(PARENT);
        assertEquals(2, rebuilt.size());
        assertTrue(rebuilt.contains(CHILD));
    }
}
