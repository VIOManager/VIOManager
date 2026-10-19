<script setup lang="ts">
/**
 * 比赛时间节点面板（PR16–PR20）
 * 管理员维护比赛关键日期：部门管理员 草稿 → 提交审核（MILESTONE_APPROVE 单，审批中心可见）
 * → 超管批后自动给挂载该比赛的项目生成任务（审批回调，mock 同构模拟）
 * 超管创建即生效（2026-10-16 拍板：审批人本就是超管，免审直接 APPROVED+同步任务）
 * 仅 DRAFT/REJECTED 可编辑；DRAFT 可删，超管例外可删非 PENDING（直生效节点修正路径，7009 兜底）
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiContests } from '@/api/contest'
import type { ContestListItem } from '@/api/contest'
import { apiCreateMilestone, apiDeleteMilestone, apiMilestones, apiSubmitMilestone } from '@/api/project'
import type { Milestone } from '@/api/project'
import { useUserStore } from '@/stores/user'
import { MILESTONE_STATUS_LABEL, MILESTONE_TYPE_LABEL } from '@/config/labels'

const userStore = useUserStore()
const isSuper = computed(() => userStore.role === 'SUPER_ADMIN')

const contests = ref<ContestListItem[]>([])
const contestId = ref('')
const list = ref<Milestone[]>([])
const loading = ref(false)

async function load() {
  if (!contestId.value) {
    list.value = []
    return
  }
  loading.value = true
  try {
    list.value = await apiMilestones(contestId.value)
  } finally {
    loading.value = false
  }
}

// ── 新建节点 ──
const formVisible = ref(false)
const form = ref({ nodeType: 'OTHER', name: '', nodeTime: '', description: '' })
const saving = ref(false)

async function submit() {
  if (saving.value) return
  if (!form.value.name.trim() || !form.value.nodeTime) {
    ElMessage.warning('节点名称与时间不能为空')
    return
  }
  saving.value = true
  try {
    // datetime-local 出参 'YYYY-MM-DDTHH:mm'（带T无秒），后端契约 'yyyy-MM-dd HH:mm:ss'
    let nodeTime = form.value.nodeTime.replace('T', ' ')
    if (nodeTime.length === 16) nodeTime += ':00'
    await apiCreateMilestone(contestId.value, { ...form.value, nodeTime })
    // 超管创建即生效（免审直接 APPROVED+同步任务）；部门管理员仍是草稿待提审
    ElMessage.success(isSuper.value
      ? '节点已创建并直接生效（超管免审，已同步任务到挂载项目）'
      : '节点已创建（草稿），提交审核后生效')
    formVisible.value = false
    form.value = { nodeType: 'OTHER', name: '', nodeTime: '', description: '' }
    await load()
  } finally {
    saving.value = false
  }
}

async function onSubmit(m: Milestone) {
  try {
    await ElMessageBox.confirm('提交审核「' + m.name + '」？将在审批中心生成待办（审批人=超管）', '提交审核确认', { type: 'warning' })
  } catch {
    return
  }
  await apiSubmitMilestone(m.id)
  ElMessage.success('已提交审核，请到审批中心跟进')
  await load()
}

async function onDelete(m: Milestone) {
  const tip = m.status === 'DRAFT' ? '删除草稿节点' : '删除已生效节点「' + m.name + '」？（仅超管可删，已同步的任务不回收）'
  try {
    await ElMessageBox.confirm(tip, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await apiDeleteMilestone(m.id)
  ElMessage.success('已删除')
  await load()
}

/** 删除按钮显隐：DRAFT 人人可删（管理端）；超管例外可删 APPROVED/REJECTED（直生效修正路径），PENDING 恒不可删 */
function canDelete(m: Milestone): boolean {
  return m.status === 'DRAFT' || (isSuper.value && m.status !== 'PENDING')
}

function stampClass(status: string): string {
  if (status === 'PENDING') return 'warn'
  if (status === 'APPROVED') return 'ok'
  if (status === 'REJECTED') return 'bad'
  return 'dim'
}

onMounted(async () => {
  const r = await apiContests({})
  contests.value = r
  if (r.length > 0) {
    contestId.value = r[0].id
    await load()
  }
})
</script>

<template>
  <div>
    <div class="fbar">
      <select v-model="contestId" class="input-line" style="min-width: 260px" @change="load">
        <option v-for="c in contests" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
      <span class="fs" style="font-size: 12px; color: var(--vio-ink-3)">节点审核通过后，自动给挂载该比赛的 ACTIVE 项目生成同名任务</span>
      <span style="flex: 1"></span>
      <button class="btn-ink" :disabled="!contestId" @click="formVisible = true">新建节点</button>
    </div>

    <div class="panel">
      <table class="ledger">
        <thead>
          <tr><th>节点</th><th>类型</th><th>时间</th><th>状态</th><th>审批人/原因</th><th style="width: 150px">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="m in list" :key="m.id">
            <td style="color: var(--vio-ink); font-weight: 600">{{ m.name }}</td>
            <td>{{ MILESTONE_TYPE_LABEL[m.nodeType] }}</td>
            <td class="mono" style="font-size: 12px">{{ m.nodeTime }}</td>
            <td><span class="stamp" :class="stampClass(m.status)">{{ MILESTONE_STATUS_LABEL[m.status] }}</span></td>
            <td style="font-size: 12px">{{ m.status === 'REJECTED' ? m.rejectReason : m.approvedBy || '—' }}</td>
            <td>
              <button v-if="m.status === 'DRAFT' || m.status === 'REJECTED'" class="btn-act" @click="onSubmit(m)">提交审核</button>
              <button v-if="canDelete(m)" class="btn-act" @click="onDelete(m)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && list.length === 0" class="empty-note">该比赛暂无节点</p>
    </div>

    <el-dialog v-model="formVisible" title="新建时间节点" width="460px">
      <div class="form-col">
        <label>类型</label>
        <select v-model="form.nodeType" class="input-line">
          <option v-for="(label, v) in MILESTONE_TYPE_LABEL" :key="v" :value="v">{{ label }}</option>
        </select>
        <label>节点名称</label>
        <input v-model="form.name" class="input-line" placeholder="如 作品提交截止" />
        <label>节点时间</label>
        <input v-model="form.nodeTime" type="datetime-local" class="input-line" />
        <label>说明（选填）</label>
        <textarea v-model="form.description" class="input-line" rows="2"></textarea>
      </div>
      <template #footer>
        <button class="btn-line" @click="formVisible = false">取消</button>
        <button class="btn-ink" :disabled="saving" @click="submit">创建</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.stamp.bad { color: var(--vio-danger); border-color: var(--vio-danger); }
.stamp.dim { color: var(--vio-ink-3); border-color: var(--vio-rule); }
.form-col { display: flex; flex-direction: column; gap: 6px; }
.form-col label { font-size: 12px; color: var(--vio-ink-3); margin-top: 6px; }
</style>
