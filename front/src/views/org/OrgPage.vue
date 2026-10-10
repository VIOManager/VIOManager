<script setup lang="ts">
/**
 * 组织管理页（/org，A 线 O01–O06 + 技能字典审核 D01–D03）
 * 对应方案：《前端ABC模块页面开发-优化方案》PA1
 * 结构：左组织树 + 右节点详情与操作；第二 tab 为技能字典审核
 * ⚠️ 安全：页面按钮只做展示层操作入口，数据范围与删除保护均由后端判定（3006/3007）
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiDeleteOrg, apiOrgTree } from '@/api/org'
import type { OrgNode } from '@/api/org'
import OrgTree from './OrgTree.vue'
import OrgFormDialog from './OrgFormDialog.vue'
import DictAuditPanel from './DictAuditPanel.vue'

const tab = ref<'org' | 'dict'>('org')
const tree = ref<OrgNode[]>([])
const loading = ref(false)
const selected = ref<OrgNode | null>(null)

const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')

async function loadTree() {
  loading.value = true
  try {
    tree.value = await apiOrgTree()
    if (!selected.value && tree.value.length > 0) selected.value = tree.value[0]
  } finally {
    loading.value = false
  }
}

/** 编辑弹窗的"换上级"候选：扁平化全树、排除自身（子孙由后端 3006 拦截） */
const orgOptions = computed(() => {
  const list: { id: string; name: string; level: number }[] = []
  const walk = (nodes: OrgNode[]) => {
    for (const n of nodes) {
      if (!selected.value || n.id !== selected.value.id) list.push({ id: n.id, name: n.name, level: n.level })
      walk(n.children)
    }
  }
  walk(tree.value)
  return list
})

function openCreate() {
  formMode.value = 'create'
  formVisible.value = true
}

function openEdit() {
  formMode.value = 'edit'
  formVisible.value = true
}

async function onDelete() {
  if (!selected.value) return
  const name = selected.value.name
  try {
    await ElMessageBox.confirm('确认删除组织「' + name + '」？该组织下人员的组织归属将被清空。', '删除组织', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return // 取消
  }
  try {
    await apiDeleteOrg(selected.value.id)
    ElMessage.success('组织已删除')
    selected.value = null
    await loadTree()
  } catch {
    // 3007 有子组织拒删等提示已由 http 拦截器弹出
  }
}

onMounted(loadTree)
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>组织管理</h3>
      <div class="dossier-meta mono">
        <span>A 线 · 基础数据</span>
        <span>O01–O06 / D01–D03</span>
      </div>
    </div>

    <!-- 手工页签：图纸档案语言，不用组件库默认页签皮 -->
    <div class="tabs">
      <button class="tab" :class="{ on: tab === 'org' }" @click="tab = 'org'">组织结构</button>
      <button class="tab" :class="{ on: tab === 'dict' }" @click="tab = 'dict'">技能字典审核</button>
    </div>

    <div v-if="tab === 'org'" class="org-grid">
      <div class="panel">
        <h5 class="panel-h">组织树<span class="sub">点击节点查看详情</span></h5>
        <p v-if="loading" class="empty-note">加载中…</p>
        <template v-else>
          <OrgTree v-for="n in tree" :key="n.id" :node="n" :selected-id="selected?.id ?? ''" @select="selected = $event" />
        </template>
      </div>

      <div class="panel">
        <h5 class="panel-h">节点详情<span class="sub">{{ selected ? selected.id : '未选中' }}</span></h5>
        <template v-if="selected">
          <dl class="kv">
            <div><dt>名称</dt><dd>{{ selected.name }}</dd></div>
            <div><dt>负责人</dt><dd>{{ selected.leaderName || '—' }}</dd></div>
            <div><dt>层级</dt><dd class="mono">{{ selected.level }} 级</dd></div>
            <div><dt>排序号</dt><dd class="mono">{{ selected.sortNo }}</dd></div>
            <div><dt>子组织数</dt><dd class="mono">{{ selected.children.length }}</dd></div>
            <div><dt>状态</dt><dd><span class="stamp ok">{{ selected.status === 'ENABLED' ? '启用' : '停用' }}</span></dd></div>
          </dl>
          <div class="ops">
            <button class="btn-ink" @click="openCreate">新建子组织</button>
            <button class="btn-line" @click="openEdit">编辑</button>
            <button class="btn-line danger" @click="onDelete">删除</button>
          </div>
          <p class="fs hint">提示：删除有子组织的节点会被拒绝（3007）；编辑时把上级改成自己的子孙会被拒绝（3006）。</p>
        </template>
        <p v-else class="empty-note">在左侧选择一个组织节点</p>
      </div>
    </div>

    <div v-else class="panel">
      <h5 class="panel-h">技能字典（SKILL）<span class="sub">预置 + 用户补充，审核通过进入全站下拉</span></h5>
      <DictAuditPanel />
    </div>

    <OrgFormDialog
      v-model="formVisible"
      :mode="formMode"
      :parent="formMode === 'create' ? selected : null"
      :node="formMode === 'edit' ? selected : null"
      :org-options="orgOptions"
      @saved="loadTree"
    />
  </div>
</template>

<style scoped>
.tabs { display: flex; gap: 22px; border-bottom: 1px solid var(--vio-rule); margin-bottom: 18px; }
.tab {
  background: none;
  border: none;
  padding: 6px 2px 10px;
  font-size: 14px;
  color: var(--vio-ink-3);
  cursor: pointer;
  letter-spacing: 0.08em;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}
.tab.on { color: var(--vio-ink); font-weight: 600; border-bottom-color: var(--vio-blue-ink); }
.org-grid { display: grid; grid-template-columns: 1fr 1.2fr; gap: 18px; }
.kv { display: grid; grid-template-columns: 1fr 1fr; gap: 10px 26px; margin: 12px 0 18px; }
.kv dt { font-family: var(--vio-fangsong); font-size: 12px; color: var(--vio-ink-3); letter-spacing: 0.1em; }
.kv dd { font-size: 13.5px; color: var(--vio-ink); margin-top: 2px; }
.ops { display: flex; gap: 10px; }
.btn-line.danger { color: var(--vio-seal); border-color: var(--vio-seal); }
.btn-line.danger:hover { background: var(--vio-seal); color: #fff; }
.hint { margin-top: 14px; font-size: 12px; color: var(--vio-ink-3); }
</style>
