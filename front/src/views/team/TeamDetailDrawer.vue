<script setup lang="ts">
/**
 * 赛队详情抽屉（TE02/TE11 + 成员生命周期 TE04/TE06–TE10/TE12/TE13）
 * 视角驱动（viewerRole 由后端判定）：
 *   LEADER/ADMIN：加人、移除、编辑队名、加/删指导教师、解散
 *   MEMBER：退出（队长除外）；待确认成员本人：确认/拒绝
 * ⚠️ 安全：字段有无以后端裁剪结果为准；移除/解散为高危操作，二次确认
 */
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiAddAdvisor, apiConfirmTeamMember, apiDissolveTeam, apiExitTeam, apiRemoveAdvisor, apiRemoveTeamMember, apiTeamDetail, apiUpdateTeam } from '@/api/team'
import type { TeamDetail, TeamMemberItem } from '@/api/team'
import { apiMembers } from '@/api/member'
import { useUserStore } from '@/stores/user'
import { TEAM_MEMBER_STATUS_LABEL, TEAM_ROLE_LABEL } from '@/config/labels'
import MaskField from '@/components/MaskField.vue'
import MemberAddDialog from './MemberAddDialog.vue'

const visible = defineModel<boolean>({ required: true })
const props = defineProps<{ teamId: string }>()
const emit = defineEmits<{ changed: [] }>()

const user = useUserStore()
const detail = ref<TeamDetail | null>(null)
const loading = ref(false)
const addVisible = ref(false)
const advisorPicker = ref(false)
const advisorCandidates = ref<{ id: string; name: string; college: string }[]>([])

// 队名/项目名编辑态
const editingInfo = ref(false)
const editForm = ref({ name: '', projectName: '' })

watch([visible, () => props.teamId], async ([v]) => {
  if (!v || !props.teamId) return
  await reload()
})

async function reload() {
  loading.value = true
  try {
    detail.value = await apiTeamDetail(props.teamId)
  } finally {
    loading.value = false
  }
}

function canManage(): boolean {
  const vr = detail.value?.viewerRole
  return vr === 'LEADER' || vr === 'ADMIN'
}

/** 成员行操作：移除（管理者）；确认/拒绝（待确认本人——demo 里学生端用王雨桐登录即本人） */
function isSelf(m: TeamMemberItem): boolean {
  // 学生账号王雨桐绑定成员 m7；demo 简化：手机号非空且姓名匹配当前登录人
  return m.name === user.realName
}

async function onRemove(m: TeamMemberItem) {
  try {
    await ElMessageBox.confirm('确认申请移除队员「' + m.name + '」？将生成审批单。', '移除队员', {
      confirmButtonText: '提交移除申请', cancelButtonText: '取消', type: 'warning'
    })
  } catch {
    return
  }
  try {
    await apiRemoveTeamMember(props.teamId, m.memberId)
    ElMessage.success('移除申请已提交审批')
    emit('changed')
  } catch {
    // 4010 等提示已由拦截器弹出
  }
}

async function onConfirm(m: TeamMemberItem, action: 'CONFIRM' | 'REJECT') {
  await apiConfirmTeamMember(props.teamId, m.memberId, action)
  ElMessage.success(action === 'CONFIRM' ? '已确认加入' : '已拒绝')
  await reload()
  emit('changed')
}

async function onExit() {
  try {
    await ElMessageBox.confirm('确认退出本队？队长将在审批中心收到通知。', '退出赛队', {
      confirmButtonText: '退出', cancelButtonText: '取消', type: 'warning'
    })
  } catch {
    return
  }
  await apiExitTeam(props.teamId)
  ElMessage.success('已退出')
  visible.value = false
  emit('changed')
}

