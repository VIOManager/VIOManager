package com.viomanager.datascop;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 组织树缓存预热（2026-10-20 组长规矩：必要的热路径先缓存预热）。
 * 数据范围判定是全接口热路径，cache-aside 意味着重启后第一个用户要付两条 SQL 的冷启动费，
 * 且 TTL 10 分钟过期后每个组织的第一笔请求都要再付一次——本任务把这两笔费包下来：
 * 启动即预热 + 每 8 分钟续温（续在 10 分钟 TTL 过期之前），读路径永远命中暖缓存。
 * 开关关着（vio.cache.org-enabled=false）时 warmAll 内部空转，本任务每轮零成本跳过。
 */
@Slf4j
@Component
public class OrgCacheWarmUp {

    @Resource
    private OrgScopeSupport orgScopeSupport;

    /** fixedDelay 首轮在启动后立即执行，之后每轮结束再等 8 分钟 */
    @Scheduled(fixedDelay = 480_000)
    public void warm() {
        int warmed = orgScopeSupport.warmAll();
        if (warmed > 0) {
            log.info("组织树缓存预热完成：{} 个组织的子树族已续温", warmed);
        }
    }
}
