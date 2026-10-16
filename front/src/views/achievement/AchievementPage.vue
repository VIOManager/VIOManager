<script setup lang="ts">
/**
 * 成果管理页（/achievement，管理端，PC3）
 * 列表（状态筛选/关键词/行勾选批量）+ 录入（直接生效）+ 详情抽屉（审批/佐证/删除）
 * + 获奖导出（5008：队伍存在未通过成果时禁止导出）——勾选行按所选队伍逐队导出，
 *   未勾选导出全部（后端 export 的 teamId 可选）
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiAchievements, apiExportAwards } from '@/api/achievement'
import type { AchievementItem } from '@/api/achievement'
import { apiTeams } from '@/api/team'
import type { TeamListItem } from '@/api/team'
import { ACHIEVEMENT_STATUS_LABEL, ACHIEVEMENT_TYPE_LABEL } from '@/config/labels'
import ScrollLoader from '@/components/ScrollLoader.vue'
import AchievementFormDialog from './AchievementFormDialog.vue'
import AchievementDetailDrawer from './AchievementDetailDrawer.vue'

const list = ref<AchievementItem[]>([])
/** 本地滚动分页：A01 返回全量数组，前端切片显示（每批 20 条）；全选/导出仍作用于全量 */
const loadedCount = ref(20)
const visible = computed(() => list.value.slice(0, loadedCount.value))
const noMore = computed(() => loadedCount.value >= list.value.length)
const loading = ref(false)
const teams = ref<TeamListItem[]>([])
const statusFilter = ref('')
const keyword = ref('')

const formVisible = ref(false)
const drawerVisible = ref(false)
const detailId = ref('')

/** 行勾选（批量处理基础：勾选成果行 → 导出按所在队伍去重逐队下载） */
const selectedIds = ref<string[]>([])
const allPicked = computed(() => list.value.length > 0 && selectedIds.value.length === list.value.length)

function toggle(id: string) {
  const i = selectedIds.value.indexOf(id)
  if (i >= 0) selectedIds.value.splice(i, 1)
  else selectedIds.value.push(id)
}

function toggleAll() {
  selectedIds.value = allPicked.value ? [] : list.value.map((a) => a.id)
}

/** 所选成果覆盖的队伍（去重） */
function pickedTeamIds(): string[] {
  const ids: string[] = []
  for (const a of list.value) {
    if (selectedIds.value.indexOf(a.id) >= 0 && ids.indexOf(a.teamId) < 0) ids.push(a.teamId)
  }
  return ids
}

async function load() {
  loading.value = true
  try {
    list.value = await apiAchievements({ status: statusFilter.value || undefined, keyword: keyword.value || undefined })
    selectedIds.value = []
    loadedCount.value = 20
  } finally {
    loading.value = false
  }
}

/** 滚动到底扩大切片（ScrollLoader 的 reach 回调） */
function loadMore() {
  if (loading.value || noMore.value) return
  loadedCount.value += 20
}

function openDetail(id: string) {
  detailId.value = id
  drawerVisible.value = true
}

/**
 * 导出：勾选了按所选队伍逐队下载（后端仅支持单 teamId，多队伍=多文件）；
 * 未勾选导出全部。后端在队伍存在未通过成果时 5008 拦截（弹错由拦截器统一）
 */
async function onExport() {
  const teamIds = pickedTeamIds()
  if (teamIds.length === 0) {
    await apiExportAwards('')
    ElMessage.success('导出下载已开始（全部队伍）')
    return
  }
  for (let i = 0; i < teamIds.length; i++) {
    await apiExportAwards(teamIds[i])
  }
  ElMessage.success('导出下载已开始（' + teamIds.length + ' 个队伍，每队一个文件）')
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
        <span>共 {{ list.length }} 项 · 已选 {{ selectedIds.length }} 项</span>
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
      <button class="btn-line" @click="onExport">
        {{ selectedIds.length > 0 ? '导出所选（' + selectedIds.length + ' 项）' : '导出全部' }}
      </button>
      <button class="btn-ink" @click="formVisible = true">录入成果</button>
    </div>

    <div class="panel">
      <table class="ledger">
        <thead>
          <tr>
            <th style="width: 36px">
              <input id="pk-all" type="checkbox" class="pk" :checked="allPicked" @change="toggleAll" />
              <label for="pk-all" class="pick" title="全选 / 清空"></label>
            </th>
            <th>成果名称</th><th>类型</th><th>级别</th><th>队伍</th><th>提交人</th><th>获奖时间</th><th>佐证</th><th>状态</th><th style="width: 60px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in visible" :key="a.id" class="linkrow" @click="openDetail(a.id)">
            <td class="pickcell" @click.stop>
              <input :id="'pk-' + a.id" type="checkbox" class="pk" :checked="selectedIds.indexOf(a.id) >= 0" @change="toggle(a.id)" />
              <label :for="'pk-' + a.id" class="pick"></label>
            </td>
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
      <ScrollLoader :loading="loading" :no-more="noMore" :loaded="visible.length" :total="list.length" @reach="loadMore" />
    </div>

    <AchievementFormDialog v-model="formVisible" mode="admin" :teams="teams.map((t) => ({ teamId: t.id, teamName: t.name }))" @saved="load" />
    <AchievementDetailDrawer v-model="drawerVisible" :ach-id="detailId" @changed="load" />
  </div>
</template>

<style scoped>
.stamp.bad { color: var(--vio-danger); border-color: var(--vio-danger); }

/* 行勾选圆圈（图纸档案风：图线圆圈，选中实心墨蓝带白对勾；input 隐藏 + label 绘制） */
.pk { display: none; }
.pick { display: inline-block; position: relative; width: 16px; height: 16px; border: 1.5px solid var(--vio-rule); border-radius: 50%; cursor: pointer; vertical-align: middle; box-sizing: border-box; }
.pick:hover { border-color: var(--vio-blue-ink); }
.pk:checked + .pick { border-color: var(--vio-blue-ink); background: var(--vio-blue-ink); }
.pk:checked + .pick::after { content: ''; position: absolute; left: 4px; top: 1.5px; width: 4px; height: 8px; border: solid #FFF; border-width: 0 1.5px 1.5px 0; transform: rotate(45deg); }
.pickcell { text-align: center; }
</style>
