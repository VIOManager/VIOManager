<script setup lang="ts">
/**
 * 排班规则配置面板（SC10/SC11）
 * 7×5 时段开关表：勾选=启用该时段排班，配人数下限/上限；批量保存（upsert 子组织默认规则）
 * 未启用时段排班会被 8007 拦截
 */
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiSaveScheduleRules, apiScheduleRules } from '@/api/schedule'
import type { ScheduleRule } from '@/api/schedule'
import { apiOrgTree } from '@/api/org'
import type { OrgNode } from '@/api/org'
import { SLOT_LABEL, WEEKDAY_LABEL } from '@/config/labels'

const subOrgId = ref('')
const orgs = ref<Array<{ id: string; name: string }>>([])
const items = ref<ScheduleRule[]>([])
const saving = ref(false)

function ruleAt(weekday: number, slotIndex: number): ScheduleRule {
  for (const r of items.value) {
    if (r.weekday === weekday && r.slotIndex === slotIndex) return r
  }
  return { weekday, slotIndex, enabled: false, minPerson: 0, maxPerson: 0 }
}

async function load() {
  if (!subOrgId.value) return
  items.value = await apiScheduleRules({ subOrgId: subOrgId.value })
}

async function save() {
  if (!subOrgId.value || saving.value) return
  saving.value = true
  try {
    await apiSaveScheduleRules({ subOrgId: subOrgId.value, items: items.value })
    ElMessage.success('排班规则已保存')
    await load()
  } finally {
    saving.value = false
  }
}

/** 组织树拍平成下拉选项：排班按子组织隔离，只收 SUB_ORG 层（根/学院层选了后端必拒） */
function flatten(nodes: OrgNode[], out: Array<{ id: string; name: string }>) {
  for (const n of nodes) {
    if (n.orgType === 'SUB_ORG') out.push({ id: n.id, name: n.name })
    flatten(n.children, out)
  }
}

onMounted(async () => {
  const tree = await apiOrgTree()
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
        <option v-for="o in orgs" :key="o.id" :value="o.id">{{ o.name }}</option>
      </select>
      <span class="fs" style="font-size: 12px; color: var(--vio-ink-3)">当前为子组织默认规则（所有学期生效）；未勾选时段排班会被拦截</span>
      <span style="flex: 1"></span>
      <button class="btn-ink" :disabled="saving" @click="save">保存规则</button>
    </div>

    <div class="panel" style="overflow-x: auto">
      <table class="grid">
        <thead>
          <tr>
            <th class="mono">节次</th>
            <th v-for="w in 7" :key="w">{{ WEEKDAY_LABEL[w] }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="slot in 5" :key="slot">
            <td class="mono slot-head">{{ SLOT_LABEL[slot] }}</td>
            <td v-for="w in 7" :key="w" class="cell" :class="{ off: !ruleAt(w, slot).enabled }">
              <label class="switch-row">
                <input v-model="ruleAt(w, slot).enabled" type="checkbox" />
                <span>{{ ruleAt(w, slot).enabled ? '启用' : '停用' }}</span>
              </label>
              <template v-if="ruleAt(w, slot).enabled">
                <div class="num-row">
                  <span>下限</span>
                  <input v-model.number="ruleAt(w, slot).minPerson" type="number" min="0" class="input-line num" />
                </div>
                <div class="num-row">
                  <span>上限</span>
                  <input v-model.number="ruleAt(w, slot).maxPerson" type="number" min="1" class="input-line num" />
                </div>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.grid { width: 100%; border-collapse: collapse; font-size: 12.5px; }
.grid th { font-family: var(--vio-fangsong); font-weight: 400; font-size: 11.5px; color: var(--vio-ink-3); letter-spacing: 0.1em; padding: 8px 6px; border-bottom: 1px solid var(--vio-rule); }
.grid td { border: 1px solid var(--vio-rule-soft); padding: 8px; min-width: 110px; vertical-align: top; }
.slot-head { color: var(--vio-ink-3); white-space: nowrap; }
.cell.off { background: var(--vio-wash); }
.switch-row { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--vio-ink-2); cursor: pointer; }
.num-row { display: flex; align-items: center; gap: 6px; margin-top: 6px; font-size: 11.5px; color: var(--vio-ink-3); }
.num-row .num { width: 56px; text-align: right; }
</style>
