<script setup lang="ts">
/**
 * 比赛管理页（/competition，B 线）
 * 对应方案：《前端ABC模块页面开发-优化方案》PB1+PB2
 * 三个页签：比赛列表（C01）/ 模板管理（T01–T05）/ 待处理池（P01–P03）
 * ⚠️ 安全：学生仅见已发布及之后状态（后端过滤）；状态流转与发布检查由后端判定（4001/4011）
 */
import { onMounted, ref } from 'vue'
import { apiContests } from '@/api/contest'
import type { ContestListItem, PoolItem } from '@/api/contest'
import { apiSemesters } from '@/api/semester'
import type { Semester } from '@/api/semester'
import { CONTEST_STATUS_LABEL } from '@/config/labels'
import ContestFormDialog from './ContestFormDialog.vue'
import ContestDetailDrawer from './ContestDetailDrawer.vue'
import TemplatePanel from './TemplatePanel.vue'
import PoolPanel from './PoolPanel.vue'

const tab = ref<'contest' | 'template' | 'pool'>('contest')

const contests = ref<ContestListItem[]>([])
const semesters = ref<Semester[]>([])
const loading = ref(false)
const filter = ref({ semesterId: '', status: '' })

const formVisible = ref(false)
const editingId = ref('')
const drawerVisible = ref(false)
const detailId = ref('')
/** 待处理池确认预填（PB2）：带初始值打开创建弹窗 */
const prefill = ref<{ name: string; description: string; signupStart: string } | null>(null)

async function load() {
  loading.value = true
  try {
    contests.value = await apiContests({
      semesterId: filter.value.semesterId || undefined,
      status: filter.value.status || undefined
    })
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = ''
  prefill.value = null
  formVisible.value = true
}

function openDetail(id: string) {
  detailId.value = id
  drawerVisible.value = true
}

/** 池条目确认 → 预填创建比赛（保存后由弹窗内部走 P02 关联） */
function onPoolConfirm(item: PoolItem) {
  editingId.value = ''
  prefill.value = {
    name: item.title.replace(/^关于(举办)?/, '').replace(/的通知$/, '').trim() || item.title,
    description: item.content,
    signupStart: ''
  }
  formVisible.value = true
  poolConfirmId.value = item.id
}

const poolConfirmId = ref('')
/** 待处理池面板引用：池确认创建成功后回刷池列表 */
const poolPanelRef = ref<{ load: () => void } | null>(null)

function onSaved() {
  void load()
  poolPanelRef.value?.load()
}

function statusStamp(s: string): string {
  if (s === 'REGISTERING') return 'ok'
  if (s === 'RUNNING') return 'blue'
  if (s === 'DRAFT') return 'dim'
  if (s === 'FINISHED') return 'warn'
  return 'dim'
}

onMounted(async () => {
  void load()
  try {
    semesters.value = await apiSemesters()
  } catch {
    // 联调兜底：A 线学期接口后端未实现（404），用后端库已知学期占位，接口上线自动替换
    semesters.value = [{ id: '301', name: '2026-2027学年第一学期', startDate: '', endDate: '', weekCount: 20, status: 'ACTIVE' }]
  }
})
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>比赛管理</h3>
      <div class="dossier-meta mono">
        <span>B 线 · 赛事主线</span>
        <span>T01–T05 / C01–C09 / P01–P03</span>
      </div>
    </div>

    <div class="tabs">
      <button class="tab" :class="{ on: tab === 'contest' }" @click="tab = 'contest'">比赛列表</button>
      <button class="tab" :class="{ on: tab === 'template' }" @click="tab = 'template'">模板管理</button>
      <button class="tab" :class="{ on: tab === 'pool' }" @click="tab = 'pool'">待处理池</button>
    </div>

    <template v-if="tab === 'contest'">
      <div class="fbar">
        <select v-model="filter.semesterId" class="input-line" @change="load">
          <option value="">全部学期</option>
          <option v-for="s in semesters" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
        <select v-model="filter.status" class="input-line" @change="load">
          <option value="">全部状态</option>
          <option v-for="(label, v) in CONTEST_STATUS_LABEL" :key="v" :value="v">{{ label }}</option>
        </select>
        <span style="flex: 1"></span>
        <button class="btn-ink" @click="openCreate">创建比赛</button>
      </div>

      <div class="panel">
        <table class="ledger">
          <thead>
            <tr><th>名称</th><th>学期</th><th>状态</th><th>报名起止</th><th>比赛起止</th><th class="num">赛道</th><th class="num">赛队</th><th style="width: 60px">操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="c in contests" :key="c.id" class="linkrow" @click="openDetail(c.id)">
              <td style="color: var(--vio-ink); font-weight: 600">{{ c.name }}</td>
              <td class="nw">{{ c.semesterName }}</td>
              <td><span class="stamp" :class="statusStamp(c.status)">{{ CONTEST_STATUS_LABEL[c.status] }}</span></td>
              <td class="mono nw">{{ c.signupStart || '—' }} ~ {{ c.signupEnd || '—' }}</td>
              <td class="mono nw">{{ c.contestStart || '—' }} ~ {{ c.contestEnd || '—' }}</td>
              <td class="num mono">{{ c.trackCount }}</td>
              <td class="num mono">{{ c.teamCount }}</td>
              <td><button class="btn-act" @click.stop="openDetail(c.id)">详情</button></td>
            </tr>
          </tbody>
        </table>
        <p v-if="!loading && contests.length === 0" class="empty-note">暂无比赛</p>
      </div>
    </template>

    <TemplatePanel v-else-if="tab === 'template'" />
    <PoolPanel v-else ref="poolPanelRef" @confirm="onPoolConfirm" />

    <ContestFormDialog v-model="formVisible" :contest-id="editingId" :prefill="prefill" :pool-id="poolConfirmId" @saved="onSaved" />
    <ContestDetailDrawer v-model="drawerVisible" :contest-id="detailId" @changed="load" @edit="(id) => { editingId = id; prefill = null; poolConfirmId = ''; formVisible = true }" />
  </div>
</template>

<style scoped>
.tabs { display: flex; gap: 22px; border-bottom: 1px solid var(--vio-rule); margin-bottom: 18px; }
.tab {
  background: none; border: none; padding: 6px 2px 10px; font-size: 14px; color: var(--vio-ink-3);
  cursor: pointer; letter-spacing: 0.08em; border-bottom: 2px solid transparent; margin-bottom: -1px;
}
.tab.on { color: var(--vio-ink); font-weight: 600; border-bottom-color: var(--vio-blue-ink); }
</style>
