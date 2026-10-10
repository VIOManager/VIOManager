<script setup lang="ts">
/**
 * 组织树节点（按文件名递归自渲染）：缩进 + 选中高亮，默认全展开
 * 演示数据层级浅，不做折叠；层级深了再加展开/收起状态
 */
import type { OrgNode } from '@/api/org'

defineProps<{ node: OrgNode; selectedId: string }>()
const emit = defineEmits<{ select: [node: OrgNode] }>()
</script>

<template>
  <div class="tnode">
    <div
      class="trow"
      :class="{ on: node.id === selectedId }"
      :style="{ paddingLeft: (node.level - 1) * 20 + 'px' }"
      @click="emit('select', node)"
    >
      <i class="fold">{{ node.children.length ? '▾' : '·' }}</i>
      <span class="tname">{{ node.name }}</span>
      <span class="tleader fs">{{ node.leaderName }}</span>
    </div>
    <OrgTree v-for="c in node.children" :key="c.id" :node="c" :selected-id="selectedId" @select="emit('select', $event)" />
  </div>
</template>

<style scoped>
.trow {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  cursor: pointer;
  font-size: 13px;
  color: var(--vio-ink-2);
  border-left: 2px solid transparent;
}
.trow:hover { background: var(--vio-wash); }
.trow.on { background: var(--vio-wash); border-left-color: var(--vio-blue-ink); color: var(--vio-ink); font-weight: 600; }
.fold { font-style: normal; width: 14px; color: var(--vio-ink-3); font-size: 11px; }
.tname { flex: 1; }
.tleader { font-size: 11.5px; color: var(--vio-ink-3); }
</style>
