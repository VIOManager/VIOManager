<script setup lang="ts">
/**
 * 赛道添加/编辑弹窗（C07/C08）：赛道级规则覆盖走 RulesEditor override 模式
 */
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { apiCreateTrack, apiUpdateTrack } from '@/api/contest'
import type { ContestTrack, RulesMap } from '@/api/contest'
import RulesEditor from '@/components/RulesEditor.vue'

const visible = defineModel<boolean>({ required: true })
const props = defineProps<{ contestId: string; track: ContestTrack | null }>()
const emit = defineEmits<{ saved: [] }>()

const form = reactive({ name: '', code: '', description: '', overrideRules: {} as RulesMap })
const submitting = ref(false)

watch(visible, (v) => {
  if (!v) return
  if (props.track) {
    Object.assign(form, { name: props.track.name, code: props.track.code, description: props.track.description, overrideRules: { ...props.track.overrideRules } })
  } else {
    Object.assign(form, { name: '', code: '', description: '', overrideRules: {} })
  }
})

async function onSubmit() {
  if (!form.name.trim()) {
    ElMessage.warning('赛道名称必填')
    return
  }
  submitting.value = true
  try {
    if (props.track) {
      await apiUpdateTrack(props.contestId, props.track.id, { ...form })
      ElMessage.success('赛道已更新')
    } else {
      await apiCreateTrack(props.contestId, { ...form })
      ElMessage.success('赛道已添加')
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
  <el-dialog v-model="visible" :title="track ? '编辑赛道' : '添加赛道'" width="560px" append-to-body>
    <el-form label-width="86px" @submit.prevent>
      <el-form-item label="赛道名称" required><el-input v-model="form.name" placeholder="如：硬件赛道" /></el-form-item>
      <el-form-item label="编码"><el-input v-model="form.code" placeholder="如 HW" /></el-form-item>
      <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
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
