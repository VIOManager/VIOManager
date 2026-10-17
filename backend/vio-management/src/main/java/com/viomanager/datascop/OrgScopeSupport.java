package com.viomanager.datascop;

import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 组织子树判定的统一收口（审批中心升级插入点①）。
 * 之前同一套 path LIKE 子树查询复制在 approval/contest/file/person/team 五个包共 10 处，
 * 数据范围又是全接口热路径——组织树几乎不变，这里加 Redis cache-aside（Set 存子树 id，TTL 10 分钟）。
 * 降级原则与 kb 推荐位缓存同款：Redis 是纯优化不是依赖，yml 命令超时 1s + 这里 30s 熔断，
 * 任何故障直接查库，行为与收口前完全一致。
 */
@Slf4j
@Component
public class OrgScopeSupport {

    private static final String KEY_PREFIX = "vio:org:subtree:";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final long BREAKER_OPEN_MILLIS = 30_000L;

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 按 @Resource 名字注入规则刻意叫 stringRedisTemplate：叫 redisTemplate 会按名命中泛型 RedisTemplate bean 直接类型炸 */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 缓存开关（vio.cache.org-enabled，默认 false）：关 = 永远直查库，行为与收口前一致 */
    @Value("${vio.cache.org-enabled:false}")
    private boolean cacheEnabled;

    /** 熔断截止时刻（epoch 毫秒）；0 = 闭合。写竞争无害：多写同一个近似值 */
    private final AtomicLong breakerOpenUntil = new AtomicLong(0);

    /**
     * 管理员组织子树 ID 列表（含自己）——严格版，审批/赛队/比赛三处同款语义：
     * 未配组织、组织不存在、查询失败一律 1003（按无权限处理，宁可误拒不可误放）。
     */
    public List<Long> subtreeIds(Long orgId) {
        //1.未配组织直接拒
        if (orgId == null) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "部门管理员未配置所属组织");
        }
        //2.先试缓存
        List<Long> cached = tryCache(orgId);
        if (cached != null) {
            return cached;
        }
        //3.查库回写（组织不存在与查询失败都按无权限拒绝）
        try {
            List<Long> ids = querySubtreeFromDb(orgId);
            if (ids == null) {
                throw new BizException(CommonErrorCode.FORBIDDEN, "组织不存在或已删除");
            }
            putCache(orgId, ids);
            return ids;
        } catch (BizException e) {
            throw e;
        } catch (DataAccessException e) {
            log.warn("子树组织查询失败，按无权限处理：{}", e.getMessage());
            throw new BizException(CommonErrorCode.FORBIDDEN);
        }
    }

    /**
     * 宽松版：人员模块 M01 列表过滤的现行为——没配组织/组织不存在返回空列表，
     * 数据库异常照旧向上冒泡（不吞），与收口前逐点一致。
     */
    public List<Long> subtreeIdsOrEmpty(Long orgId) {
        if (orgId == null) {
            return List.of();
        }
        List<Long> cached = tryCache(orgId);
        if (cached != null) {
            return cached;
        }
        List<Long> ids = querySubtreeFromDb(orgId);
        if (ids == null) {
            return List.of();
        }
        putCache(orgId, ids);
        return ids;
    }

    /**
     * 目标组织是否在管理员子树内（含自己）——单点越权判定的通用口。
     * 任一端为空、组织不存在、查询失败都判不在（fail-closed）。
     */
    public boolean inSubtree(Long adminOrgId, Long targetOrgId) {
        if (adminOrgId == null || targetOrgId == null) {
            return false;
        }
        List<Long> cached = tryCache(adminOrgId);
        if (cached != null) {
            return cached.contains(targetOrgId);
        }
        try {
            List<Long> ids = querySubtreeFromDb(adminOrgId);
            if (ids == null) {
                return false;
            }
            putCache(adminOrgId, ids);
            return ids.contains(targetOrgId);
        } catch (DataAccessException e) {
            log.warn("子树校验查询失败，按不在子树处理：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 清全部子树缓存。刻意不按单个 orgId 清：祖先组织的缓存集里嵌着子孙的 id，
     * 动一个节点全家族都可能变脏；组织量级百级，整族清最稳也最简单。
     * 组织写操作请调 evictOrgCacheAfterCommit，不要直接调这个。
     */
    public void evictOrgCache() {
        if (!cacheEnabled) {
            return;
        }
        try {
            Set<String> keys = stringRedisTemplate.keys(KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (DataAccessException e) {
            trip(e);
        }
    }

    /**
     * 组织写操作后的失效口：有事务挂 afterCommit 再清——事务内先清会被并发读
     * 在提交前回填旧数据（清了等于没清），回滚则不清（数据没变，缓存本来就该留）。
     */
    public void evictOrgCacheAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictOrgCache();
                }
            });
        } else {
            evictOrgCache();
        }
    }

    // ---------- 私有支撑 ----------

    /**
     * 查库版子树：组织不存在返回 null。刻意用 query 列表判空而不是 queryForObject——
     * 后者查不到会抛 EmptyResultDataAccessException，收口前各处的"组织不存在"分支其实接不到
     * （person 原实现靠 NULL 子查询才做到出空列表）；统一 null 契约后三个口子的语义都接得干净。
     * SQL 与收口前各处逐字相同。
     */
    private List<Long> querySubtreeFromDb(Long orgId) {
        List<String> paths = jdbcTemplate.query(
                "SELECT path FROM sys_org WHERE id = ? AND deleted = 0",
                (rs, rowNum) -> rs.getString(1), orgId);
        if (paths.isEmpty() || paths.get(0) == null) {
            return null;
        }
        return jdbcTemplate.queryForList(
                "SELECT id FROM sys_org WHERE path LIKE CONCAT(?, '%') AND deleted = 0",
                Long.class, paths.get(0));
    }

    /** 读缓存：开关关/熔断中/未命中都返回 null（当没缓存，走库） */
    private List<Long> tryCache(Long orgId) {
        if (!cacheEnabled || breakerOpen()) {
            return null;
        }
        try {
            Set<String> members = stringRedisTemplate.opsForSet().members(KEY_PREFIX + orgId);
            if (members == null || members.isEmpty()) {
                return null;
            }
            List<Long> ids = new ArrayList<>();
            for (String member : members) {
                ids.add(Long.valueOf(member));
            }
            return ids;
        } catch (DataAccessException e) {
            trip(e);
            return null;
        }
    }

    /** 回写缓存：失败只损失缓存，不影响返回值 */
    private void putCache(Long orgId, List<Long> ids) {
        if (!cacheEnabled || ids.isEmpty() || breakerOpen()) {
            return;
        }
        try {
            String[] members = new String[ids.size()];
            for (int i = 0; i < ids.size(); i++) {
                members[i] = String.valueOf(ids.get(i));
            }
            stringRedisTemplate.opsForSet().add(KEY_PREFIX + orgId, members);
            stringRedisTemplate.expire(KEY_PREFIX + orgId, TTL);
        } catch (DataAccessException e) {
            trip(e);
        }
    }

    private boolean breakerOpen() {
        return System.currentTimeMillis() < breakerOpenUntil.get();
    }

    /** 熔断：打开窗口并告警（窗口内只记这一条，不刷屏） */
    private void trip(DataAccessException e) {
        breakerOpenUntil.set(System.currentTimeMillis() + BREAKER_OPEN_MILLIS);
        log.warn("Redis 不可用，{} 秒内组织子树缓存降级为直查库（影响性能不影响功能），原因：{}",
                BREAKER_OPEN_MILLIS / 1000, e.getMessage());
    }
}
