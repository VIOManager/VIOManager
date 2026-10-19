<script setup lang="ts">
/**
 * 组织新建/编辑弹窗（O02/O03）
 * 编辑时允许改上级；选自身子孙会被后端 3006 拒绝（提示由 http 拦截器弹出）
 * TODO(联调)：负责人当前手填姓名，人员选择器落地后改选人
 */
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { apiCreateOrg, apiUpdateOrg } from '@/api/org'
import type { OrgNode } from '@/api/org'
import { ORG_TYPE_LABEL } from '@/config/labels'

const visible = defineModel<boolean>({ required: true })
const props = defineProps<{
  mode: 'create' | 'edit'
  /** 新建时的上级组织 */
  parent: OrgNode | null
  /** 编辑时的当前节点 */
  node: OrgNode | null
  /** 编辑换上级用的扁平组织列表（不含自身） */
  orgOptions: { id: string; name: string; level: number }[]
}>()
const emit = defineEmits<{ saved: [] }>()

/** 2026-10-17 联调对齐：orgType 后端必填；编辑时 parentId 回填当前上级（后端要求必传 Long，留空会 1001） */
const form = reactive({ parentId: '', name: '', orgType: 'SUB_ORG', leaderName: '', sortNo: 99 })
const submitting = ref(false)

// 组织类型下拉选项（翻译表统一维护，弹窗不硬编码）
const orgTypeOptions = Object.keys(ORG_TYPE_LABEL).filter(k => k !== 'ROOT')

// 打开弹窗时按模式回填
watch(visible, (v) => {
  if (!v) return
  if (props.mode === 'edit' && props.node) {
    form.parentId = props.node.parentId ?? ''
    form.name = props.node.name
    form.orgType = props.node.orgType
    form.leaderName = props.node.leaderName
    form.sortNo = props.node.sortNo
  } else {
    form.parentId = props.parent?.id ?? ''
    form.name = ''
    form.orgType = 'SUB_ORG'
    form.leaderName = ''
    form.sortNo = 99
  }
})

async function onSubmit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写组织名称')
    return
  }
  if (!form.parentId) {
    ElMessage.warning('请选择上级组织')
    return
  }
  submitting.value = true
  try {
    const body = {
      parentId: form.parentId,
      name: form.name.trim(),
      orgType: form.orgType,
      leaderName: form.leaderName.trim(),
      sortNo: form.sortNo
    }
    if (props.mode === 'create') {
      await apiCreateOrg(body)
      ElMessage.success('组织已创建')
    } else if (props.node) {
      await apiUpdateOrg(props.node.id, body)
      ElMessage.success('组织已更新')
    }
    visible.value = false
    emit('saved')
  } catch {
    // 3006 防循环等业务提示已由 http 拦截器弹出
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="mode === 'create' ? '新建子组织' : '编辑组织'" width="440px">
    <el-form label-width="86px" @submit.prevent>
      <el-form-item v-if="mode === 'create'" label="上级组织">
        <span>{{ parent ? parent.name : '（顶级）' }}</span>
      </el-form-item>
      <el-form-item v-else label="上级组织">
        <el-select v-model="form.parentId" placeholder="选择上级组织" style="width: 100%">
          <el-option v-for="o in orgOptions" :key="o.id" :value="o.id" :label="'　'.repeat(o.level - 1) + o.name" />
        </el-select>
      </el-form-item>
      <el-form-item label="组织名称">
        <el-input v-model="form.name" placeholder="如：数媒协会" />
      </el-form-item>
      <el-form-item label="组织类型">
        <el-select v-model="form.orgType" style="width: 100%">
          <el-option v-for="t in orgTypeOptions" :key="t" :value="t" :label="ORG_TYPE_LABEL[t]" />
        </el-select>
      </el-form-item>
      <el-form-item label="负责人">
        <el-input v-model="form.leaderName" placeholder="负责人姓名" />
      </el-form-item>
      <el-form-item label="排序号">
        <el-input v-model.number="form.sortNo" type="number" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>
