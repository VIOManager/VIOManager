<script setup lang="ts">
/**
 * 成果管理页（/achievement，管理端，PC3）
 * 列表（状态筛选/关键词）+ 录入（直接生效）+ 详情抽屉（审批/佐证/删除）
 * + 获奖导出（5008：队伍存在未通过成果时禁止导出）
 */
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiAchievements, apiExportAwards } from '@/api/achievement'
import type { AchievementItem } from '@/api/achievement'
import { apiTeams } from '@/api/team'
import type { TeamListItem } from '@/api/team'
import { ACHIEVEMENT_STATUS_LABEL, ACHIEVEMENT_TYPE_LABEL } from '@/config/labels'
import AchievementFormDialog from './AchievementFormDialog.vue'
import AchievementDetailDrawer from './AchievementDetailDrawer.vue'

const list = ref<AchievementItem[]>([])
const teams = ref<TeamListItem[]>([])
const statusFilter = ref('')
const keyword = ref('')
const exportTeamId = ref('')

const formVisible = ref(false)
const drawerVisible = ref(false)
const detailId = ref('')

async function load() {
  list.value = await apiAchievements({ status: statusFilter.value || undefined, keyword: keyword.value || undefined })
}

function openDetail(id: string) {
  detailId.value = id
  drawerVisible.value = true
}

async function onExport() {
  if (!exportTeamId.value) {
    ElMessage.warning('请选择要导出的队伍')
    return
  }
  const r = await apiExportAwards(exportTeamId.value)
  ElMessage.success(r.note)
}

onMounted(async () => {
  await load()
  teams.value = await apiTeams()
})
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>成果管理</h3>
      <div class="dossier-meta mono">
        <span>共 {{ list.length }} 项</span>
        <span>C 线 · A01–A08</span>
      </div>
    </div>

    <div class="fbar">
      <input v-model="keyword" class="input-line" style="width: 180px" placeholder="成果名称 / 级别" @keyup.enter="load" />
      <select v-model="statusFilter" class="input-line" @change="load">
        <option value="">全部状态</option>
        <option v-for="(label, v) in ACHIEVEMENT_STATUS_LABEL" :key="v" :value="v">{{ label }}</option>
      </select>
      <button class="btn-line" @click="load">查询</button>
      <span style="flex: 1"></span>
      <select v-model="exportTeamId" class="input-line">
        <option value="">选择队伍导出获奖清单</option>
        <option v-for="t in teams" :key="t.id" :value="t.id">{{ t.name }}</option>
      </select>
      <button class="btn-line" @click="onExport">导出</button>
      <button class="btn-ink" @click="formVisible = true">录入成果</button>
    </div>

    <div class="panel">
      <table class="ledger">
        <thead>
          <tr><th>成果名称</th><th>类型</th><th>级别</th><th>队伍</th><th>提交人</th><th>获奖时间</th><th>佐证</th><th>状态</th><th style="width: 60px">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="a in list" :key="a.id" class="linkrow" @click="openDetail(a.id)">
            <td style="color: var(--vio-ink); font-weight: 600">{{ a.name }}</td>
            <td>{{ ACHIEVEMENT_TYPE_LABEL[a.type] }}</td>
            <td>{{ a.level }}</td>
            <td>{{ a.teamName }}</td>
            <td>{{ a.submittedBy }}</td>
            <td class="mono" style="font-size: 12px">{{ a.achievedAt }}</td>
            <td>{{ a.hasEvidence ? '已传' : '未传' }}</td>
            <td>
              <span class="stamp" :class="a.status === 'APPROVED' ? 'ok' : a.status === 'PENDING' ? 'warn' : 'bad'">{{ ACHIEVEMENT_STATUS_LABEL[a.status] }}</span>
            </td>
            <td><button class="btn-act" @click.stop="openDetail(a.id)">详情</button></td>
          </tr>
        </tbody>
      </table>
      <p v-if="list.length === 0" class="empty-note">暂无成果</p>
    </div>

    <AchievementFormDialog v-model="formVisible" mode="admin" :teams="teams.map((t) => ({ teamId: t.id, teamName: t.name }))" @saved="load" />
    <AchievementDetailDrawer v-model="drawerVisible" :ach-id="detailId" @changed="load" />
  </div>
</template>

<style scoped>
.stamp.bad { color: var(--vio-danger); border-color: var(--vio-danger); }
</style>
