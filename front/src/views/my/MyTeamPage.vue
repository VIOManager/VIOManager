<script setup lang="ts">
/**
 * 我的赛队（/my/team，学生端）
 * 三块：组队申请入口（TE03）/ 我的队伍列表 / 待我确认的入队邀请（TE06 确认/拒绝）
 * ⚠️ 学生数据范围由后端过滤（仅自己所在队伍）；非本队访问详情 4008
 */
import { onMounted, ref } from 'vue'
import { apiTeams } from '@/api/team'
import type { TeamListItem } from '@/api/team'
import TeamDetailDrawer from '@/views/team/TeamDetailDrawer.vue'
import TeamApplyDialog from '@/views/team/TeamApplyDialog.vue'

const list = ref<TeamListItem[]>([])
const loading = ref(false)
const applyVisible = ref(false)
const drawerVisible = ref(false)
const detailId = ref('')

async function load() {
  loading.value = true
  try {
    list.value = await apiTeams({})
  } finally {
    loading.value = false
  }
}

function openDetail(id: string) {
  detailId.value = id
  drawerVisible.value = true
}

onMounted(load)
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>我的赛队</h3>
      <div class="dossier-meta mono">
        <span>学生视角 · 仅本人所在队伍</span>
      </div>
    </div>

    <div class="fbar">
      <button class="btn-ink" @click="applyVisible = true">发起组队申请</button>
      <span class="fs" style="font-size: 12px; color: var(--vio-ink-3)">申请提交后进审批中心，审批通过才建队；入队邀请在队伍详情里确认</span>
    </div>

    <div class="panel">
      <table class="ledger">
        <thead>
          <tr><th>队名</th><th>项目名</th><th>比赛 / 赛道</th><th>队长</th><th>材料状态</th><th class="num">齐全率</th><th style="width: 60px">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="t in list" :key="t.id" class="linkrow" @click="openDetail(t.id)">
            <td style="color: var(--vio-ink); font-weight: 600">{{ t.name }}</td>
            <td>{{ t.projectName }}</td>
            <td class="fs" style="font-size: 12px">{{ t.contestName }}<template v-if="t.trackName"> · {{ t.trackName }}</template></td>
            <td>{{ t.leaderName }}</td>
            <td><span class="stamp" :class="t.materialCompleteRate >= 100 ? 'ok' : 'warn'">{{ t.materialStatus }}</span></td>
            <td class="num mono">{{ t.materialCompleteRate }}%</td>
            <td><button class="btn-act" @click.stop="openDetail(t.id)">详情</button></td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && list.length === 0" class="empty-note">暂未加入任何赛队，点左上发起组队申请</p>
    </div>

    <TeamApplyDialog v-model="applyVisible" @submitted="load" />
    <TeamDetailDrawer v-model="drawerVisible" :team-id="detailId" @changed="load" />
  </div>
</template>
