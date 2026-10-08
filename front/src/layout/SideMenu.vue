<script setup lang="ts">
/**
 * 侧边目录（图纸档案 · 编号目录式菜单）
 * 对应方案：Step 2；菜单数据来自 @/config/menu（三端显隐唯一数据源）
 * ⚠️ 安全：菜单过滤仅为展示层，路由与接口权限以服务端为准
 * TODO(后续步骤)：badge 接 P08 未读数接口；当前为静态演示值
 */
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { menuOf } from '@/config/menu'
import type { MenuEntry } from '@/types'

interface MenuGroup {
  group: string
  items: MenuEntry[]
}

const user = useUserStore()

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
      <b>VIOMANAGER</b>
      <span class="mono">{{ user.role === 'STUDENT' ? '学生端 · 2026 秋' : '管理台账 · 2026 秋' }}</span>
    </div>
    <nav class="catalog-menu">
      <template v-for="g in groups" :key="g.group">
        <div class="cat-group">{{ g.group }}</div>
        <router-link
          v-for="m in g.items"
          :key="m.path"
          :to="m.path"
          active-class="active"
        >
          <span>{{ seq.get(m.path) }} {{ m.title }}</span>
          <em v-if="m.badge">{{ m.badge }}</em>
        </router-link>
      </template>
    </nav>
    <div class="catalog-foot mono">VOL.2026F · W6</div>
  </aside>
</template>

<style scoped>
.catalog {
  border-right: 1px solid var(--vio-rule);
  background: var(--vio-sheet);
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}
.catalog-head { padding: 20px 20px 14px; border-bottom: 2px solid var(--vio-ink); }
.catalog-head b { display: block; font-family: var(--vio-disp-latin); font-size: 16px; letter-spacing: 0.1em; }
.catalog-head span { font-size: 11px; color: var(--vio-ink-3); letter-spacing: 0.12em; }
.catalog-menu { flex: 1; padding: 10px 14px 16px; overflow: auto; }
.cat-group {
  margin: 16px 0 5px;
  font-family: var(--vio-serif);
  font-weight: 700;
  font-size: 12px;
  letter-spacing: 0.2em;
  color: var(--vio-ink);
}
.catalog-menu a {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 7px 10px;
  margin-bottom: 1px;
  font-size: 13px;
  color: var(--vio-ink-2);
  font-family: var(--vio-mono);
  text-decoration: none;
  transition: background 0.2s var(--vio-ease), color 0.2s var(--vio-ease);
}
.catalog-menu a:hover { background: var(--vio-wash); color: var(--vio-blue-ink); }
.catalog-menu a.active {
  background: var(--vio-wash);
  color: var(--vio-blue-ink);
  font-weight: 700;
  box-shadow: inset 3px 0 0 var(--vio-blue-ink);
}
.catalog-menu a em {
  font-style: normal;
  font-size: 11px;
  color: var(--vio-seal);
  font-weight: 700;
}
.catalog-foot {
  padding: 13px 20px;
  border-top: 1px solid var(--vio-rule-soft);
  font-size: 10.5px;
  color: var(--vio-ink-3);
  letter-spacing: 0.14em;
}
</style>
