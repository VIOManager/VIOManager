<script setup lang="ts">
/**
 * 排班管理页（/schedule，管理端，TT01–TT08 + SC01–SC11）
 * 四面板：周排班表（格子排班）/ 空课查询（7×5 矩阵+单格名单）/ 课表导入（批次+映射确认）/ 规则配置
 * 学生端菜单隐藏本页（SC05 我的排班在 /my/schedule）
 */
import { ref } from 'vue'
import ScheduleGridPanel from './ScheduleGridPanel.vue'
import FreeSlotPanel from './FreeSlotPanel.vue'
import TimetablePanel from './TimetablePanel.vue'
import ScheduleRulePanel from './ScheduleRulePanel.vue'

type TabKey = 'grid' | 'free' | 'timetable' | 'rule'
const tab = ref<TabKey>('grid')

const TABS: Array<{ key: TabKey; label: string }> = [
  { key: 'grid', label: '周排班表' },
  { key: 'free', label: '空课查询' },
  { key: 'timetable', label: '课表导入' },
  { key: 'rule', label: '规则配置' }
]
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>排班管理</h3>
      <div class="dossier-meta mono">
        <span>后九 · 8001–8039</span>
      </div>
    </div>

    <div class="vtabs">
      <button v-for="t in TABS" :key="t.key" class="vtab" :class="{ active: tab === t.key }" @click="tab = t.key">{{ t.label }}</button>
    </div>

    <ScheduleGridPanel v-if="tab === 'grid'" />
    <FreeSlotPanel v-else-if="tab === 'free'" />
    <TimetablePanel v-else-if="tab === 'timetable'" />
    <ScheduleRulePanel v-else />
  </div>
</template>

<style scoped>
.vtabs { display: flex; align-items: center; gap: 8px; margin-bottom: 14px; }
.vtab {
  padding: 7px 18px;
  border: 1px solid var(--vio-rule);
  background: var(--vio-sheet);
  color: var(--vio-ink-2);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s var(--vio-ease);
}
.vtab.active { border-color: var(--vio-ink); color: var(--vio-ink); font-weight: 700; box-shadow: inset 0 -2px 0 var(--vio-ink); }
</style>
