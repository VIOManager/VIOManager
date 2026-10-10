<script setup lang="ts">
/**
 * 赛队管理页（/team，管理端，TE01）
 * 列表含材料状态与齐全率；行点击进详情抽屉（成员生命周期操作在抽屉内）
 * 学生端为 /my/team（MyTeamPage），本页仅管理端
 */
import { onMounted, ref } from 'vue'
import { apiTeams } from '@/api/team'
import type { TeamListItem } from '@/api/team'
import { apiContests } from '@/api/contest'
import type { ContestListItem } from '@/api/contest'
import TeamDetailDrawer from './TeamDetailDrawer.vue'

const list = ref<TeamListItem[]>([])
const contests = ref<ContestListItem[]>([])
const loading = ref(false)
const contestFilter = ref('')
const keyword = ref('')

const drawerVisible = ref(false)
const detailId = ref('')

async function load() {
  loading.value = true
  try {
    list.value = await apiTeams({ contestId: contestFilter.value || undefined, keyword: keyword.value || undefined })
  } finally {
    loading.value = false
  }
}

function openDetail(id: string) {
  detailId.value = id
  drawerVisible.value = true
}

onMounted(async () => {
  void load()
  contests.value = await apiContests({})
})
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>赛队管理</h3>
      <div class="dossier-meta mono">
        <span>{{ loading ? '加载中…' : '共 ' + list.length + ' 支' }}</span>
        <span>B 线 · TE01–TE13</span>
      </div>
    </div>

    <div class="fbar">
      <input v-model="keyword" class="input-line" style="width: 200px" placeholder="队名 / 项目名" @keyup.enter="load" />
      <select v-model="contestFilter" class="input-line" @change="load">
        <option value="">全部比赛</option>
        <option v-for="c in contests" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
      <button class="btn-line" @click="load">查询</button>
    </div>

    <div class="panel">
      <table class="ledger">
        <thead>
          <tr><th>队名</th><th>项目名</th><th>比赛 / 赛道</th><th>队长</th><th class="num">成员</th><th class="num">指导教师</th><th>材料状态</th><th class="num">齐全率</th><th style="width: 60px">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="t in list" :key="t.id" class="linkrow" @click="openDetail(t.id)">
            <td style="color: var(--vio-ink); font-weight: 600">{{ t.name }}</td>
            <td>{{ t.projectName }}</td>
            <td class="fs" style="font-size: 12px">{{ t.contestName }}<template v-if="t.trackName"> · {{ t.trackName }}</template></td>
            <td>{{ t.leaderName }}</td>
            <td class="num mono">{{ t.memberCount }}</td>
            <td class="num mono">{{ t.advisorCount }}</td>
            <td><span class="stamp" :class="t.materialCompleteRate >= 100 ? 'ok' : 'warn'">{{ t.materialStatus }}</span></td>
            <td class="num mono">{{ t.materialCompleteRate }}%</td>
            <td><button class="btn-act" @click.stop="openDetail(t.id)">详情</button></td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && list.length === 0" class="empty-note">暂无赛队</p>
    </div>

    <TeamDetailDrawer v-model="drawerVisible" :team-id="detailId" @changed="load" />
  </div>
</template>
