<script setup lang="ts">
/**
 * 项目管理页（/project，管理端，PR01–PR31）
 * 两 tab：项目列表（筛选+新建+详情抽屉）/ 比赛节点（DRAFT→提交审核→通过后自动同步任务）
 * 可见性八条由后端判定（清单 §2）；写操作仅项目所有人（7002，按钮显隐以 viewerIsOwner 驱动）
 */
import { onMounted, computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiCreateProject, apiProjects } from '@/api/project'
import type { ProjectItem } from '@/api/project'
import { PROJECT_STATUS_LABEL, PROJECT_VISIBILITY_LABEL } from '@/config/labels'
import ScrollLoader from '@/components/ScrollLoader.vue'
import ProjectDetailDrawer from './ProjectDetailDrawer.vue'
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

// ── 新建项目（所有人=当前用户；幂等键随提交生成）──
const formVisible = ref(false)
const form = ref({ name: '', description: '', visibility: 'ORG', startDate: '', endDate: '' })
const submitting = ref(false)

async function submit() {
  if (submitting.value) return
  if (!form.value.name.trim()) {
    ElMessage.warning('项目名称不能为空')
    return
  }
  submitting.value = true
  try {
    await apiCreateProject({
      name: form.value.name.trim(),
      description: form.value.description,
      visibility: form.value.visibility,
      startDate: form.value.startDate,
      endDate: form.value.endDate
    })
    ElMessage.success('项目已创建（你是所有人，可加成员/挂赛队）')
    formVisible.value = false
    form.value = { name: '', description: '', visibility: 'ORG', startDate: '', endDate: '' }
    await load()
  } finally {
    submitting.value = false
  }
}

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

    <el-dialog v-model="formVisible" title="新建项目" width="480px">
      <div class="form-col">
        <label>项目名称（同学期同组织内唯一，7004）</label>
        <input v-model="form.name" class="input-line" placeholder="如 智能循迹小车" />
        <label>项目简介</label>
        <textarea v-model="form.description" class="input-line" rows="3" placeholder="做什么、用什么技术"></textarea>
        <label>可见性</label>
        <select v-model="form.visibility" class="input-line">
          <option v-for="(label, v) in PROJECT_VISIBILITY_LABEL" :key="v" :value="v">{{ label }}</option>
        </select>
        <label>起止日期</label>
        <div style="display: flex; gap: 8px">
          <input v-model="form.startDate" type="date" class="input-line" />
          <input v-model="form.endDate" type="date" class="input-line" />
        </div>
      </div>
      <template #footer>
        <button class="btn-line" @click="formVisible = false">取消</button>
        <button class="btn-ink" :disabled="submitting" @click="submit">创建</button>
      </template>
    </el-dialog>

    <ProjectDetailDrawer v-model="drawerVisible" :project-id="detailId" @changed="load" />
  </div>
</template>

<style scoped>
.vtabs { display: flex; align-items: center; gap: 8px; margin-bottom: 14px; }
.vtab {
  padding: 7px 18px;
  border: 1px solid var(--vio-rule);
  background: var(--vio-sheet);
  color: var(--vio-ink-2);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s var(--vio-ease);
}
.vtab.active { border-color: var(--vio-ink); color: var(--vio-ink); font-weight: 700; box-shadow: inset 0 -2px 0 var(--vio-ink); }
.stamp.dim { color: var(--vio-ink-3); border-color: var(--vio-rule); }
.form-col { display: flex; flex-direction: column; gap: 6px; }
.form-col label { font-size: 12px; color: var(--vio-ink-3); margin-top: 6px; }
</style>
