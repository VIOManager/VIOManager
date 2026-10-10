<script setup lang="ts">
/**
 * 学期管理页（/semester，仅超管，A 线 S01–S08）
 * 对应方案：《前端ABC模块页面开发-优化方案》PA4
 * 两块：学期台账（增删改）+ 切换任务（发起/取消/执行的状态机与影响快照）
 * ⚠️ 安全：学期切换是高危操作——发起要密码二次确认，执行要 ElMessageBox 再确认 + 乐观锁版本
 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiCancelSwitchTask, apiDeleteSemester, apiExecuteSwitchTask, apiSemesters, apiSwitchTasks } from '@/api/semester'
import type { Semester, SwitchTask } from '@/api/semester'
import { SEMESTER_STATUS_LABEL, SWITCH_TASK_STATUS_LABEL } from '@/config/labels'
import SemesterFormDialog from './SemesterFormDialog.vue'
import SwitchTaskDialog from './SwitchTaskDialog.vue'

const semesters = ref<Semester[]>([])
const tasks = ref<SwitchTask[]>([])
const loading = ref(false)

const formVisible = ref(false)
const editing = ref<Semester | null>(null)
const switchVisible = ref(false)

async function load() {
  loading.value = true
  try {
    semesters.value = await apiSemesters()
    tasks.value = await apiSwitchTasks()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  formVisible.value = true
}

function openEdit(s: Semester) {
  editing.value = s
  formVisible.value = true
}

async function onDelete(s: Semester) {
  try {
    await ElMessageBox.confirm('确认删除学期「' + s.name + '」？', '删除学期', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await apiDeleteSemester(s.id)
    ElMessage.success('已删除')
    await load()
  } catch {
    // 3009 有关联数据拒删提示已由拦截器弹出
  }
}

async function onCancelTask(t: SwitchTask) {
  try {
    await apiCancelSwitchTask(t.id)
    ElMessage.success('任务已取消')
    await load()
  } catch {
    // 提示已由拦截器弹出
  }
}

/** 执行切换：二次确认文案带影响快照；携带 version 乐观锁 */
async function onExecuteTask(t: SwitchTask) {
  const text =
    '将归档当前学期并激活「' + t.toSemesterName + '」。影响快照：待归档比赛 ' +
    t.impact.contestCount + ' 项、待继承赛队 ' + t.impact.teamCount + ' 支、成员关系 ' +
    t.impact.memberRelationCount + ' 条。执行期间系统进入维护锁定。确认执行？'
  try {
    await ElMessageBox.confirm(text, '执行学期切换（高危）', {
      confirmButtonText: '执行',
      cancelButtonText: '再想想',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await apiExecuteSwitchTask(t.id, t.version)
    ElMessage.success('切换完成')
    await load()
  } catch {
    // 3011 版本冲突等提示已由拦截器弹出
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>学期管理</h3>
      <div class="dossier-meta mono">
        <span>仅超级管理员</span>
        <span>A 线 · S01–S08</span>
      </div>
    </div>

    <div class="panel" style="margin-bottom: 18px">
      <h5 class="panel-h">学期台账 <button class="btn-ink" style="font-size: 12px; padding: 5px 14px" @click="openCreate">新建学期</button></h5>
      <table class="ledger">
        <thead>
          <tr><th>名称</th><th>起止日期</th><th class="num">教学周数</th><th>状态</th><th style="width: 130px">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="s in semesters" :key="s.id">
            <td style="color: var(--vio-ink); font-weight: 600">{{ s.name }}</td>
            <td class="mono nw">{{ s.startDate }} ~ {{ s.endDate }}</td>
            <td class="num mono">{{ s.weekCount }}</td>
            <td><span class="stamp" :class="s.status === 'ACTIVE' ? 'ok' : s.status === 'PLANNED' ? 'blue' : 'dim'">{{ SEMESTER_STATUS_LABEL[s.status] }}</span></td>
            <td class="nw">
              <button class="btn-act" @click="openEdit(s)">编辑</button>
              <button class="btn-act danger" @click="onDelete(s)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && semesters.length === 0" class="empty-note">暂无学期</p>
    </div>

    <div class="panel">
      <h5 class="panel-h">学期切换任务 <button class="btn-line" style="font-size: 12px; padding: 5px 14px" @click="switchVisible = true">发起切换</button></h5>
      <table class="ledger">
        <thead>
          <tr><th>任务号</th><th>目标学期</th><th>创建时间</th><th>窗口截止</th><th>影响快照</th><th>状态</th><th style="width: 120px">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="t in tasks" :key="t.id">
            <td class="mono nw">{{ t.id }} <span class="fs" style="color: var(--vio-ink-3)">v{{ t.version }}</span></td>
            <td style="color: var(--vio-ink); font-weight: 600">{{ t.toSemesterName }}</td>
            <td class="mono nw">{{ t.createdAt }}</td>
            <td class="mono nw">{{ t.deadline }}</td>
            <td class="fs" style="font-size: 12px">
              归档比赛 {{ t.impact.contestCount }} · 继承赛队 {{ t.impact.teamCount }} · 成员关系 {{ t.impact.memberRelationCount }}
            </td>
            <td><span class="stamp" :class="t.status === 'SUCCESS' ? 'ok' : t.status === 'PENDING' ? 'warn' : t.status === 'CANCELLED' ? 'dim' : 'bad'">{{ SWITCH_TASK_STATUS_LABEL[t.status] }}</span></td>
            <td class="nw">
              <template v-if="t.status === 'PENDING'">
                <button class="btn-act" @click="onExecuteTask(t)">执行</button>
                <button class="btn-act danger" @click="onCancelTask(t)">取消</button>
              </template>
              <span v-else class="fs" style="color: var(--vio-ink-3); font-size: 12px">—</span>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && tasks.length === 0" class="empty-note">暂无切换任务，点右上"发起切换"创建</p>
    </div>

    <SemesterFormDialog v-model="formVisible" :semester="editing" @saved="load" />
    <SwitchTaskDialog v-model="switchVisible" :semesters="semesters" @created="load" />
  </div>
</template>
