package com.viomanager.team.support;

import com.viomanager.common.enums.TeamErrorCode;
import com.viomanager.common.exception.BizException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 赛队分布式锁（组队超卖修复，方案 §12.5）：Redisson，teamId / memberId 两个粒度。
 * 降级方向与缓存类相反——Redis 不可用或竞争超时一律抛 4013 拒绝（放行等于回到超卖老路），
 * 由调用方连同事务整体回滚。
 * 客户端懒创建 + 30 秒熔断：Redis 未起时应用照常启动，锁请求快速失败而非每次等连接超时
 * （熔断节奏与 OrgScopeSupport / ApprovalUnreadCache 同款，方向相反是刻意设计）。
 */
@Slf4j
@Component
public class TeamLockSupport {

    /** 加人锁前缀：teamId 粒度，只锁一支队的并发加人 */
    public static final String ADD_LOCK_PREFIX = "vio:team:member-add:";
    /** 确认锁前缀：memberId 粒度，防同一人跨队并发双确认 */
    public static final String CONFIRM_LOCK_PREFIX = "vio:team:member-confirm:";

    private static final long TRY_WAIT_SECONDS = 3;
    private static final long BREAKER_MILLIS = 30_000;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;
    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    private volatile RedissonClient client;
    private volatile long breakerUntil;

    /** 加人锁：同一队的并发加人串行化；拿不到（竞争超时 / Redis 不可用）抛 4013 */
    public RLock lockForAdd(Long teamId) {
        return acquire(ADD_LOCK_PREFIX + teamId);
    }

    /** 确认锁：同一人的并发确认串行化（两队同时确认只成一个） */
    public RLock lockForConfirm(Long memberId) {
        return acquire(CONFIRM_LOCK_PREFIX + memberId);
    }

    /** 释放：isHeldByCurrentThread 守门——看门狗续期期间也不会误删他人持有的锁 */
    public void release(RLock lock) {
        if (lock != null && lock.isHeldByCurrentThread()) {
            try {
                lock.unlock();
            } catch (RuntimeException e) {
                log.warn("分布式锁释放失败（不影响业务结果）：{}", e.getMessage());
            }
        }
    }

    private RLock acquire(String key) {
        RLock lock = client().getLock(key);
        try {
            // 不传 leaseTime 走看门狗自动续期：业务没跑完锁不会因超时被误释放
            if (!lock.tryLock(TRY_WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new BizException(TeamErrorCode.TEAM_LOCK_BUSY, "并发竞争超时");
            }
            return lock;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(TeamErrorCode.TEAM_LOCK_BUSY, "加锁等待被中断");
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            trip();
            throw new BizException(TeamErrorCode.TEAM_LOCK_BUSY, "分布式锁暂不可用");
        }
    }

    private RedissonClient client() {
        RedissonClient c = client;
        if (c != null) {
            return c;
        }
        if (System.currentTimeMillis() < breakerUntil) {
            throw new BizException(TeamErrorCode.TEAM_LOCK_BUSY, "分布式锁熔断中（近期 Redis 不可用）");
        }
        synchronized (this) {
            if (client == null) {
                try {
                    Config config = new Config();
                    config.useSingleServer()
                            .setAddress("redis://" + redisHost + ":" + redisPort)
                            .setConnectTimeout(1000)
                            .setTimeout(1000)
                            .setRetryAttempts(1)
                            .setRetryInterval(200);
                    client = Redisson.create(config);
                    log.info("Redisson 已就绪 {}:{}（赛队分布式锁）", redisHost, redisPort);
                } catch (RuntimeException e) {
                    trip();
                    throw new BizException(TeamErrorCode.TEAM_LOCK_BUSY, "分布式锁初始化失败");
                }
            }
            return client;
        }
    }

    private void trip() {
        breakerUntil = System.currentTimeMillis() + BREAKER_MILLIS;
        log.warn("Redisson 不可用，{} 秒内赛队锁直接拒绝——锁场景宁拒不放行", BREAKER_MILLIS / 1000);
    }
}
