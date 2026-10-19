<script setup lang="ts">
/**
 * 材料要求新建/编辑弹窗（PC1，MR01/MR02）
 * 12 种预设类型（labels.ts 唯一来源）；类型切换时格式白名单带出默认值、名称空时带出类型中文名
 * 校验：名称必填、类型必选、格式至少一种、大小 1–200MB（前端提示 + 后端 1001 兜底）
 */
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { apiCreateRequirement, apiUpdateRequirement } from '@/api/material'
import type { MaterialRequirement } from '@/api/material'
import { MATERIAL_TYPES } from '@/config/labels'

const props = defineProps<{
  modelValue: boolean
  taskId: string
  /** null=新建；有值=编辑 */
  requirement: MaterialRequirement | null
}>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; saved: [] }>()

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => {
  visible.value = v
  if (v) reset()
})
watch(visible, (v) => emit('update:modelValue', v))

/** 常见格式白名单（要求表单可选项） */
const FORMAT_OPTIONS = ['pdf', 'doc', 'docx', 'jpg', 'png', 'pptx', 'xlsx', 'zip', 'rar']

const form = reactive({ materialType: '', name: '', requiredFlag: 1, allowedFormats: [] as string[], namingRule: '', maxSizeMb: 10 })
const saving = ref(false)

function reset() {
  form.materialType = props.requirement ? props.requirement.materialType : ''
  form.name = props.requirement ? props.requirement.name : ''
  form.requiredFlag = props.requirement ? props.requirement.requiredFlag : 1
  form.allowedFormats = props.requirement ? props.requirement.allowedFormats.slice() : []
  form.namingRule = props.requirement ? props.requirement.namingRule : ''
  form.maxSizeMb = props.requirement ? props.requirement.maxSizeMb : 10
}

/** 类型切换：未手动改过格式时带出该类型默认格式；名称为空时带出类型中文名（可改） */
function onTypeChange(v: string) {
  for (const t of MATERIAL_TYPES) {
    if (t.value === v) {
      form.allowedFormats = t.defaultFormats.slice()
      if (!form.name.trim()) form.name = t.label
    }
  }
}

async function onSave() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写要求名称')
    return
  }
  if (!form.materialType) {
    ElMessage.warning('请选择材料类型')
    return
  }
  if (form.allowedFormats.length === 0) {
    ElMessage.warning('至少允许一种文件格式')
    return
  }
  if (!form.maxSizeMb || form.maxSizeMb < 1 || form.maxSizeMb > 200) {
    ElMessage.warning('单文件大小限制须在 1–200MB 之间')
    return
  }
  saving.value = true
  try {
    const body = { materialType: form.materialType, name: form.name.trim(), requiredFlag: form.requiredFlag, allowedFormats: form.allowedFormats, namingRule: form.namingRule.trim(), maxSizeMb: form.maxSizeMb }
    if (props.requirement) {
      await apiUpdateRequirement(props.requirement.id, body)
    } else {
      await apiCreateRequirement(props.taskId, body)
    }
    ElMessage.success(props.requirement ? '要求已更新' : '要求已添加')
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="requirement ? '编辑材料要求' : '添加材料要求'" width="520px">
    <el-form label-width="92px" @submit.prevent>
      <el-form-item label="要求名称" required>
        <el-input v-model="form.name" placeholder="如：报名表PDF版（选类型后自动带出，可改）" maxlength="50" />
      </el-form-item>
      <el-form-item label="材料类型" required>
        <el-select v-model="form.materialType" placeholder="12 种预设类型" style="width: 100%" @change="onTypeChange">
          <el-option v-for="t in MATERIAL_TYPES" :key="t.value" :value="t.value" :label="t.label" />
        </el-select>
      </el-form-item>
      <el-form-item label="是否必传">
        <el-switch v-model="form.requiredFlag" :active-value="1" :inactive-value="0" active-text="必传" inactive-text="选传" />
      </el-form-item>
      <el-form-item label="允许格式" required>
        <el-select v-model="form.allowedFormats" multiple placeholder="至少一种" style="width: 100%">
          <el-option v-for="f in FORMAT_OPTIONS" :key="f" :value="f" :label="f" />
        </el-select>
      </el-form-item>
      <el-form-item label="命名规则">
        <el-input v-model="form.namingRule" placeholder="选填，如：队名-报名表" maxlength="30" />
      </el-form-item>
      <el-form-item label="大小上限">
        <el-input-number v-model="form.maxSizeMb" :min="1" :max="200" :step="1" /> <span class="fs" style="margin-left: 8px">MB</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
    </template>
  </el-dialog>
</template>
