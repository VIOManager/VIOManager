<script setup lang="ts">
/**
 * 项目管理页（/project，管理端，PR01–PR31）
 * 两 tab：项目列表（筛选+新建+详情抽屉）/ 比赛节点（DRAFT→提交审核→通过后自动同步任务）
 * 可见性八条由后端判定（清单 §2）；管理权显隐以 viewerCanManage 驱动（所有人 or 管理员，2026-10-16 拍板）
 * 新建弹窗抽为 ProjectCreateDialog（与学生端 /my/project 共用，管理员代建与关联比赛在弹窗内）
 * 节点时间出参转契约格式（MilestonePanel）
 */
import { onMounted, computed, ref } from 'vue'
import { apiProjects } from '@/api/project'
import type { ProjectItem } from '@/api/project'
import { PROJECT_STATUS_LABEL, PROJECT_VISIBILITY_LABEL } from '@/config/labels'
import ScrollLoader from '@/components/ScrollLoader.vue'
import ProjectDetailDrawer from './ProjectDetailDrawer.vue'
import ProjectCreateDialog from './ProjectCreateDialog.vue'
import MilestonePanel from './MilestonePanel.vue'

type TabKey = 'list' | 'milestone'
const tab = ref<TabKey>('list')

const list = ref<ProjectItem[]>([])
/** 本地滚动分页：PR 列表返回全量数组，前端切片显示（每批 20 条） */
const loadedCount = ref(20)
const visible = computed(() => list.value.slice(0, loadedCount.value))
const noMore = computed(() => loadedCount.value >= list.value.length)
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref('')

const drawerVisible = ref(false)
const detailId = ref('')

async function load() {
  loading.value = true
  try {
    list.value = await apiProjects({ keyword: keyword.value || undefined, status: statusFilter.value || undefined })
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

// ── 新建项目：弹窗抽为 ProjectCreateDialog（与学生端共用；提交成功后回调刷新）──
const formVisible = ref(false)

function stampClass(status: string): string {
  if (status === 'ACTIVE') return 'warn'
  if (status === 'FINISHED') return 'ok'
  return 'dim'
}

onMounted(load)
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>项目管理</h3>
      <div class="dossier-meta mono">
        <span>后九 · 7xxx</span>
      </div>
    </div>

    <div class="vtabs">
      <button class="vtab" :class="{ active: tab === 'list' }" @click="tab = 'list'">项目列表</button>
      <button class="vtab" :class="{ active: tab === 'milestone' }" @click="tab = 'milestone'">比赛节点</button>
    </div>

    <template v-if="tab === 'list'">
      <div class="fbar">
        <input v-model="keyword" class="input-line" style="width: 180px" placeholder="项目名称" @keyup.enter="load" />
        <select v-model="statusFilter" class="input-line" @change="load">
          <option value="">全部状态</option>
          <option v-for="(label, v) in PROJECT_STATUS_LABEL" :key="v" :value="v">{{ label }}</option>
        </select>
        <button class="btn-line" @click="load">查询</button>
        <span style="flex: 1"></span>
        <button class="btn-ink" @click="formVisible = true">新建项目</button>
      </div>

      <div class="panel">
        <table class="ledger">
          <thead>
            <tr><th>项目名称</th><th>所有人</th><th>所属组织</th><th>关联比赛</th><th>可见性</th><th>状态</th><th>起止</th><th style="width: 60px">操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="p in visible" :key="p.id" class="linkrow" @click="openDetail(p.id)">
              <td style="color: var(--vio-ink); font-weight: 600">{{ p.name }}</td>
              <td>{{ p.ownerName }}</td>
              <td>{{ p.orgName }}</td>
              <td>{{ p.contestName || '—' }}</td>
              <td>{{ PROJECT_VISIBILITY_LABEL[p.visibility] }}</td>
              <td><span class="stamp" :class="stampClass(p.status)">{{ PROJECT_STATUS_LABEL[p.status] }}</span></td>
              <td class="mono" style="font-size: 12px">{{ p.startDate }} ~ {{ p.endDate }}</td>
              <td><button class="btn-act" @click.stop="openDetail(p.id)">详情</button></td>
            </tr>
          </tbody>
        </table>
        <p v-if="!loading && list.length === 0" class="empty-note">暂无可见项目（可见性八条见项目清单 §2）</p>
        <ScrollLoader :loading="loading" :no-more="noMore" :loaded="visible.length" :total="list.length" @reach="loadMore" />
      </div>
    </template>
    <MilestonePanel v-else />

    <ProjectCreateDialog v-model="formVisible" @submitted="load" />
    <ProjectDetailDrawer v-model="drawerVisible" :project-id="detailId" @changed="load" />
  </div>
</template>

<style scoped>
.vtabs { display: flex; align-items: center; gap: 8px; margin-bottom: 14px; }
.vtab {
  padding: 5px 16px;
  border: 2px solid var(--vio2-bd);
  background: var(--vio2-panel);
  box-shadow: 3px 3px 0 var(--vio2-shadow);
  color: var(--vio2-ink-2);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.15s var(--vio-ease), background 0.15s var(--vio-ease), color 0.15s var(--vio-ease);
}
.vtab:hover { transform: translate(-1px, -1px); color: var(--vio2-ink); }
.vtab.active { background: var(--vio2-yellow); color: #101010; }
</style>
