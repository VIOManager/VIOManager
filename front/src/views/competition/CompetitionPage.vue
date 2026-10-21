<script setup lang="ts">
/**
 * 比赛管理页（/competition，B 线）
 * 对应方案：《前端ABC模块页面开发-优化方案》PB1+PB2
 * 三个页签：比赛列表（C01）/ 模板管理（T01–T05）/ 待处理池（P01–P03）
 * ⚠️ 安全：学生仅见已发布及之后状态（后端过滤）；状态流转与发布检查由后端判定（4001/4011）
 */
import { onMounted, computed, ref } from 'vue'
import { apiContests } from '@/api/contest'
import type { ContestListItem, PoolItem } from '@/api/contest'
import { apiSemestersSafe } from '@/api/semester'
import type { Semester } from '@/api/semester'
import { CONTEST_STATUS_LABEL } from '@/config/labels'
import ScrollLoader from '@/components/ScrollLoader.vue'
import ContestFormDialog from './ContestFormDialog.vue'
import ContestDetailDrawer from './ContestDetailDrawer.vue'
import TemplatePanel from './TemplatePanel.vue'
import PoolPanel from './PoolPanel.vue'

const tab = ref<'contest' | 'template' | 'pool'>('contest')

const contests = ref<ContestListItem[]>([])
/** 本地滚动分页：C01 返回全量数组，前端切片显示（每批 20 条） */
const loadedCount = ref(20)
const visible = computed(() => contests.value.slice(0, loadedCount.value))
const noMore = computed(() => loadedCount.value >= contests.value.length)
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

/** 列表起止显示：到日、同年省略右端年份（"2026-11-20 ~ 12-05"）；完整时分秒在详情抽屉。
 *  完整时间段每列 ~320px 会把名称列挤到 60px，比赛名折行导致行高参差（2026-10-19 间距修复） */
function rangeOf(start: string, end: string): string {
  if (!start && !end) return '— ~ —'
  const s = start ? start.slice(0, 10) : '—'
  const e = !end ? '—' : (start.slice(0, 4) === end.slice(0, 4) ? end.slice(5, 10) : end.slice(0, 10))
  return s + ' ~ ' + e
}

onMounted(async () => {
  void load()
  semesters.value = await apiSemestersSafe()
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
        <table class="ledger cols-fixed">
          <thead>
            <!-- 固定布局（cols-fixed）：列宽只认此处设定，内容不再撑列；名称列拿剩余宽度 -->
            <tr>
              <th>名称</th>
              <th style="width: 170px">学期</th>
              <th style="width: 80px">状态</th>
              <th style="width: 165px">报名起止</th>
              <th style="width: 165px">比赛起止</th>
              <th class="num" style="width: 44px">赛道</th>
              <th class="num" style="width: 44px">赛队</th>
              <th style="width: 60px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in visible" :key="c.id" class="linkrow" @click="openDetail(c.id)">
              <td class="name-cell" style="color: var(--vio-ink); font-weight: 600" :title="c.name">{{ c.name }}</td>
              <td class="nw">{{ c.semesterName }}</td>
              <td><span class="stamp" :class="statusStamp(c.status)">{{ CONTEST_STATUS_LABEL[c.status] }}</span></td>
              <td class="mono nw">{{ rangeOf(c.signupStart, c.signupEnd) }}</td>
              <td class="mono nw">{{ rangeOf(c.contestStart, c.contestEnd) }}</td>
              <td class="num mono">{{ c.trackCount }}</td>
              <td class="num mono">{{ c.teamCount }}</td>
              <td><button class="btn-act" @click.stop="openDetail(c.id)">详情</button></td>
            </tr>
          </tbody>
        </table>
        <p v-if="!loading && contests.length === 0" class="empty-note">暂无比赛</p>
        <ScrollLoader :loading="loading" :no-more="noMore" :loaded="visible.length" :total="contests.length" @reach="loadMore" />
      </div>
    </template>

    <TemplatePanel v-else-if="tab === 'template'" />
    <PoolPanel v-else ref="poolPanelRef" @confirm="onPoolConfirm" />

    <ContestFormDialog v-model="formVisible" :contest-id="editingId" :prefill="prefill" :pool-id="poolConfirmId" @saved="onSaved" />
    <ContestDetailDrawer v-model="drawerVisible" :contest-id="detailId" @changed="load" @edit="(id) => { editingId = id; prefill = null; poolConfirmId = ''; formVisible = true }" />
  </div>
</template>

<style scoped>
/* 名称单元格单行省略：超长比赛名截断显示（悬停 title / 详情抽屉看全名），
   保证每行单行、行距一致（2026-10-19 间距修复） */
.cols-fixed { table-layout: fixed; }
.cols-fixed td.name-cell { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tabs { display: flex; gap: 8px; margin-bottom: 18px; }
.tab {
  background: var(--vio2-panel);
  border: 2px solid var(--vio2-bd);
  box-shadow: 3px 3px 0 var(--vio2-shadow);
  padding: 5px 16px;
  font-size: 13px;
  font-weight: 700;
  color: var(--vio2-ink-2);
  cursor: pointer;
  transition: transform 0.15s var(--vio-ease), background 0.15s var(--vio-ease), color 0.15s var(--vio-ease);
}
.tab:hover { transform: translate(-1px, -1px); color: var(--vio2-ink); }
.tab.on { background: var(--vio2-yellow); color: #101010; }
</style>
