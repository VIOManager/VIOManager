<script setup lang="ts">
/**
 * 绩效规则配置（绩效管理端面板三，PF11 + PF12）
 * 子组织维度表：维度键/名称/权重/满分/启用，批量保存（upsert，semesterId 空=子组织默认规则）
 * ⚠️ 历史周报得分是快照（weight/maxScore 随打分时点固化），改规则不回溯历史分
 */
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiPerfRules, apiSavePerfRules } from '@/api/performance'
import type { PerfRule } from '@/api/performance'
import { apiOrgTreeSafe } from '@/api/org'
import type { OrgNode } from '@/api/org'

const subOrgId = ref('')
const orgs = ref<Array<{ id: string; name: string }>>([])
const items = ref<PerfRule[]>([])
const loading = ref(false)
const saving = ref(false)

async function load() {
  if (!subOrgId.value) {
    items.value = []
    return
  }
  loading.value = true
  try {
    items.value = await apiPerfRules({ subOrgId: subOrgId.value })
  } finally {
    loading.value = false
  }
}

function addRow() {
  items.value.push({ dimensionKey: '', dimensionName: '', weight: 0.2, maxScore: 100, enabled: true, sortNo: items.value.length + 1 })
}

function removeRow(i: number) {
  items.value.splice(i, 1)
}

/** 权重合计提示（不强制=1，管理员自行把握；仅提示） */
function weightSum(): number {
  let sum = 0
  for (const it of items.value) {
    if (it.enabled) sum += Number(it.weight) || 0
  }
  return Math.round(sum * 100) / 100
}

async function save() {
  if (!subOrgId.value || saving.value) return
  for (const it of items.value) {
    if (!it.dimensionKey.trim() || !it.dimensionName.trim()) {
      ElMessage.warning('维度键与名称不能为空')
      return
    }
    if (it.weight <= 0 || it.maxScore <= 0) {
      ElMessage.warning('权重与满分必须大于 0')
      return
    }
  }
  saving.value = true
  try {
    await apiSavePerfRules({ subOrgId: subOrgId.value, items: items.value })
    ElMessage.success('规则已保存（历史周报分数不受影响）')
    await load()
  } finally {
    saving.value = false
  }
}

function flatten(nodes: OrgNode[], out: Array<{ id: string; name: string }>) {
  for (const n of nodes) {
    if (n.orgType !== 'ROOT') out.push({ id: n.id, name: n.name })
    flatten(n.children, out)
  }
}

onMounted(async () => {
  const tree = await apiOrgTreeSafe()
  const out: Array<{ id: string; name: string }> = []
  flatten(tree, out)
  orgs.value = out
  if (out.length > 0) {
    subOrgId.value = out[0].id
    await load()
  }
})
</script>

<template>
  <div>
    <div class="fbar">
      <select v-model="subOrgId" class="input-line" @change="load">
        <option value="">选择子组织</option>
        <option v-for="o in orgs" :key="o.id" :value="o.id">{{ o.name }}</option>
      </select>
      <span class="fs" style="font-size: 12px; color: var(--vio-ink-3)">当前为子组织默认规则；启用维度权重合计 <b class="mono">{{ weightSum() }}</b>（建议等于 1）</span>
      <span style="flex: 1"></span>
      <button class="btn-line" :disabled="!subOrgId" @click="addRow">加维度</button>
      <button class="btn-ink" :disabled="!subOrgId || saving" @click="save">保存</button>
    </div>

    <div class="panel">
      <table class="ledger">
        <thead>
          <tr><th>维度键（大写蛇形）</th><th>维度名称</th><th style="width: 90px">权重</th><th style="width: 90px">满分</th><th style="width: 64px">启用</th><th style="width: 60px">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="(it, i) in items" :key="i">
            <td><input v-model="it.dimensionKey" class="input-line" style="width: 160px" placeholder="如 TECH_OUTPUT" /></td>
            <td><input v-model="it.dimensionName" class="input-line" style="width: 140px" placeholder="如 技术产出" /></td>
            <td><input v-model.number="it.weight" type="number" step="0.1" min="0" max="1" class="input-line" style="width: 76px" /></td>
            <td><input v-model.number="it.maxScore" type="number" min="1" class="input-line" style="width: 76px" /></td>
            <td><input v-model="it.enabled" type="checkbox" /></td>
            <td><button class="btn-act" @click="removeRow(i)">删除</button></td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && items.length === 0" class="empty-note">{{ subOrgId ? '该子组织未配置规则（打分将回 8046），请添加维度' : '请先选择子组织' }}</p>
    </div>
  </div>
</template>
