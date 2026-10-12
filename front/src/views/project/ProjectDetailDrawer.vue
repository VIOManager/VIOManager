<script setup lang="ts">
/**
 * 项目详情抽屉（PR03–PR14 + PR29–PR31）
 * 分区：基本信息（所有人可编辑/状态流转/删除）/ 成员与指导教师（不走审批）/ 挂载赛队 / 查看授权
 * 任务与文件区拆在 ProjectTaskPanel / ProjectFilePanel（单文件行数约束）
 * 权限显隐以 detail.viewerIsOwner 驱动（后端仍逐接口判定，7002 兜底）
 */
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiAddProjectAdvisor, apiAddProjectMember, apiDeleteProject, apiProjectDetail, apiProjectMembers, apiProjectTeams, apiRemoveProjectAdvisor, apiRemoveProjectMember, apiRevokeGrant, apiUpdateProject, apiViewGrants } from '@/api/project'
import type { ProjectDetail, ProjectMember, ProjectTeamRef, ViewGrant } from '@/api/project'
import { apiMembers } from '@/api/member'
import type { MemberListItem } from '@/api/member'
import { GRANT_STATUS_LABEL, GRANT_TYPE_LABEL, PROJECT_STATUS_LABEL, PROJECT_VISIBILITY_LABEL } from '@/config/labels'
import ProjectTaskPanel from './ProjectTaskPanel.vue'
import ProjectFilePanel from './ProjectFilePanel.vue'

const props = defineProps<{ modelValue: boolean; projectId: string }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; changed: [] }>()

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => {
  visible.value = v
  if (v) void load()
})
watch(visible, (v) => emit('update:modelValue', v))

const detail = ref<ProjectDetail | null>(null)
const members = ref<ProjectMember[]>([])
const teams = ref<ProjectTeamRef[]>([])
const grants = ref<ViewGrant[]>([])

async function load() {
  if (!props.projectId) return
  detail.value = await apiProjectDetail(props.projectId)
  members.value = await apiProjectMembers(props.projectId)
  teams.value = await apiProjectTeams(props.projectId)
  grants.value = await apiViewGrants(props.projectId).catch(() => [])
}

function changed() {
  emit('changed')
  void load()
}

// ── 所有人操作 ──
async function onStatusFlow() {
  if (!detail.value) return
  const next = detail.value.status === 'ACTIVE' ? 'FINISHED' : 'ARCHIVED'
  const label = PROJECT_STATUS_LABEL[next]
  try {
    await ElMessageBox.confirm('将项目状态流转为「' + label + '」？状态按序不可回退', '状态流转确认', { type: 'warning' })
  } catch {
    return
  }
  await apiUpdateProject(detail.value.id, { status: next })
  ElMessage.success('已流转为「' + label + '」')
  changed()
}

async function onDelete() {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm('删除该项目？有挂载中的赛队会被 7010 拦截', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await apiDeleteProject(detail.value.id)
  ElMessage.success('已删除')
  visible.value = false
  emit('changed')
}

// ── 成员/指导教师（搜索走 A 线人员档案列表，不走审批）──
const memberKw = ref('')
const memberOptions = ref<MemberListItem[]>([])

async function searchMember() {
  if (!memberKw.value.trim()) return
  const r = await apiMembers({ page: 1, size: 10, keyword: memberKw.value.trim() })
  memberOptions.value = r.list
}

async function addMember(m: MemberListItem) {
  if (!detail.value) return
  await apiAddProjectMember(detail.value.id, m.id)
  ElMessage.success('已加入项目（不走审批）')
  memberOptions.value = []
  memberKw.value = ''
  changed()
}

async function addAdvisor(m: MemberListItem) {
  if (!detail.value) return
  await apiAddProjectAdvisor(detail.value.id, m.id)
  ElMessage.success('已添加指导教师')
  memberOptions.value = []
  memberKw.value = ''
  changed()
}

async function removeMember(memberId: string, name: string) {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm('将 ' + name + ' 移出项目？', '移除确认', { type: 'warning' })
  } catch {
    return
  }
  await apiRemoveProjectMember(detail.value.id, memberId)
  changed()
}

async function onRemoveAdvisor(memberId: string) {
  if (!detail.value) return
  await apiRemoveProjectAdvisor(detail.value.id, memberId)
  ElMessage.success('已移除指导教师')
  changed()
}

async function revokeGrant(g: ViewGrant) {
  try {
    await ElMessageBox.confirm('撤销 ' + g.applicantName + ' 的查看授权？', '撤销确认', { type: 'warning' })
  } catch {
    return
  }
  await apiRevokeGrant(g.id)
  ElMessage.success('已撤销')
  changed()
}
</script>

