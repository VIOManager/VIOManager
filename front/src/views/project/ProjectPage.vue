<script setup lang="ts">
/**
 * 项目管理页（/project，管理端，PR01–PR31）
 * 两 tab：项目列表（筛选+新建+详情抽屉）/ 比赛节点（DRAFT→提交审核→通过后自动同步任务）
 * 可见性八条由后端判定（清单 §2）；管理权显隐以 viewerCanManage 驱动（所有人 or 管理员，2026-10-16 拍板）
 * 新建支持管理员代建（ownerMemberId 帮成员创建）；关联比赛（contestId 选填，挂载池按此过滤）
 * 节点时间出参转契约格式（MilestonePanel）
 */
import { onMounted, computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiCreateProject, apiProjects } from '@/api/project'
import type { ProjectItem } from '@/api/project'
import { apiMembers } from '@/api/member'
import type { MemberListItem } from '@/api/member'
import { apiContests } from '@/api/contest'
import type { ContestListItem } from '@/api/contest'
import { useUserStore } from '@/stores/user'
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

// ── 新建项目（所有人=当前用户；管理员可代建=帮指定成员创建；幂等键随提交生成）──
const userStore = useUserStore()
const isAdmin = computed(() => userStore.role === 'SUPER_ADMIN' || userStore.role === 'DEPT_ADMIN')

const formVisible = ref(false)
const form = ref({ name: '', description: '', visibility: 'ORG', startDate: '', endDate: '', contestId: '' })
const submitting = ref(false)

/** 关联比赛候选（当前学期全部，选填）：挂载池按 contest_id 过滤，不关联的项目进不了对应比赛的池 */
const contests = ref<ContestListItem[]>([])

// 代建（仅管理员）：null=为自己建；选中成员后帮其创建（搜索走 A 线人员档案）
const owner = ref<MemberListItem | null>(null)
const ownerKw = ref('')
const ownerOptions = ref<MemberListItem[]>([])

async function searchOwner() {
  if (!ownerKw.value.trim()) return
  const r = await apiMembers({ page: 1, size: 10, keyword: ownerKw.value.trim() })
  ownerOptions.value = r.list
}

function pickOwner(m: MemberListItem) {
  owner.value = m
  ownerOptions.value = []
  ownerKw.value = ''
}

function resetCreateForm() {
  form.value = { name: '', description: '', visibility: 'ORG', startDate: '', endDate: '', contestId: '' }
  owner.value = null
  ownerOptions.value = []
  ownerKw.value = ''
}

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
      endDate: form.value.endDate,
      ...(form.value.contestId ? { contestId: form.value.contestId } : {}),
      ...(owner.value ? { ownerMemberId: owner.value.id } : {})
    })
    ElMessage.success(owner.value
      ? '项目已创建（所有人：' + owner.value.name + '）'
      : '项目已创建（你是所有人，可加成员/挂赛队）')
    formVisible.value = false
    resetCreateForm()
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

onMounted(async () => {
  load()
  try {
    contests.value = await apiContests({})
  } catch {
    contests.value = [] // 比赛加载失败不阻断建项，仅无可选项
  }
})
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
        <label>关联比赛（选填：关联后才能被该比赛的赛队挂载）</label>
        <select v-model="form.contestId" class="input-line">
          <option value="">不关联（通用项目）</option>
          <option v-for="c in contests" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <template v-if="isAdmin">
          <label>所有人（管理员代建，选填：不选=为自己创建）</label>
          <template v-if="owner">
            <div class="owner-picked">
              <span>已选：{{ owner.name }}<em class="mono">· {{ owner.college }}</em></span>
              <button class="btn-act" @click="owner = null">重选</button>
            </div>
          </template>
          <template v-else>
            <div style="display: flex; gap: 8px">
              <input v-model="ownerKw" class="input-line" style="flex: 1" placeholder="姓名/学号搜索成员" @keyup.enter="searchOwner" />
              <button class="btn-line" @click="searchOwner">搜索</button>
            </div>
            <div v-for="m in ownerOptions" :key="m.id" class="owner-opt" @click="pickOwner(m)">
              <span>{{ m.name }}<em class="mono">· {{ m.college }} · {{ m.studentNo }}</em></span>
              <span class="mono" style="font-size: 12px">选为所有人</span>
            </div>
          </template>
        </template>
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
.form-col { display: flex; flex-direction: column; gap: 6px; }
.form-col label { font-size: 12px; color: var(--vio2-ink-2); margin-top: 6px; }
.owner-picked {
  display: flex; align-items: center; justify-content: space-between;
  padding: 6px 10px; border: 2px dashed var(--vio2-bd); background: var(--vio2-panel);
  font-size: 13px;
}
.owner-picked em { font-style: normal; color: var(--vio2-ink-2); margin-left: 6px; font-size: 12px; }
.owner-opt {
  display: flex; align-items: center; justify-content: space-between;
  padding: 5px 10px; border: 1px solid var(--vio2-bd); font-size: 13px; cursor: pointer;
}
.owner-opt:hover { background: var(--vio2-yellow); }
.owner-opt em { font-style: normal; color: var(--vio2-ink-2); margin-left: 6px; font-size: 12px; }
</style>