async function onDissolve() {
  const name = detail.value?.name ?? ''
  let reason = ''
  try {
    const r = await ElMessageBox.prompt('解散后不可恢复，请输入解散原因', '解散「' + name + '」', {
      confirmButtonText: '解散', cancelButtonText: '取消', inputPlaceholder: '解散原因'
    })
    reason = r.value ?? ''
  } catch {
    return
  }
  try {
    await apiDissolveTeam(props.teamId, reason)
    ElMessage.success('队伍已解散，成员将收到通知')
    visible.value = false
    emit('changed')
  } catch {
    // 4009 阻断提示已由拦截器弹出
  }
}

function startEditInfo() {
  editForm.value = { name: detail.value?.name ?? '', projectName: detail.value?.projectName ?? '' }
  editingInfo.value = true
}

async function saveInfo() {
  await apiUpdateTeam(props.teamId, { ...editForm.value })
  ElMessage.success('已保存')
  editingInfo.value = false
  await reload()
  emit('changed')
}

async function openAdvisorPicker() {
  // 指导教师候选：教师类型成员
  const resp = await apiMembers({ page: 1, size: 50, memberType: 'TEACHER' })
  advisorCandidates.value = resp.list.map((m) => ({ id: m.id, name: m.name, college: m.college }))
  advisorPicker.value = true
}

async function onAddAdvisor(id: string) {
  try {
    await apiAddAdvisor(props.teamId, id)
    ElMessage.success('指导教师申请已提交审批')
    advisorPicker.value = false
  } catch {
    // 4006 非教师等提示已由拦截器弹出
  }
}

async function onRemoveAdvisor(id: string) {
  await apiRemoveAdvisor(props.teamId, id)
  ElMessage.success('已移除指导教师')
  await reload()
}
</script>

