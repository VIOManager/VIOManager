<script setup lang="ts">
/**
 * 空课查询面板（SC06 整周矩阵 + SC07 单格名单）
 * 矩阵每格显示空课人数（含按年级分布 tooltip）；点格看名单（只含已确认映射班级的成员，PRD 铁规则）
 * 单双周已参与计算：单周课只占奇数周格、双周课只占偶数周格
 */
import { ref } from 'vue'
import { apiFreeSlotMatrix, apiFreeSlotMembers } from '@/api/schedule'
import type { FreeMember, FreeSlotCell } from '@/api/schedule'
import { SLOT_LABEL, WEEKDAY_LABEL } from '@/config/labels'
import { activeSemester, apiSemestersSafe } from '@/api/semester'
import type { Semester } from '@/api/semester'

const semesterId = ref('') // 当前激活学期（apiSemestersSafe 取 ACTIVE，接口失败走 FALLBACK）
const semesters = ref<Semester[]>([])
const weekNo = ref(6)
const cells = ref<FreeSlotCell[]>([])
const loading = ref(false)

const picked = ref<{ weekday: number; slotIndex: number } | null>(null)
const members = ref<FreeMember[]>([])
const listLoading = ref(false)

function cellAt(weekday: number, slotIndex: number): FreeSlotCell {
  for (const c of cells.value) {
    if (c.weekday === weekday && c.slotIndex === slotIndex) return c
  }
  return { weekday, slotIndex, freeCount: 0, gradeDist: {} }
}

async function load() {
  if (!semesterId.value) return
  loading.value = true
  picked.value = null
  members.value = []
  try {
    cells.value = await apiFreeSlotMatrix({ semesterId: semesterId.value, weekNo: weekNo.value })
  } finally {
    loading.value = false
  }
}

async function pick(weekday: number, slotIndex: number) {
  picked.value = { weekday, slotIndex }
  listLoading.value = true
  try {
    members.value = await apiFreeSlotMembers({ semesterId: semesterId.value, weekNo: weekNo.value, weekday, slotIndex })
  } finally {
    listLoading.value = false
  }
}

function gradeText(dist: Record<string, number>): string {
  const parts: string[] = []
  for (const g in dist) parts.push(g + ' 级 ' + dist[g] + ' 人')
  return parts.join(' · ')
}

;(async () => {
  semesters.value = await apiSemestersSafe()
  const active = await activeSemester()
  if (active) semesterId.value = String(active.id)
  await load()
})()
</script>

<template>
  <div>
    <div class="fbar">
      <select v-model="semesterId" class="input-line" style="width: 210px" title="按学期查询（教务导入常落历史学期）" @change="load">
        <option v-for="s in semesters" :key="s.id" :value="String(s.id)">{{ s.name }}</option>
      </select>
      <input v-model.number="weekNo" type="number" min="1" max="18" class="input-line" style="width: 90px" @change="load" />
      <span class="mono" style="font-size: 12px; color: var(--vio-ink-3)">第 {{ weekNo }} 教学周 · {{ weekNo % 2 === 1 ? '单周' : '双周' }}（单双周课已计入占用）· 点击格子看名单</span>
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
            <td
              v-for="w in 7"
              :key="w"
              class="cell"
              :class="{ zero: cellAt(w, slot).freeCount === 0, picked: picked && picked.weekday === w && picked.slotIndex === slot }"
              :title="gradeText(cellAt(w, slot).gradeDist)"
              @click="pick(w, slot)"
            >
              <b class="mono">{{ cellAt(w, slot).freeCount }}</b>
              <span>人空课</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="picked" class="panel" style="margin-top: 14px">
      <h6 class="panel-title">{{ WEEKDAY_LABEL[picked.weekday] }} {{ SLOT_LABEL[picked.slotIndex] }} · 空课名单（{{ members.length }} 人）</h6>
      <table class="ledger">
        <thead>
          <tr><th>姓名</th><th>班级</th><th>年级</th></tr>
        </thead>
        <tbody>
          <tr v-for="m in members" :key="m.memberId">
            <td style="color: var(--vio-ink); font-weight: 600">{{ m.name }}</td>
            <td>{{ m.className }}</td>
            <td class="mono">{{ m.grade }} 级</td>
          </tr>
        </tbody>
      </table>
      <p v-if="!listLoading && members.length === 0" class="empty-note">该时段无空课成员</p>
    </div>
  </div>
</template>

<style scoped>
.grid { width: 100%; border-collapse: collapse; font-size: 12.5px; }
.grid th { font-family: var(--vio-fangsong); font-weight: 400; font-size: 11.5px; color: var(--vio-ink-3); letter-spacing: 0.1em; padding: 8px 6px; border-bottom: 1px solid var(--vio-rule); }
.grid td { border: 1px solid var(--vio-rule-soft); padding: 12px 6px; text-align: center; min-width: 96px; }
.slot-head { color: var(--vio-ink-3); white-space: nowrap; }
.cell { cursor: pointer; transition: background 0.2s var(--vio-ease); }
.cell:hover { background: var(--vio-wash); }
.cell.picked { background: var(--vio-wash); box-shadow: inset 0 0 0 2px var(--vio-blue-ink); }
.cell b { font-size: 20px; color: var(--vio-blue-ink); margin-right: 4px; }
.cell.zero b { color: var(--vio-ink-3); }
.cell span { font-size: 11px; color: var(--vio-ink-3); }
.panel-title { font-size: 12px; color: var(--vio-ink-3); letter-spacing: 0.08em; margin-bottom: 8px; }
</style>
