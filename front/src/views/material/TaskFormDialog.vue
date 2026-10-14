<script setup lang="ts">
/**
 * 材料任务新建/编辑弹窗（PC1，MT02/MT03）
 * 新建：选比赛（仅未归档）+ 任务名 + 截止时间；编辑：仅任务名与截止时间可改
 * 后端校验：5001 比赛不存在或该比赛已配置任务
 */
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { apiCreateTask, apiUpdateTask } from '@/api/material'
import type { MaterialTask } from '@/api/material'
import type { ContestListItem } from '@/api/contest'

const props = defineProps<{
  modelValue: boolean
  /** null=新建；有值=编辑 */
  task: MaterialTask | null
  contests: ContestListItem[]
}>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; saved: [] }>()

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => {
  visible.value = v
  if (v) reset()
})
watch(visible, (v) => emit('update:modelValue', v))

const form = reactive({ contestId: '', name: '', deadline: '' })
const saving = ref(false)

function reset() {
  form.contestId = props.task ? props.task.contestId : ''
  form.name = props.task ? props.task.name : ''
  form.deadline = props.task ? props.task.deadline : ''
}

async function onSave() {
  if (!form.contestId && !props.task) {
    ElMessage.warning('请选择比赛')
    return
  }
  if (!form.name.trim() || !form.deadline) {
    ElMessage.warning('任务名与截止时间不能为空')
    return
  }
  saving.value = true
  try {
    if (props.task) {
      await apiUpdateTask(props.task.id, { name: form.name.trim(), deadline: form.deadline })
    } else {
      await apiCreateTask({ contestId: form.contestId, name: form.name.trim(), deadline: form.deadline })
    }
    ElMessage.success(props.task ? '任务已更新' : '任务已创建')
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="task ? '编辑材料任务' : '新建材料任务'" width="480px">
    <el-form label-width="92px" @submit.prevent>
      <el-form-item label="比赛" required>
        <el-select v-if="!task" v-model="form.contestId" placeholder="选择比赛" style="width: 100%">
          <el-option v-for="c in contests" :key="c.id" :value="c.id" :label="c.name" />
        </el-select>
        <span v-else class="fs">{{ task.contestName }}</span>
      </el-form-item>
      <el-form-item label="任务名" required>
        <el-input v-model="form.name" placeholder="如：2026 电设校内选拔赛报名材料" maxlength="50" />
      </el-form-item>
      <el-form-item label="截止时间" required>
        <el-date-picker v-model="form.deadline" type="datetime" value-format="YYYY-MM-DD HH:mm" placeholder="选择截止时间" style="width: 100%" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
    </template>
  </el-dialog>
</template>
