<script setup lang="ts">
/**
 * 审批中心页（/approval，FS5，契约 P01–P09）
 * 管理员页签：待办（只看待审——批准/驳回后单子离开本栏）/ 已通过 / 已驳回（已办记录，2026-10-19
 *   lzh 需求"批准的得有记录"；后端 TODO 视角本就返回全状态单子，这里按状态分栏呈现）
 * 参与人页签：我发起的 / 我是目标人——对超管隐藏（无发起/被指向场景且种子超管未绑档案，后端回 1003）
 * 操作入口全部在详情抽屉（按当前视角显隐按钮，已处理单子无操作钮；最终权限以后端判定为准——规范 §5.3）
 * 红点联动：打开详情即按当前视角标记已读（P07 置位幂等），处理操作后刷新 P08 红点
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiApprovalList, apiMarkRead } from '@/api/approval'
import type { ApprovalItem, ApprovalRole, ApprovalView } from '@/api/approval'
import { APPROVAL_STATUS_LABEL, APPROVAL_TYPE_LABEL } from '@/config/labels'
import { useUserStore } from '@/stores/user'
import { useApprovalStore } from '@/stores/approval'
import ScrollLoader from '@/components/ScrollLoader.vue'
import ApprovalDetailDrawer from './ApprovalDetailDrawer.vue'

const user = useUserStore()
const approval = useApprovalStore()

const isAdmin = user.role !== 'STUDENT'

/** 超管判定：MINE/TARGET 两个参与人视角对其隐藏（页面头注释里的口径） */
const isSuperAdmin = user.role === 'SUPER_ADMIN'

// ── 页签与筛选 ──
/** 页签 key：TODO/MINE/TARGET 直连契约视角；DONE_OK/DONE_NO 是管理员已办记录页签（TODO 视角 + 固定状态） */
type TabKey = ApprovalView | 'DONE_OK' | 'DONE_NO'
const view = ref<TabKey>(isAdmin ? 'TODO' : 'MINE')
const statusFilter = ref('')
const typeFilter = ref('')
const keyword = ref('')
const page = ref(1)
const size = 20
const total = ref(0)
const list = ref<ApprovalItem[]>([])
const loading = ref(false)

const drawerVisible = ref(false)
const detailId = ref('')

/** tab 配置：待办/已办无红点（管理员工作职责不进 P08）；MINE/TARGET 挂 P08 红点 */
const tabs = computed(() => {
  const out: Array<{ key: TabKey; label: string; badge: number }> = []
  if (isAdmin) {
    out.push({ key: 'TODO', label: '待办', badge: 0 })
    // 已办记录：批准/驳回过的单子按状态分栏留档（后端 TODO 视角全状态返回，前端按状态分栏）
    out.push({ key: 'DONE_OK', label: '已通过', badge: 0 })
    out.push({ key: 'DONE_NO', label: '已驳回', badge: 0 })
  }
  // 参与人视角对超管隐藏：部门管理员与学生的成果提审/授权申请/邀请通知照常汇总在这两栏
  if (!isSuperAdmin) {
    out.push({ key: 'MINE', label: '我发起的', badge: approval.resultCount })
    out.push({ key: 'TARGET', label: '我是目标人', badge: approval.todoCount })
  }
  return out
})

/** 当前页签的查询参数：待办固定只看待审；已办两栏固定状态；参与人视角状态下拉自选 */
function queryOf(): { view: ApprovalView; status?: string } {
  if (view.value === 'TODO') return { view: 'TODO', status: 'PENDING' }
  if (view.value === 'DONE_OK') return { view: 'TODO', status: 'APPROVED' }
  if (view.value === 'DONE_NO') return { view: 'TODO', status: 'REJECTED' }
  return { view: view.value, status: statusFilter.value || undefined }
}

/** 首次加载 / 切视角 / 筛选 / 处理完成刷新：清空回第 1 批 */
async function load() {
  loading.value = true
  try {
    page.value = 1
    const r = await apiApprovalList({
      ...queryOf(),
      bizType: typeFilter.value || undefined,
      keyword: keyword.value || undefined,
      page: 1,
      size
    })
    list.value = r.list
    total.value = r.total
  } catch {
    // 加载失败（网络/业务拒绝如 1003）：清掉旧列表，避免残留上一个视角的数据造成误读；
    // 错误提示已由 http.ts 统一弹出，这里只兜住不外抛
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 滚动到底追加下一批（ScrollLoader 的 reach 回调） */
async function loadMore() {
  if (loading.value || list.value.length >= total.value) return
  loading.value = true
  try {
    page.value++
    const r = await apiApprovalList({
      ...queryOf(),
      bizType: typeFilter.value || undefined,
      keyword: keyword.value || undefined,
      page: page.value,
      size
    })
    list.value = list.value.concat(r.list)
    total.value = r.total
  } catch {
    // 追加失败：保住已加载的行，仅结束本次加载；提示由 http.ts 统一弹
  } finally {
    loading.value = false
  }
}

function switchView(v: TabKey) {
  view.value = v
  void load()
}

function onSearch() {
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
      <button v-if="(view === 'MINE' && approval.resultCount > 0) || (view === 'TARGET' && approval.todoCount > 0)" class="btn-line" @click="markAllRead">
        全部已读
      </button>
    </div>

    <div class="fbar">
      <input v-model="keyword" class="input-line" style="width: 180px" placeholder="搜索标题（模糊）" @keyup.enter="onSearch" />
      <!-- 状态下拉只在参与人视角出现：待办/已通过/已驳回页签的状态是固定死的 -->
      <select v-if="view === 'MINE' || view === 'TARGET'" v-model="statusFilter" class="input-line" @change="onSearch">
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
      <ScrollLoader :loading="loading" :no-more="list.length >= total" :loaded="list.length" :total="total" @reach="loadMore" />
    </div>

    <!-- 已办页签复用 TODO 视角打开抽屉：单子已出结果时抽屉不出任何操作钮（组件内 pending 守卫） -->
    <ApprovalDetailDrawer v-model="drawerVisible" :approval-id="detailId" :view="view === 'MINE' || view === 'TARGET' ? view : 'TODO'" @changed="onChanged" />
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
.vtab em { font-style: normal; margin-left: 6px; font-size: 11px; color: var(--vio2-pink); font-weight: 700; }
.vtab.active em { background: #101010; color: var(--vio2-yellow); padding: 0 4px; }
</style>
