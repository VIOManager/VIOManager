<script setup lang="ts">
/**
 * 审批中心页（/approval，FS5，契约 P01–P09）
 * 三视角 tab：待办（仅管理员，学生调 TODO 后端回 1003）/ 我发起的 / 我是目标人
 * 操作入口全部在详情抽屉（按当前视角显隐按钮，最终权限以后端判定为准——规范 §5.3）
 * 红点联动：打开详情即按当前视角标记已读（P07 置位幂等），处理操作后刷新 P08 红点
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiApprovalList, apiMarkRead } from '@/api/approval'
import type { ApprovalItem, ApprovalRole, ApprovalView } from '@/api/approval'
import { APPROVAL_STATUS_LABEL, APPROVAL_TYPE_LABEL } from '@/config/labels'
import { useUserStore } from '@/stores/user'
import { useApprovalStore } from '@/stores/approval'
import ApprovalDetailDrawer from './ApprovalDetailDrawer.vue'

const user = useUserStore()
const approval = useApprovalStore()

const isAdmin = user.role !== 'STUDENT'

// ── 三视角与筛选 ──
const view = ref<ApprovalView>(isAdmin ? 'TODO' : 'MINE')
const statusFilter = ref('')
const typeFilter = ref('')
const page = ref(1)
const size = 20
const total = ref(0)
const list = ref<ApprovalItem[]>([])
const loading = ref(false)

const drawerVisible = ref(false)
const detailId = ref('')

/** tab 配置：待办无红点（管理员待办是工作职责，P08 不含）；MINE/TARGET 挂 P08 红点 */
const tabs = computed(() => {
  const out: Array<{ key: ApprovalView; label: string; badge: number }> = []
  if (isAdmin) out.push({ key: 'TODO', label: '待办', badge: 0 })
  out.push({ key: 'MINE', label: '我发起的', badge: approval.resultCount })
  out.push({ key: 'TARGET', label: '我是目标人', badge: approval.todoCount })
  return out
})

async function load() {
  loading.value = true
  try {
    const r = await apiApprovalList({
      view: view.value,
      status: statusFilter.value || undefined,
      bizType: typeFilter.value || undefined,
      page: page.value,
      size
    })
    list.value = r.list
    total.value = r.total
  } finally {
    loading.value = false
  }
}

function switchView(v: ApprovalView) {
  view.value = v
  page.value = 1
  void load()
}

function onSearch() {
  page.value = 1
  void load()
}

/** 当前视角对应的已读身份位（P07 asRole） */
function asRoleOf(): ApprovalRole {
  if (view.value === 'MINE') return 'APPLICANT'
  if (view.value === 'TARGET') return 'TARGET'
  return 'APPROVER'
}

/** 打开详情：按当前视角标记该单已读（置位幂等），红点随 P08 刷新收敛 */
async function openDetail(id: string) {
  detailId.value = id
  drawerVisible.value = true
  await apiMarkRead([id], asRoleOf())
  void approval.refresh()
}

/** 全部已读：当前页可见单一次性置位（P07 幂等，多置无害） */
async function markAllRead() {
  const ids: string[] = []
  for (const a of list.value) ids.push(a.id)
  if (ids.length === 0) return
  await apiMarkRead(ids, asRoleOf())
  await approval.refresh()
  ElMessage.success('已全部标记为已读')
}

/** 抽屉内处理完成：刷新列表与红点 */
function onChanged() {
  void load()
  void approval.refresh()
}

function stampClass(status: string): string {
  if (status === 'PENDING') return 'warn'
  if (status === 'APPROVED') return 'ok'
  if (status === 'REJECTED') return 'bad'
  return 'dim'
}

onMounted(load)
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>审批中心</h3>
      <div class="dossier-meta mono">
        <span>共 {{ total }} 单</span>
        <span>骨架 · P01–P09</span>
      </div>
    </div>

    <div class="vtabs">
      <button
        v-for="t in tabs"
        :key="t.key"
        class="vtab"
        :class="{ active: view === t.key }"
        @click="switchView(t.key)"
      >
        {{ t.label }}<em v-if="t.badge">{{ t.badge }}</em>
      </button>
      <span style="flex: 1"></span>
      <button v-if="view !== 'TODO' && (view === 'MINE' ? approval.resultCount : approval.todoCount) > 0" class="btn-line" @click="markAllRead">
        全部已读
      </button>
    </div>

    <div class="fbar">
      <select v-model="statusFilter" class="input-line" @change="onSearch">
        <option value="">全部状态</option>
        <option v-for="(label, v) in APPROVAL_STATUS_LABEL" :key="v" :value="v">{{ label }}</option>
      </select>
      <select v-model="typeFilter" class="input-line" @change="onSearch">
        <option value="">全部类型</option>
        <option v-for="(label, v) in APPROVAL_TYPE_LABEL" :key="v" :value="v">{{ label }}</option>
      </select>
      <button class="btn-line" @click="onSearch">查询</button>
    </div>

    <div class="panel">
      <table class="ledger">
        <thead>
          <tr><th>标题</th><th>类型</th><th>申请人</th><th>目标人</th><th>状态</th><th>申请时间</th><th>处理时间</th><th style="width: 60px">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="a in list" :key="a.id" class="linkrow" @click="openDetail(a.id)">
            <td style="color: var(--vio-ink); font-weight: 600">{{ a.bizTitle }}</td>
            <td>{{ APPROVAL_TYPE_LABEL[a.bizType] ?? a.bizType }}</td>
            <td>{{ a.applicantName }}</td>
            <td>{{ a.targetMemberName || '—' }}</td>
            <td><span class="stamp" :class="stampClass(a.status)">{{ APPROVAL_STATUS_LABEL[a.status] ?? a.status }}</span></td>
            <td class="mono" style="font-size: 12px">{{ a.createdAt }}</td>
            <td class="mono" style="font-size: 12px">{{ a.handledAt || '—' }}</td>
            <td><button class="btn-act" @click.stop="openDetail(a.id)">详情</button></td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && list.length === 0" class="empty-note">暂无审批单</p>
    </div>

    <div v-if="total > size" class="pages mono">
      <button class="btn-line" :disabled="page <= 1" @click="page--; load()">上一页</button>
      <span>第 {{ page }} 页 · 共 {{ total }} 单</span>
      <button class="btn-line" :disabled="page * size >= total" @click="page++; load()">下一页</button>
    </div>

    <ApprovalDetailDrawer v-model="drawerVisible" :approval-id="detailId" :view="view" @changed="onChanged" />
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
.vtab em { font-style: normal; margin-left: 6px; font-size: 11px; color: var(--vio-seal); font-weight: 700; }
.vtab.active { border-color: var(--vio-ink); color: var(--vio-ink); font-weight: 700; box-shadow: inset 0 -2px 0 var(--vio-ink); }
.stamp.dim { color: var(--vio-ink-3); border-color: var(--vio-rule); }
.pages { display: flex; align-items: center; gap: 14px; margin-top: 14px; font-size: 12px; color: var(--vio-ink-3); }
</style>
