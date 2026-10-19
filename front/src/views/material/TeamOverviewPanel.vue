<script setup lang="ts">
/**
 * 队伍材料提交概览面板（PC2，MS03/MS06）
 * 选任务 → 各队伍 × 各要求 的提交矩阵（版本号 + 未交标记）+ 齐全率 + 打包下载
 * 打包下载 mock 只回提示文案；真实后端为文件流
 */
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { apiExportTeamPackage, apiRequirements, apiTaskOverview } from '@/api/material'
import type { MaterialRequirement, MaterialTask, TeamMaterialRow } from '@/api/material'
import { MATERIAL_TYPE_LABEL } from '@/config/labels'

const props = defineProps<{ tasks: MaterialTask[] }>()

const taskId = ref('')
const reqs = ref<MaterialRequirement[]>([])
const rows = ref<TeamMaterialRow[]>([])

async function load() {
  if (!taskId.value) {
    reqs.value = []
    rows.value = []
    return
  }
  reqs.value = await apiRequirements(taskId.value)
  const task = props.tasks.find((t) => t.id === taskId.value)
  if (!task) {
    rows.value = []
    return
  }
  try {
    rows.value = await apiTaskOverview(task)
  } catch {
    // 兜底：任一队伍概览取失败都不该让整张表崩（组合接口内部已逐队 catch，这里防队伍列表本身失败）
    rows.value = []
  }
}

watch(taskId, () => void load())

function cellOf(row: TeamMaterialRow, requirementId: string) {
  for (const it of row.items) {
    if (it.requirementId === requirementId) return it
  }
  return null
}

async function onExport(row: TeamMaterialRow) {
  await apiExportTeamPackage(row.teamId)
  ElMessage.success('打包下载已开始')
}
</script>

<template>
  <div>
    <div class="fbar">
      <span class="fs" style="font-size: 12.5px; color: var(--vio-ink-3)">选择材料任务查看各队提交情况</span>
      <select v-model="taskId" class="input-line" style="min-width: 280px">
        <option value="">请选择任务</option>
        <option v-for="t in props.tasks" :key="t.id" :value="t.id">{{ t.name }}（{{ t.contestName }}）</option>
      </select>
    </div>

    <div v-if="taskId" class="panel">
      <table class="ledger">
        <thead>
          <tr>
            <th>队伍</th><th>队长</th>
            <th v-for="r in reqs" :key="r.id" class="num">{{ MATERIAL_TYPE_LABEL[r.materialType] ?? r.materialType }}</th>
            <th class="num">齐全率</th><th>状态</th><th style="width: 90px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.teamId">
            <td style="color: var(--vio-ink); font-weight: 600">{{ row.teamName }}</td>
            <td>{{ row.leaderName }}</td>
            <td v-for="r in reqs" :key="r.id" class="num mono">
              <span v-if="cellOf(row, r.id) && cellOf(row, r.id)!.status === 'SUBMITTED'" class="mono">v{{ cellOf(row, r.id)!.version }}</span>
              <span v-else class="miss">未交</span>
            </td>
            <td class="num mono">{{ row.completeRate }}%</td>
            <td><span class="stamp" :class="row.completeRate >= 100 ? 'ok' : 'warn'">{{ row.statusText }}</span></td>
            <td><button class="btn-act" @click="onExport(row)">打包下载</button></td>
          </tr>
        </tbody>
      </table>
      <p v-if="rows.length === 0" class="empty-note">该比赛暂无在册队伍</p>
    </div>
    <p v-else class="empty-note">请先选择材料任务</p>
  </div>
</template>

<style scoped>
.miss { color: var(--vio-seal); font-size: 12px; letter-spacing: 0.05em; }
</style>
