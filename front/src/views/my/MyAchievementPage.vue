<script setup lang="ts">
/**
 * 我的成果页（/my/achievement，学生端，PC3）
 * 展示本人所在队伍的全部成果 + 队长提交入口（提交后进审批中心，通过才生效）
 * 队长标识由后端判定（my/achievements 返回 isLeader），非队长不显示提交按钮
 */
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiMyAchievements } from '@/api/achievement'
import type { AchievementItem } from '@/api/achievement'
import { ACHIEVEMENT_STATUS_LABEL, ACHIEVEMENT_TYPE_LABEL } from '@/config/labels'
import AchievementFormDialog from '../achievement/AchievementFormDialog.vue'

const teams = ref<Array<{ teamId: string; teamName: string; isLeader: boolean }>>([])
const items = ref<AchievementItem[]>([])
const formVisible = ref(false)

/** 本人任队长的队伍（提交成果的队伍范围） */
const leaderTeams = ref<Array<{ teamId: string; teamName: string }>>([])

async function load() {
  const r = await apiMyAchievements()
  teams.value = r.teams
  items.value = r.items
  leaderTeams.value = r.teams.filter((t) => t.isLeader).map((t) => ({ teamId: t.teamId, teamName: t.teamName }))
}

function onSubmit() {
  if (leaderTeams.value.length === 0) {
    ElMessage.warning('仅队长可提交成果')
    return
  }
  formVisible.value = true
}

onMounted(load)
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>我的成果</h3>
      <div class="dossier-meta mono">
        <span>{{ items.length }} 项成果</span>
        <span>{{ leaderTeams.length > 0 ? '你担任 ' + leaderTeams.length + ' 支队伍队长' : '非队长：仅查看' }}</span>
      </div>
    </div>

    <div class="fbar">
      <span class="fs" style="font-size: 12.5px; color: var(--vio-ink-3)">我的队伍：{{ teams.map((t) => t.teamName + (t.isLeader ? '（队长）' : '')).join('、') || '—' }}</span>
      <span style="flex: 1"></span>
      <button class="btn-ink" :disabled="leaderTeams.length === 0" @click="onSubmit">提交成果</button>
    </div>

    <div class="panel">
      <table class="ledger">
        <thead>
          <tr><th>成果名称</th><th>类型</th><th>级别</th><th>队伍</th><th>获奖时间</th><th>佐证</th><th>状态</th></tr>
        </thead>
        <tbody>
          <tr v-for="a in items" :key="a.id">
            <td style="color: var(--vio-ink); font-weight: 600">{{ a.name }}</td>
            <td>{{ ACHIEVEMENT_TYPE_LABEL[a.type] }}</td>
            <td>{{ a.level }}</td>
            <td>{{ a.teamName }}</td>
            <td class="mono" style="font-size: 12px">{{ a.achievedAt }}</td>
            <td>{{ a.hasEvidence ? '已传' : '未传' }}</td>
            <td>
              <span class="stamp" :class="a.status === 'APPROVED' ? 'ok' : a.status === 'PENDING' ? 'warn' : 'bad'">{{ ACHIEVEMENT_STATUS_LABEL[a.status] }}</span>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="items.length === 0" class="empty-note">暂无成果（队长提交并通过审批后在此展示）</p>
    </div>

    <AchievementFormDialog v-model="formVisible" mode="leader" :teams="leaderTeams" @saved="load" />
  </div>
</template>

<style scoped>
/* 状态戳走全局 page.css 贴纸块，本页无额外样式 */
</style>
