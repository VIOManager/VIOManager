<script setup lang="ts">
/**
 * 项目任务面板（PR21–PR24）
 * 手工任务由所有人创建；节点同步任务带 milestoneNodeId 溯源标（标题旁"节点"章）
 * 负责人本人仅可置 DONE（其他人编辑由后端 7002 拦）；删除仅所有人（节点任务也可删，不自动重建）
 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiCreateTask, apiDeleteTask, apiProjectTasks, apiUpdateTask } from '@/api/project'
import type { ProjectTask } from '@/api/project'
import { TASK_PRIORITY_LABEL, TASK_STATUS_LABEL } from '@/config/labels'

const props = defineProps<{ projectId: string; canEdit: boolean }>()

const list = ref<ProjectTask[]>([])

async function load() {
  list.value = await apiProjectTasks(props.projectId)
}

// ── 新建任务 ──
const formVisible = ref(false)
const form = ref({ title: '', description: '', dueDate: '', priority: 'NORMAL' })
const saving = ref(false)

async function submit() {
  if (saving.value) return
  if (!form.value.title.trim()) {
    ElMessage.warning('任务标题不能为空')
    return
  }
  saving.value = true
  try {
    await apiCreateTask(props.projectId, form.value)
    ElMessage.success('任务已创建')
    formVisible.value = false
    form.value = { title: '', description: '', dueDate: '', priority: 'NORMAL' }
    await load()
  } finally {
    saving.value = false
  }
}

async function markDone(t: ProjectTask) {
  await apiUpdateTask(props.projectId, t.id, { status: 'DONE' })
  ElMessage.success('已完成')
  await load()
}

async function onDelete(t: ProjectTask) {
  try {
    await ElMessageBox.confirm('删除任务「' + t.title + '」？节点同步任务删后不自动重建', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await apiDeleteTask(props.projectId, t.id)
  await load()
}

onMounted(load)
</script>

<template>
  <div class="zone">
    <h6>
      项目任务
      <button v-if="canEdit" class="btn-line" style="float: right" @click="formVisible = true">新建任务</button>
    </h6>
    <div v-for="t in list" :key="t.id" class="task-row">
      <span class="task-title">
        {{ t.title }}
        <em v-if="t.milestoneNodeId" class="ms-tag mono">节点同步</em>
      </span>
      <span class="mono task-meta">
        {{ TASK_PRIORITY_LABEL[t.priority] ?? t.priority }} · {{ t.assigneeName || '未指派' }} · 截止 {{ t.dueDate || '—' }}
      </span>
      <span class="task-ops">
        <span class="stamp" :class="t.status === 'DONE' ? 'ok' : t.status === 'DOING' ? 'warn' : 'dim'">{{ TASK_STATUS_LABEL[t.status] }}</span>
        <button v-if="t.status !== 'DONE'" class="btn-act" @click="markDone(t)">完成</button>
        <button v-if="canEdit" class="btn-act" @click="onDelete(t)">删除</button>
      </span>
    </div>
    <p v-if="list.length === 0" class="dim-note">暂无任务（比赛节点审核通过后会自动生成）</p>

    <el-dialog v-model="formVisible" title="新建任务" width="440px">
      <div class="form-col">
        <label>标题</label>
        <input v-model="form.title" class="input-line" placeholder="如 传感器采购清单确认" />
        <label>描述</label>
        <textarea v-model="form.description" class="input-line" rows="2"></textarea>
        <label>截止日期 / 优先级</label>
        <div style="display: flex; gap: 8px">
          <input v-model="form.dueDate" type="date" class="input-line" />
          <select v-model="form.priority" class="input-line">
            <option v-for="(label, v) in TASK_PRIORITY_LABEL" :key="v" :value="v">{{ label }}</option>
          </select>
        </div>
      </div>
      <template #footer>
        <button class="btn-line" @click="formVisible = false">取消</button>
        <button class="btn-ink" :disabled="saving" @click="submit">创建</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.zone { margin-top: 18px; padding-top: 12px; border-top: 1px solid var(--vio-rule-soft); }
.zone h6 { font-size: 12px; color: var(--vio-ink-3); margin-bottom: 10px; letter-spacing: 0.08em; }
.task-row { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px dashed var(--vio-rule-soft); font-size: 12.5px; }
.task-title { color: var(--vio-ink); font-weight: 600; }
.ms-tag { font-style: normal; font-size: 10px; border: 1px solid var(--vio-blue-ink); color: var(--vio-blue-ink); padding: 0 4px; margin-left: 6px; }
.task-meta { flex: 1; font-size: 11.5px; color: var(--vio-ink-3); }
.task-ops { display: flex; align-items: center; gap: 6px; }
.stamp.ok { color: var(--vio-ok, #2e7d32); }
.stamp.dim { color: var(--vio-ink-3); border-color: var(--vio-rule); }
.form-col { display: flex; flex-direction: column; gap: 6px; }
.form-col label { font-size: 12px; color: var(--vio-ink-3); margin-top: 6px; }
.dim-note { font-size: 12px; color: var(--vio-ink-3); }
</style>
