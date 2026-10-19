<script setup lang="ts">
/**
 * 材料管理页（/material，管理端，PC1）
 * Tab1 材料任务：任务台账（挂比赛）+ 选中任务的要求配置（12 类型/必传/格式/命名规则/大小）
 * Tab2 队伍概览：提交矩阵与打包下载（TeamOverviewPanel）
 * 拒删分支：要求有提交记录 5005 / 任务下有提交记录 5006（提示由 http 拦截器统一弹出）
 */
import { onMounted, computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiDeleteRequirement, apiDeleteTask, apiMaterialTasks, apiRequirements } from '@/api/material'
import type { MaterialRequirement, MaterialTask } from '@/api/material'
import { apiContests } from '@/api/contest'
import type { ContestListItem } from '@/api/contest'
import { MATERIAL_TYPE_LABEL } from '@/config/labels'
import ScrollLoader from '@/components/ScrollLoader.vue'
import TaskFormDialog from './TaskFormDialog.vue'
import RequirementFormDialog from './RequirementFormDialog.vue'
import TeamOverviewPanel from './TeamOverviewPanel.vue'

const tab = ref('tasks')
const contests = ref<ContestListItem[]>([])
const tasks = ref<MaterialTask[]>([])
/** 本地滚动分页：任务台账返回全量数组，前端切片显示（每批 20 条）；要求配置与矩阵为配置视图不分页 */
const loadedCount = ref(20)
const visible = computed(() => tasks.value.slice(0, loadedCount.value))
const noMore = computed(() => loadedCount.value >= tasks.value.length)
const loading = ref(false)
const contestFilter = ref('')

const taskDialogVisible = ref(false)
const editingTask = ref<MaterialTask | null>(null)
const reqDialogVisible = ref(false)
const editingReq = ref<MaterialRequirement | null>(null)

/** 当前展开要求配置的任务（点击"要求"展开） */
const currentTask = ref<MaterialTask | null>(null)
const currentReqs = ref<MaterialRequirement[]>([])

