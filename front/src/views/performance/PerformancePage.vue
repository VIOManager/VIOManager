<script setup lang="ts">
/**
 * 绩效统计页（/performance，管理端，PF06–PF12）
 * 三面板：周报审核（打分/驳回）/ 汇总排行（评优参考+导出）/ 规则配置（子组织维度）
 * 绩效审核不走审批中心（PRD §3.13 管理员直审）；打分维度快照随单固化
 */
import { ref } from 'vue'
import WeeklyReviewPanel from './WeeklyReviewPanel.vue'
import PerfSummaryPanel from './PerfSummaryPanel.vue'
import PerfRulePanel from './PerfRulePanel.vue'

type TabKey = 'review' | 'summary' | 'rule'
const tab = ref<TabKey>('review')

const TABS: Array<{ key: TabKey; label: string }> = [
  { key: 'review', label: '周报审核' },
  { key: 'summary', label: '汇总排行' },
  { key: 'rule', label: '规则配置' }
]
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>绩效统计</h3>
      <div class="dossier-meta mono">
        <span>后九 · PF06–PF12</span>
      </div>
    </div>

    <div class="vtabs">
      <button v-for="t in TABS" :key="t.key" class="vtab" :class="{ active: tab === t.key }" @click="tab = t.key">{{ t.label }}</button>
    </div>

    <WeeklyReviewPanel v-if="tab === 'review'" />
    <PerfSummaryPanel v-else-if="tab === 'summary'" />
    <PerfRulePanel v-else />
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
