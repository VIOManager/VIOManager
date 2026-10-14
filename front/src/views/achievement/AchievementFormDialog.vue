<script setup lang="ts">
/**
 * 成果提交/录入弹窗（PC3，A02/A03）
 * 管理端录入：选任意队伍，保存直接生效（APPROVED + §5 联动）
 * 学生队长提交：队伍限定为本人任队长的队伍，保存进审批中心（PENDING）
 */
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { apiCreateAchievement } from '@/api/achievement'
import type { AchievementType } from '@/api/achievement'
import UploadField from '@/components/UploadField.vue'
import { ACHIEVEMENT_TYPE_LABEL } from '@/config/labels'

const props = defineProps<{
  modelValue: boolean
  /** admin=管理端录入（可选全部队伍）；leader=队长提交（teams 已过滤为本人任队长的队伍） */
  mode: 'admin' | 'leader'
  teams: Array<{ teamId: string; teamName: string }>
}>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; saved: [status: string] }>()

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => {
  visible.value = v
  if (v) reset()
})
watch(visible, (v) => emit('update:modelValue', v))

const form = reactive({
  teamId: '', type: 'AWARD' as AchievementType, name: '', level: '', achievedAt: '',
  description: '', evidenceFileId: '', evidenceFileName: ''
})
const saving = ref(false)

function reset() {
  form.teamId = props.teams.length === 1 ? props.teams[0].teamId : ''
  form.type = 'AWARD'
  form.name = ''
  form.level = ''
  form.achievedAt = ''
  form.description = ''
  form.evidenceFileId = ''
  form.evidenceFileName = ''
}

/** 佐证单文件覆盖式上传（先传文件服务拿 fileId，提交成果时一并带上） */
function onEvidence(file: { fileId: string; originalName: string }) {
  form.evidenceFileId = file.fileId
  form.evidenceFileName = file.originalName
}

async function onSave() {
  if (!form.teamId) {
    ElMessage.warning('请选择队伍')
    return
  }
  if (!form.name.trim() || !form.level.trim() || !form.achievedAt) {
    ElMessage.warning('成果名称、级别与时间不能为空')
    return
  }
  saving.value = true
  try {
    const r = await apiCreateAchievement({
      teamId: form.teamId, type: form.type, name: form.name.trim(), level: form.level.trim(),
      achievedAt: form.achievedAt, description: form.description.trim(),
      evidenceFileId: form.evidenceFileId || undefined, evidenceFileName: form.evidenceFileName || undefined
    })
    ElMessage.success(r.status === 'APPROVED' ? '成果已录入生效（已计入材料统计）' : '已提交，等待管理员审批（审批中心可见）')
    visible.value = false
    emit('saved', r.status)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="mode === 'admin' ? '录入成果（直接生效）' : '提交成果（需审批）'" width="560px">
    <el-form label-width="92px" @submit.prevent>
      <el-form-item label="队伍" required>
        <el-select v-model="form.teamId" placeholder="选择队伍" style="width: 100%">
          <el-option v-for="t in props.teams" :key="t.teamId" :value="t.teamId" :label="t.teamName" />
        </el-select>
      </el-form-item>
      <el-form-item label="成果类型" required>
        <el-select v-model="form.type" style="width: 100%">
          <el-option v-for="(label, value) in ACHIEVEMENT_TYPE_LABEL" :key="value" :value="value" :label="label" />
        </el-select>
      </el-form-item>
      <el-form-item label="成果名称" required>
        <el-input v-model="form.name" placeholder="作品 / 项目名" maxlength="50" />
      </el-form-item>
      <el-form-item label="级别" required>
        <el-input v-model="form.level" placeholder="如：省级一等奖 / 校级优秀 / 软件著作权" maxlength="30" />
      </el-form-item>
      <el-form-item label="获奖时间" required>
        <el-date-picker v-model="form.achievedAt" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
      </el-form-item>
      <el-form-item label="成果描述">
        <el-input v-model="form.description" type="textarea" :rows="3" placeholder="选填" maxlength="300" />
      </el-form-item>
      <el-form-item label="佐证材料">
        <UploadField biz-type="ACHIEVEMENT_EVIDENCE" accept=".pdf,.jpg,.png,.zip" :max-mb="20" button-text="上传佐证" tip="选填，论文/专利/作品均可；审批通过后还须在「我的材料」补交佐证必交项" @uploaded="onEvidence" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
    </template>
  </el-dialog>
</template>
