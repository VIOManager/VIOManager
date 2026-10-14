package com.viomanager.kb.support;

import com.viomanager.kb.vo.KbDocVO;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * KB03 推荐位本地缓存（方案"缓存"对策：读多写少的最典型点）。
 * 口径：TTL 60 秒兜底 + 管理写操作（发布/编辑/删除/置顶/上下架）主动失效——
 * 置顶最迟 1 分钟对全员生效，可接受（方案文档决策项 4）。
 * 刻意不用 Caffeine/Redis：200 人规模不值得为 5 条卡片引入缓存组件。
 * 线程安全：AtomicReference 整体替换，读永远拿到完整快照，不会读到半新半旧。
 */
@Component
public class KbRecommendCache {

    /** 缓存有效期（毫秒） */
    static final long TTL_MILLIS = 60_000L;

    private record Snapshot(List<KbDocVO> items, long cachedAt) {
    }

    private final AtomicReference<Snapshot> holder = new AtomicReference<>();

    /** 读：命中未过期快照直接返回；否则调 loader 重建（并发重建最多多算一次，无害） */
    public List<KbDocVO> get(Supplier<List<KbDocVO>> loader) {
        Snapshot snapshot = holder.get();
        if (snapshot != null && System.currentTimeMillis() - snapshot.cachedAt() < TTL_MILLIS) {
            return snapshot.items();
        }
        List<KbDocVO> fresh = List.copyOf(loader.get());
        holder.set(new Snapshot(fresh, System.currentTimeMillis()));
        return fresh;
    }

    /** 失效：管理写操作后调用，下次读取重建 */
    public void invalidate() {
        holder.set(null);
    }
}