<template>
  <el-drawer v-model="visible" :title="detail ? detail.name : '项目详情'" size="560px">
    <template v-if="detail">
      <div class="dossier-meta mono" style="margin-bottom: 12px">
        <span class="stamp" :class="detail.status === 'ACTIVE' ? 'warn' : detail.status === 'FINISHED' ? 'ok' : 'dim'">{{ PROJECT_STATUS_LABEL[detail.status] }}</span>
        <span>{{ PROJECT_VISIBILITY_LABEL[detail.visibility] }}</span>
      </div>
      <dl class="sheet">
        <div><dt>所有人</dt><dd>{{ detail.ownerName }}</dd></div>
        <div><dt>所属组织</dt><dd>{{ detail.orgName }}</dd></div>
        <div><dt>学期</dt><dd>{{ detail.semesterName }}</dd></div>
        <div><dt>关联比赛</dt><dd>{{ detail.contestName || '—' }}</dd></div>
        <div><dt>起止</dt><dd class="mono">{{ detail.startDate }} ~ {{ detail.endDate }}</dd></div>
      </dl>
      <p v-if="detail.description" class="desc">{{ detail.description }}</p>

      <div v-if="detail.viewerIsOwner" class="zone">
        <h6>所有人操作</h6>
        <button v-if="detail.status !== 'ARCHIVED'" class="btn-line" @click="onStatusFlow">
          {{ detail.status === 'ACTIVE' ? '完结项目' : '归档项目' }}
        </button>
        <button class="btn-line" style="margin-left: 8px" @click="onDelete">删除项目</button>
      </div>

      <div class="zone">
        <h6>项目成员（不走审批）</h6>
        <div v-for="m in members" :key="m.memberId" class="row-line">
          <span>{{ m.name }}<em class="mono">{{ m.role === 'OWNER' ? '所有人' : '成员' }}</em></span>
          <button v-if="detail.viewerIsOwner && m.role !== 'OWNER'" class="btn-act" @click="removeMember(m.memberId, m.name)">移除</button>
        </div>
        <div v-for="a in detail.advisors" :key="a.memberId" class="row-line">
          <span>{{ a.name }}<em class="mono">指导教师 · {{ a.college }}</em></span>
          <button v-if="detail.viewerIsOwner" class="btn-act" @click="onRemoveAdvisor(a.memberId)">移除</button>
        </div>
        <template v-if="detail.viewerIsOwner">
          <div class="add-row">
            <input v-model="memberKw" class="input-line" style="flex: 1" placeholder="姓名/学号搜索" @keyup.enter="searchMember" />
            <button class="btn-line" @click="searchMember">搜索</button>
          </div>
          <div v-for="m in memberOptions" :key="m.id" class="row-line">
            <span>{{ m.name }}<em class="mono">{{ m.college }}</em></span>
            <span>
              <button class="btn-act" @click="addMember(m)">加为成员</button>
              <button class="btn-act" @click="addAdvisor(m)">加为指导教师</button>
            </span>
          </div>
        </template>
      </div>

      <div class="zone">
        <h6>挂载中的赛队（一项目可被多队挂载打不同比赛）</h6>
        <div v-for="t in teams" :key="t.teamId" class="row-line">
          <span>{{ t.teamName }}<em class="mono">{{ t.contestName }} · {{ t.status === 'ACTIVE' ? '挂载中' : '已解绑' }}</em></span>
        </div>
        <p v-if="teams.length === 0" class="dim-note">暂无赛队挂载（队长在赛队详情页挂载）</p>
      </div>

      <ProjectTaskPanel :project-id="detail.id" :can-edit="detail.viewerIsOwner" />
      <ProjectFilePanel :project-id="detail.id" :can-edit="detail.viewerIsOwner" />

      <div v-if="grants.length > 0" class="zone">
        <h6>查看授权</h6>
        <div v-for="g in grants" :key="g.id" class="row-line">
          <span>{{ g.applicantName }}<em class="mono">{{ GRANT_TYPE_LABEL[g.grantType] }}<template v-if="g.grantType === 'LIMITED'"> · 至 {{ g.expireAt }}</template></em></span>
          <span>
            <span class="stamp" :class="g.status === 'ACTIVE' ? 'ok' : g.status === 'PENDING' ? 'warn' : 'dim'">{{ GRANT_STATUS_LABEL[g.status] }}</span>
            <button v-if="g.status === 'ACTIVE' && detail.viewerIsOwner" class="btn-act" @click="revokeGrant(g)">撤销</button>
          </span>
        </div>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.sheet div { display: flex; padding: 7px 0; border-bottom: 1px dashed var(--vio-rule-soft); font-size: 13px; }
.sheet dt { width: 76px; color: var(--vio-ink-3); flex-shrink: 0; }
.sheet dd { color: var(--vio-ink); }
.desc { margin-top: 12px; padding: 10px 12px; background: var(--vio-wash); font-size: 12.5px; line-height: 1.8; }
.zone { margin-top: 18px; padding-top: 12px; border-top: 1px solid var(--vio-rule-soft); }
.zone h6 { font-size: 12px; color: var(--vio-ink-3); margin-bottom: 10px; letter-spacing: 0.08em; }
.row-line { display: flex; align-items: center; justify-content: space-between; padding: 7px 0; border-bottom: 1px dashed var(--vio-rule-soft); font-size: 13px; }
.row-line em { font-style: normal; font-size: 11px; color: var(--vio-ink-3); margin-left: 8px; }
.add-row { display: flex; gap: 8px; margin-top: 10px; }
.stamp.dim { color: var(--vio-ink-3); border-color: var(--vio-rule); }
.dim-note { font-size: 12px; color: var(--vio-ink-3); }
</style>