async function loadTasks() {
  loading.value = true
  try {
    tasks.value = await apiMaterialTasks(contestFilter.value || undefined)
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

async function loadReqs() {
  if (!currentTask.value) return
  currentReqs.value = await apiRequirements(currentTask.value.id)
}

function openTaskCreate() {
  editingTask.value = null
  taskDialogVisible.value = true
}
function openTaskEdit(t: MaterialTask) {
  editingTask.value = t
  taskDialogVisible.value = true
}
async function onTaskSaved() {
  await loadTasks()
  // 编辑的是当前展开任务时同步刷新要求区
  if (currentTask.value) {
    for (const t of tasks.value) {
      if (t.id === currentTask.value.id) currentTask.value = t
    }
  }
}

async function onDeleteTask(t: MaterialTask) {
  try {
    await ElMessageBox.confirm('删除任务「' + t.name + '」？其下未交过的要求将一并移除', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await apiDeleteTask(t.id)
  ElMessage.success('任务已删除')
  if (currentTask.value && currentTask.value.id === t.id) currentTask.value = null
  await loadTasks()
}

function openReqs(t: MaterialTask) {
  currentTask.value = t
  void loadReqs()
}
function openReqCreate() {
  editingReq.value = null
  reqDialogVisible.value = true
}
function openReqEdit(r: MaterialRequirement) {
  editingReq.value = r
  reqDialogVisible.value = true
}
async function onReqSaved() {
  await loadReqs()
  await loadTasks()
  if (currentTask.value) {
    for (const t of tasks.value) {
      if (t.id === currentTask.value.id) currentTask.value = t
    }
  }
}
async function onDeleteReq(r: MaterialRequirement) {
  try {
    await ElMessageBox.confirm('删除要求「' + (MATERIAL_TYPE_LABEL[r.materialType] ?? r.materialType) + '」？', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await apiDeleteRequirement(r.id)
  ElMessage.success('要求已删除')
  await onReqSaved()
}

onMounted(async () => {
  contests.value = await apiContests({})
  await loadTasks()
})
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>材料管理</h3>
      <div class="dossier-meta mono">
        <span>{{ loading ? '加载中…' : '共 ' + tasks.length + ' 个任务' }}</span>
        <span>C 线 · MT / MR / MS</span>
      </div>
    </div>

    <el-tabs v-model="tab" class="ink-tabs">
      <el-tab-pane label="材料任务" name="tasks">
        <div class="fbar">
          <select v-model="contestFilter" class="input-line" @change="loadTasks">
            <option value="">全部比赛</option>
            <option v-for="c in contests" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
          <button class="btn-ink" @click="openTaskCreate">新建任务</button>
        </div>

        <div class="panel">
          <table class="ledger">
            <thead>
              <tr><th>任务名</th><th>比赛</th><th class="num">要求数</th><th class="num">交齐队伍</th><th>截止时间</th><th style="width: 190px">操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="t in visible" :key="t.id" :class="{ cur: currentTask && currentTask.id === t.id }">
                <td style="color: var(--vio-ink); font-weight: 600">{{ t.name }}</td>
                <td class="fs" style="font-size: 12px">{{ t.contestName }}</td>
                <td class="num mono">{{ t.requirementCount }}</td>
                <td class="num mono">{{ t.teamComplete }} / {{ t.teamTotal }}</td>
                <td class="mono" style="font-size: 12px">{{ t.deadline }}</td>
                <td>
                  <button class="btn-act" @click="openReqs(t)">要求</button>
                  <button class="btn-line" style="margin-left: 6px" @click="openTaskEdit(t)">编辑</button>
                  <button class="btn-line" style="margin-left: 6px" @click="onDeleteTask(t)">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
          <p v-if="tasks.length === 0" class="empty-note">暂无材料任务</p>
          <ScrollLoader :loading="loading" :no-more="noMore" :loaded="visible.length" :total="tasks.length" @reach="loadMore" />
        </div>

        <!-- 要求配置区：点"要求"展开 -->
        <template v-if="currentTask">
          <div class="dossier-head" style="margin-top: 18px">
            <h5>「{{ currentTask.name }}」要求配置</h5>
            <button class="btn-ink" @click="openReqCreate">添加要求</button>
          </div>
          <div class="panel">
            <table class="ledger">
              <thead>
                <tr><th>名称</th><th>类型</th><th>必传</th><th>允许格式</th><th>命名规则</th><th class="num">大小上限</th><th style="width: 130px">操作</th></tr>
              </thead>
              <tbody>
                <tr v-for="r in currentReqs" :key="r.id">
                  <td style="color: var(--vio-ink); font-weight: 600">{{ r.name || '—' }}</td>
                  <td class="fs">{{ MATERIAL_TYPE_LABEL[r.materialType] ?? r.materialType }}</td>
                  <td><span class="stamp" :class="r.requiredFlag === 1 ? 'warn' : 'ok'">{{ r.requiredFlag === 1 ? '必传' : '选传' }}</span></td>
                  <td class="mono" style="font-size: 12px">{{ r.allowedFormats.join(' / ') }}</td>
                  <td class="fs">{{ r.namingRule || '—' }}</td>
                  <td class="num mono">{{ r.maxSizeMb }} MB</td>
                  <td>
                    <button class="btn-line" @click="openReqEdit(r)">编辑</button>
                    <button class="btn-line" style="margin-left: 6px" @click="onDeleteReq(r)">删除</button>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-if="currentReqs.length === 0" class="empty-note">尚未配置要求</p>
          </div>
        </template>
      </el-tab-pane>

      <el-tab-pane label="队伍概览" name="overview">
        <TeamOverviewPanel :tasks="tasks" />
      </el-tab-pane>
    </el-tabs>

    <TaskFormDialog v-model="taskDialogVisible" :task="editingTask" :contests="contests" @saved="onTaskSaved" />
    <RequirementFormDialog v-model="reqDialogVisible" :task-id="currentTask ? currentTask.id : ''" :requirement="editingReq" @saved="onReqSaved" />
  </div>
</template>

<style scoped>
tr.cur td { background: var(--vio2-panel-2); }
</style>
