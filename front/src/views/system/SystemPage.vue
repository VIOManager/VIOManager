<script setup lang="ts">
/**
 * 系统配置页（/system，超管独占，SY01–SY10 + CF01/CF02）
 * 两 tab：字典管理（类型/条目/词条审核）/ 系统参数
 * 部门管理员菜单本项隐藏（PRD §2.1）；学生与其他角色调接口后端 1003 兜底
 */
import { ref } from 'vue'
import DictPanel from './DictPanel.vue'
import ConfigPanel from './ConfigPanel.vue'

type TabKey = 'dict' | 'config'
const tab = ref<TabKey>('dict')
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>系统配置</h3>
      <div class="dossier-meta mono">
        <span>超管独占 · SY/CF</span>
      </div>
    </div>

    <div class="vtabs">
      <button class="vtab" :class="{ active: tab === 'dict' }" @click="tab = 'dict'">字典管理</button>
      <button class="vtab" :class="{ active: tab === 'config' }" @click="tab = 'config'">系统参数</button>
    </div>

    <DictPanel v-if="tab === 'dict'" />
    <ConfigPanel v-else />
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
