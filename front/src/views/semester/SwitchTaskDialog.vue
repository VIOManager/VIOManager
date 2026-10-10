<script setup lang="ts">
/**
 * 发起学期切换弹窗（S05）：目标学期 + 超管密码二次确认
 * 提交后生成 30 分钟待执行窗口任务（窗口内可取消）
 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { apiCreateSwitchTask } from '@/api/semester'
import type { Semester } from '@/api/semester'

const visible = defineModel<boolean>({ required: true })
const props = defineProps<{ semesters: Semester[] }>()
const emit = defineEmits<{ created: [] }>()

const form = reactive({ toSemesterId: '', password: '' })
const submitting = ref(false)

/** 候选目标：非进行中的学期 */
const candidates = computed(() => props.semesters.filter((s) => s.status !== 'ACTIVE'))

watch(visible, (v) => {
  if (v) {
    form.toSemesterId = ''
    form.password = ''
  }
})

async function onSubmit() {
  if (!form.toSemesterId || !form.password) {
    ElMessage.warning('请选择目标学期并输入密码')
    return
  }
  submitting.value = true
  try {
    await apiCreateSwitchTask(form.toSemesterId, form.password)
    ElMessage.success('切换任务已创建，进入 30 分钟待执行窗口')
    visible.value = false
    emit('created')
  } catch {
    // 2002 密码不正确等提示已由拦截器弹出
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="发起学期切换" width="440px">
    <p class="fs" style="font-size: 12.5px; color: var(--vio-ink-3); margin-bottom: 14px">
      切换后旧学期归档、新学期激活，未结束的比赛/赛队/成员关系自动继承。执行前需超管密码二次确认。
    </p>
    <el-form label-width="92px" @submit.prevent>
      <el-form-item label="目标学期" required>
        <el-select v-model="form.toSemesterId" style="width: 100%">
          <el-option v-for="s in candidates" :key="s.id" :value="s.id" :label="s.name" />
        </el-select>
      </el-form-item>
      <el-form-item label="超管密码" required>
        <el-input v-model="form.password" type="password" show-password autocomplete="current-password" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">发起</el-button>
    </template>
  </el-dialog>
</template>
