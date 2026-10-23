<script setup lang="ts">
/**
 * 侧边目录（粗野贴纸 · SEC 编组导航）
 * 对应方案：Step 2；S2 双主题粗野重涂（2026-10-15 方案）；菜单数据来自 @/config/menu（三端显隐唯一数据源）
 * ⚠️ 安全：菜单过滤仅为展示层，路由与接口权限以服务端为准
 * badge：审批中心项接 P08 未读数（stores/approval），其余项暂无红点
 */
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { useApprovalStore } from '@/stores/approval'
import { menuOf } from '@/config/menu'
import type { MenuEntry } from '@/types'

interface MenuGroup {
  group: string
  items: MenuEntry[]
}

const user = useUserStore()
const approval = useApprovalStore()

/** 菜单红点：审批中心 = P08 合计，其余项走静态配置 */
function badgeOf(m: MenuEntry): number {
  if (m.path === '/approval') return approval.total
  return m.badge ?? 0
}

/** 论坛站入口（独立站，JWT 与主站共享）：地址走环境变量，开发期默认 5175；新标签打开主站会话不丢。
 *  跨端口即跨源，sessionStorage 不互通——登录态经 URL 一次性带给论坛站，其入口读进后随即抹除（2026-10-23） */
const forumUrl = import.meta.env.VITE_FORUM_URL ?? 'http://localhost:5175'
const forumHref = computed(() => user.token
  ? `${forumUrl}?token=${encodeURIComponent(user.token)}`
  : forumUrl)

/** 按角色过滤后保持分组顺序收拢 */
const groups = computed<MenuGroup[]>(() => {
  const list = menuOf(user.role)
  const out: MenuGroup[] = []
  for (const item of list) {
    const g = out.find((x) => x.group === item.group)
    if (g) g.items.push(item)
    else out.push({ group: item.group, items: [item] })
  }
  return out
})

/** 目录序号：按过滤后的全量顺序编号，保持"台账"语汇 */
const seq = computed(() => {
  const map = new Map<string, string>()
  menuOf(user.role).forEach((m, i) => map.set(m.path, String(i + 1).padStart(2, '0')))
  return map
})
</script>

<template>
  <aside class="catalog">
    <div class="catalog-head">
      <span class="brand">VIO<span class="tick">.</span></span>
      <span class="mono sub">{{ user.role === 'STUDENT' ? 'STUDENT DECK' : 'ADMIN DECK' }} · 2026F</span>
    </div>
    <nav class="catalog-menu">
      <template v-for="g in groups" :key="g.group">
        <div class="cat-group mono">SEC · {{ g.group }}</div>
        <router-link
          v-for="m in g.items"
          :key="m.path"
          :to="m.path"
          active-class="active"
        >
          <span class="m-no mono">{{ seq.get(m.path) }}</span>
          <span class="m-title">{{ m.title }}</span>
          <em v-if="badgeOf(m)" class="mono">{{ badgeOf(m) }}</em>
        </router-link>
      </template>
    </nav>
    <div class="catalog-foot">
      <a class="forum-link mono" :href="forumHref" target="_blank" rel="noopener">论坛 FORUM ↗</a>
      <span class="mono">VOL.2026F</span>
    </div>
  </aside>
</template>

<style scoped>
.catalog {
  border-right: 3px solid var(--vio2-bd);
  background: var(--vio2-panel);
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  position: relative;
  z-index: 1; /* 压过壳层网格底纹 */
}
.catalog-head { padding: 18px 18px 14px; border-bottom: 3px solid var(--vio2-bd); }
.brand {
  display: inline-block;
  background: var(--vio2-yellow);
  color: #101010;
  border: 3px solid var(--vio2-bd);
  box-shadow: 4px 4px 0 var(--vio2-shadow);
  padding: 2px 10px 3px;
  font-family: var(--vio2-din);
  font-weight: 900;
  font-size: 22px;
  letter-spacing: 0.04em;
  transform: rotate(-2deg);
}
.tick { color: var(--vio2-pink); }
.catalog-head .sub {
  display: block;
  margin-top: 10px;
  font-size: 10px;
  color: var(--vio2-ink-3);
  letter-spacing: 0.22em;
}
.catalog-menu { flex: 1; padding: 12px 14px 16px; overflow: auto; }
.cat-group {
  margin: 14px 2px 6px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.18em;
  color: var(--vio2-ink-3);
}
.catalog-menu a {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 9px;
  margin: 0 0 6px;
  font-size: 12.5px;
  color: var(--vio2-ink-2);
  background: var(--vio2-panel-2);
  border: 2px solid var(--vio2-bd);
  box-shadow: 3px 3px 0 var(--vio2-shadow);
  text-decoration: none;
  transition: transform 0.15s var(--vio-ease), background 0.15s var(--vio-ease), color 0.15s var(--vio-ease);
}
.catalog-menu a:hover { transform: translate(-1px, -1px); color: var(--vio2-ink); }
.catalog-menu a.active {
  background: var(--vio2-yellow);
  color: #101010;
  font-weight: 700;
  transform: translate(-1px, -1px);
}
.m-no { font-size: 10px; color: var(--vio2-ink-3); font-weight: 700; }
a.active .m-no { color: #101010; }
.m-title { flex: 1; }
.catalog-menu a em {
  font-style: normal;
  font-size: 10px;
  font-weight: 700;
  color: #101010;
  background: var(--vio2-pink);
  border: 2px solid var(--vio2-bd);
  padding: 0 5px;
}
.catalog-foot {
  padding: 12px 18px;
  border-top: 3px solid var(--vio2-bd);
  font-size: 10px;
  color: var(--vio2-ink-3);
  letter-spacing: 0.14em;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.forum-link {
  color: var(--vio2-blue);
  text-decoration: none;
  font-weight: 700;
  font-size: 10px;
  letter-spacing: 0.1em;
}
.forum-link:hover { text-decoration: underline; }
</style>
