<script setup lang="ts">
/**
 * 学期新建/编辑弹窗（S02/S03）：名称查重 3008 由后端判定
 */
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { apiCreateSemester, apiUpdateSemester } from '@/api/semester'
import type { Semester } from '@/api/semester'

const visible = defineModel<boolean>({ required: true })
const props = defineProps<{ semester: Semester | null }>()
const emit = defineEmits<{ saved: [] }>()

const form = reactive({ name: '', startDate: '', endDate: '', weekCount: 16 })
const submitting = ref(false)

watch(visible, (v) => {
  if (!v) return
  if (props.semester) {
    form.name = props.semester.name
    form.startDate = props.semester.startDate
    form.endDate = props.semester.endDate
    form.weekCount = props.semester.weekCount
  } else {
    form.name = ''
    form.startDate = ''
    form.endDate = ''
    form.weekCount = 16
  }
})

async function onSubmit() {
  if (!form.name.trim() || !form.startDate || !form.endDate) {
    ElMessage.warning('名称与起止日期必填')
    return
  }
  submitting.value = true
  try {
    if (props.semester) {
      await apiUpdateSemester(props.semester.id, { ...form })
      ElMessage.success('学期已更新')
    } else {
      await apiCreateSemester({ ...form })
      ElMessage.success('学期已创建')
    }
    visible.value = false
    emit('saved')
  } catch {
    // 3008 名称重复等提示已由拦截器弹出
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="semester ? '编辑学期' : '新建学期'" width="440px">
    <el-form label-width="92px" @submit.prevent>
      <el-form-item label="学期名称" required><el-input v-model="form.name" placeholder="如：2027春季学期" /></el-form-item>
      <el-form-item label="开始日期" required><el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
      <el-form-item label="结束日期" required><el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
      <el-form-item label="教学周数"><el-input v-model.number="form.weekCount" type="number" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>