<template>
  <el-drawer v-model="visible" :title="detail ? detail.name : '赛队详情'" size="560px">
    <p v-if="loading" class="empty-note">加载中…</p>
    <template v-else-if="detail">
      <template v-if="!editingInfo">
        <dl class="kv">
          <div><dt>队名</dt><dd>{{ detail.name }} <span class="stamp" :class="detail.status === 'ACTIVE' ? 'ok' : 'dim'">{{ detail.status === 'ACTIVE' ? '在队' : '已解散' }}</span></dd></div>
          <div><dt>项目名</dt><dd>{{ detail.projectName }}</dd></div>
          <div><dt>比赛</dt><dd>{{ detail.contestName }}</dd></div>
          <div><dt>赛道</dt><dd>{{ detail.trackName || '—' }}</dd></div>
          <div><dt>队长</dt><dd>{{ detail.leaderName }}</dd></div>
          <div><dt>材料</dt><dd>{{ detail.materialStatus }} · <span class="mono">{{ detail.materialCompleteRate }}%</span></dd></div>
        </dl>
        <div class="ops">
          <button v-if="canManage()" class="btn-line" @click="startEditInfo">编辑队名/项目名</button>
          <button v-if="detail.viewerRole === 'MEMBER'" class="btn-line danger" @click="onExit">退出赛队</button>
          <button v-if="canManage()" class="btn-line danger" @click="onDissolve">解散队伍</button>
        </div>
      </template>
      <template v-else>
        <div class="editline"><span class="fs">队名</span><input v-model="editForm.name" class="input-line" /></div>
        <div class="editline"><span class="fs">项目名</span><input v-model="editForm.projectName" class="input-line" /></div>
        <div class="ops">
          <button class="btn-ink" @click="saveInfo">保存</button>
          <button class="btn-line" @click="editingInfo = false">取消</button>
        </div>
      </template>

      <div class="sec">
        <h6>成员 <button v-if="canManage()" class="btn-act" @click="addVisible = true">加人</button></h6>
        <table class="ledger">
          <thead><tr><th>姓名</th><th>角色</th><th>状态</th><th>学院 / 班级</th><th>手机号</th><th style="width: 120px">操作</th></tr></thead>
          <tbody>
            <tr v-for="m in detail.members" :key="m.memberId">
              <td style="color: var(--vio-ink); font-weight: 600">{{ m.name }}</td>
              <td><span class="stamp" :class="m.role === 'LEADER' ? 'blue' : 'dim'">{{ TEAM_ROLE_LABEL[m.role] }}</span></td>
              <td><span class="stamp" :class="m.status === 'CONFIRMED' ? 'ok' : m.status === 'UNCONFIRMED' ? 'warn' : 'bad'">{{ TEAM_MEMBER_STATUS_LABEL[m.status] }}</span></td>
              <td class="fs" style="font-size: 12px">
                <template v-if="m.college">{{ m.college }}<template v-if="m.className"> · {{ m.className }}</template></template>
                <span v-else style="color: var(--vio-ink-3)">无权查看</span>
              </td>
              <td><MaskField v-if="m.phone" :text="m.phone" kind="phone" /><span v-else class="fs" style="color: var(--vio-ink-3); font-size: 12px">无权查看</span></td>
              <td class="nw">
                <template v-if="m.status === 'UNCONFIRMED' && isSelf(m)">
                  <button class="btn-act" @click="onConfirm(m, 'CONFIRM')">确认加入</button>
                  <button class="btn-act danger" @click="onConfirm(m, 'REJECT')">拒绝</button>
                </template>
                <button v-else-if="canManage() && m.role !== 'LEADER'" class="btn-act danger" @click="onRemove(m)">移除</button>
                <span v-else class="fs" style="color: var(--vio-ink-3); font-size: 12px">—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="sec">
        <h6>指导教师 <button v-if="canManage()" class="btn-act" @click="openAdvisorPicker">添加</button></h6>
        <table v-if="detail.advisors.length > 0" class="ledger">
          <tbody>
            <tr v-for="a in detail.advisors" :key="a.memberId">
              <td style="color: var(--vio-ink); font-weight: 600">{{ a.name }}</td>
              <td class="fs" style="font-size: 12px">{{ a.college }}</td>
              <td style="width: 70px"><button v-if="canManage()" class="btn-act danger" @click="onRemoveAdvisor(a.memberId)">移除</button></td>
            </tr>
          </tbody>
        </table>
        <p v-else class="empty-note" style="padding: 14px 0">暂无指导教师</p>
      </div>
    </template>

    <MemberAddDialog v-model="addVisible" :team-id="props.teamId" />
    <el-dialog v-model="advisorPicker" title="选择指导教师" width="420px" append-to-body>
      <table class="ledger">
        <tbody>
          <tr v-for="c in advisorCandidates" :key="c.id">
            <td style="color: var(--vio-ink); font-weight: 600">{{ c.name }}</td>
            <td class="fs" style="font-size: 12px">{{ c.college }}</td>
            <td style="width: 70px"><button class="btn-act" @click="onAddAdvisor(c.id)">申请</button></td>
          </tr>
        </tbody>
      </table>
    </el-dialog>
  </el-drawer>
</template>

<style scoped>
.kv { display: grid; grid-template-columns: 1fr 1fr; gap: 12px 20px; }
.kv dt { font-family: var(--vio-fangsong); font-size: 12px; color: var(--vio-ink-3); letter-spacing: 0.1em; }
.kv dd { font-size: 13.5px; color: var(--vio-ink); margin-top: 2px; }
.ops { display: flex; gap: 10px; margin: 16px 0 4px; flex-wrap: wrap; }
.sec { margin-top: 18px; border-top: 1px solid var(--vio-rule-soft); padding-top: 12px; }
.sec h6 { font-family: var(--vio-serif); font-size: 13.5px; letter-spacing: 0.12em; margin-bottom: 10px; display: flex; justify-content: space-between; align-items: baseline; }
.editline { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; font-size: 13px; }
.editline .input-line { flex: 1; }
.btn-line.danger { color: var(--vio-seal); border-color: var(--vio-seal); }
.btn-line.danger:hover { background: var(--vio-seal); color: #fff; }
</style>
