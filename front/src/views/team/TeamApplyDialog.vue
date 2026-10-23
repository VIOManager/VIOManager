<script setup lang="ts">
/**
 * 组队申请弹窗（TE03）：选比赛（仅报名中）→ 选赛道 → 队名/项目名 → 预填成员
 * ⚠️ 预填成员数上限取比赛生效规则 prefillLimit（清单 §7 三人队取值未拍板，mock 模板默认 1）
 * 提交后生成 TEAM_CREATE 审批单，不直接建队（审批中心可见待办）
 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { apiContests, apiContestDetail } from '@/api/contest'
import type { ContestListItem, ContestTrack } from '@/api/contest'
import { apiCreateTeam } from '@/api/team'
import { apiMemberOptions } from '@/api/member'
import type { MemberOption } from '@/api/member'
import { RULE_KEY_LABEL, ruleValueText } from '@/config/labels'

const visible = defineModel<boolean>({ required: true })
const emit = defineEmits<{ submitted: [] }>()

const contests = ref<ContestListItem[]>([])
const tracks = ref<ContestTrack[]>([])
const form = reactive({ contestId: '', trackId: '', name: '', projectName: '', prefillMemberIds: [] as string[] })
const memberOptions = ref<MemberOption[]>([])
const prefillLimit = ref(0)
const submitting = ref(false)

watch(visible, async (v) => {
  if (!v) return
  Object.assign(form, { contestId: '', trackId: '', name: '', projectName: '', prefillMemberIds: [] })
  tracks.value = []
  memberOptions.value = []
  const all = await apiContests({})
  contests.value = all.filter((c) => c.status === 'REGISTERING')
})

const selectedContest = computed(() => {
  for (const c of contests.value) {
    if (c.id === form.contestId) return c
  }
  return null
})

async function onContestChange() {
  form.trackId = ''
  form.prefillMemberIds = []
  tracks.value = []
  prefillLimit.value = 0
  if (!form.contestId) return
  const d = await apiContestDetail(form.contestId)
  tracks.value = d.tracks
  for (const r of d.rules) {
    if (r.key === 'prefillLimit') prefillLimit.value = Number(r.value)
  }
}

async function onTrackChange() {
  form.prefillMemberIds = []
  memberOptions.value = []
  if (!form.contestId) return
  // 预填成员候选走 M14 受限搜索口（任意登录角色可用；M01 是管理员专属，学生队长调必 403）
  const resp = await apiMemberOptions({ page: 1, size: 50 })
  memberOptions.value = resp.list
}

function memberLabel(id: string): string {
  for (const m of memberOptions.value) {
    if (m.id === id) return m.name + ' ' + m.studentNo
  }
  return id
}

async function onSubmit() {
  if (!form.contestId || !form.name.trim() || !form.projectName.trim()) {
    ElMessage.warning('比赛、队名、项目名必填')
    return
  }
  if (tracks.value.length > 0 && !form.trackId) {
    ElMessage.warning('请选择赛道')
    return
  }
  submitting.value = true
  try {
    await apiCreateTeam({
      contestId: form.contestId,
      trackId: form.trackId,
      name: form.name.trim(),
      projectName: form.projectName.trim(),
      prefillMemberIds: [...form.prefillMemberIds]
    })
    ElMessage.success('组队申请已提交，等待管理员审批（审批中心可见）')
    visible.value = false
    emit('submitted')
  } catch {
    // 4002 队名重复 / 4003 超预填上限等提示已由拦截器弹出
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="发起组队申请" width="560px">
    <el-form label-width="92px" @submit.prevent>
      <el-form-item label="比赛" required>
        <el-select v-model="form.contestId" style="width: 100%" placeholder="仅报名中的比赛可选" @change="onContestChange">
          <el-option v-for="c in contests" :key="c.id" :value="c.id" :label="c.name" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="tracks.length > 0" label="赛道" required>
        <el-select v-model="form.trackId" style="width: 100%" @change="onTrackChange">
          <el-option v-for="t in tracks" :key="t.id" :value="t.id" :label="t.name" />
        </el-select>
      </el-form-item>
      <el-form-item label="队名" required><el-input v-model="form.name" placeholder="同比赛内唯一（重复报 4002）" /></el-form-item>
      <el-form-item label="项目名" required><el-input v-model="form.projectName" /></el-form-item>
      <el-form-item v-if="form.contestId" label="预填成员">
        <el-select v-model="form.prefillMemberIds" multiple style="width: 100%" :placeholder="'最多 ' + prefillLimit + ' 人（规则：' + RULE_KEY_LABEL.prefillLimit + '）'">
          <el-option v-for="m in memberOptions" :key="m.id" :value="m.id" :label="memberLabel(m.id)" :disabled="form.prefillMemberIds.length >= prefillLimit && !form.prefillMemberIds.includes(m.id)" />
        </el-select>
      </el-form-item>
      <p v-if="form.contestId" class="fs" style="font-size: 12px; color: var(--vio-ink-3); margin-left: 92px">
        该比赛规则：{{ RULE_KEY_LABEL.prefillLimit }} {{ ruleValueText(prefillLimit) }}；提交后生成审批单，审批通过才建队
      </p>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">提交申请</el-button>
    </template>
  </el-dialog>
</template>
