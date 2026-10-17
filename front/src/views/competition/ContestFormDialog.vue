<script setup lang="ts">
/**
 * 比赛创建/编辑弹窗（C03/C04；也承接待处理池确认预填 P02）
 * 模板选定后规则走 RulesEditor override 模式（只存被覆盖的键）
 * 时间窗校验：报名起 < 报名止 < 比赛起 < 比赛止（前端先挡一道，后端仍兜底）
 */
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { apiConfirmPoolItem, apiContestDetail, apiCreateContest, apiTemplates, apiUpdateContest } from '@/api/contest'
import type { ContestTemplate, RulesMap } from '@/api/contest'
import { apiSemestersSafe } from '@/api/semester'
import type { Semester } from '@/api/semester'
import RulesEditor from '@/components/RulesEditor.vue'

const visible = defineModel<boolean>({ required: true })
const props = defineProps<{
  /** 空串=创建；有值=编辑 */
  contestId: string
  /** 池确认预填（PB2） */
  prefill: { name: string; description: string; signupStart: string } | null
  /** 池条目 id（确认流程关联用） */
  poolId: string
}>()
const emit = defineEmits<{ saved: [] }>()

const empty = () => ({
  templateId: '', semesterId: '', name: '',
  signupStart: '', signupEnd: '', contestStart: '', contestEnd: '',
  contact: '', description: '', overrideRules: {} as RulesMap
})

const form = reactive(empty())
const templates = ref<ContestTemplate[]>([])
const semesters = ref<Semester[]>([])
const submitting = ref(false)

watch(visible, async (v) => {
  if (!v) return
  Object.assign(form, empty())
  if (templates.value.length === 0) templates.value = await apiTemplates()
  if (semesters.value.length === 0) semesters.value = await apiSemestersSafe()
  if (props.contestId) {
    const d = await apiContestDetail(props.contestId)
    Object.assign(form, {
      templateId: d.templateId, semesterId: d.semesterId, name: d.name,
      signupStart: d.signupStart, signupEnd: d.signupEnd,
      contestStart: d.contestStart, contestEnd: d.contestEnd,
      contact: d.contact, description: d.description, overrideRules: { ...d.overrideRules }
    })
  } else if (props.prefill) {
    form.name = props.prefill.name
    form.description = props.prefill.description
    form.signupStart = props.prefill.signupStart
  }
})

async function onSubmit() {
  if (!form.name.trim() || !form.templateId || !form.semesterId) {
    ElMessage.warning('名称、模板、学期必填')
    return
  }
  if (form.signupStart && form.signupEnd && form.contestStart) {
    if (!(form.signupStart <= form.signupEnd && form.signupEnd <= form.contestStart)) {
      ElMessage.warning('时间顺序应为：报名起 ≤ 报名止 ≤ 比赛起')
      return
    }
  }
  submitting.value = true
  try {
    if (props.contestId) {
      await apiUpdateContest(props.contestId, { ...form })
      ElMessage.success('比赛已更新')
    } else if (props.poolId) {
      await apiConfirmPoolItem(props.poolId, { ...form })
      ElMessage.success('已按池条目确认为正式比赛')
    } else {
      await apiCreateContest({ ...form })
      ElMessage.success('比赛已创建（草稿态）')
    }
    visible.value = false
    emit('saved')
  } catch {
    // 提示已由拦截器弹出
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="contestId ? '编辑比赛' : poolId ? '确认为正式比赛' : '创建比赛'" width="640px">
    <el-form label-width="92px" @submit.prevent>
      <el-form-item label="比赛名称" required><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="模板" required>
        <el-select v-model="form.templateId" style="width: 100%" :disabled="!!contestId">
          <el-option v-for="t in templates" :key="t.id" :value="t.id" :label="t.name" />
        </el-select>
      </el-form-item>
      <el-form-item label="学期" required>
        <el-select v-model="form.semesterId" style="width: 100%">
          <el-option v-for="s in semesters" :key="s.id" :value="s.id" :label="s.name" />
        </el-select>
      </el-form-item>
      <el-form-item label="报名起止">
        <div style="display: flex; gap: 8px; width: 100%">
          <el-date-picker v-model="form.signupStart" type="date" value-format="YYYY-MM-DD" style="width: 50%" />
          <el-date-picker v-model="form.signupEnd" type="date" value-format="YYYY-MM-DD" style="width: 50%" />
        </div>
      </el-form-item>
      <el-form-item label="比赛起止">
        <div style="display: flex; gap: 8px; width: 100%">
          <el-date-picker v-model="form.contestStart" type="date" value-format="YYYY-MM-DD" style="width: 50%" />
          <el-date-picker v-model="form.contestEnd" type="date" value-format="YYYY-MM-DD" style="width: 50%" />
        </div>
      </el-form-item>
      <el-form-item label="联系人"><el-input v-model="form.contact" placeholder="姓名 + 手机号" /></el-form-item>
      <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
      <el-form-item label="规则覆盖">
        <RulesEditor v-model="form.overrideRules" mode="override" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>
