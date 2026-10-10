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

const form = reactive({ parentId: '', name: '', leaderName: '', sortNo: 99 })
const submitting = ref(false)

// 打开弹窗时按模式回填
watch(visible, (v) => {
  if (!v) return
  if (props.mode === 'edit' && props.node) {
    form.parentId = ''
    form.name = props.node.name
    form.leaderName = props.node.leaderName
    form.sortNo = props.node.sortNo
  } else {
    form.parentId = props.parent?.id ?? ''
    form.name = ''
    form.leaderName = ''
    form.sortNo = 99
  }
})

async function onSubmit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写组织名称')
    return
  }
  submitting.value = true
  try {
    if (props.mode === 'create') {
      await apiCreateOrg({ parentId: form.parentId, name: form.name.trim(), leaderName: form.leaderName.trim(), sortNo: form.sortNo })
      ElMessage.success('组织已创建')
    } else if (props.node) {
      await apiUpdateOrg(props.node.id, { parentId: form.parentId, name: form.name.trim(), leaderName: form.leaderName.trim(), sortNo: form.sortNo })
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
        <el-select v-model="form.parentId" placeholder="不改上级请留空" clearable style="width: 100%">
          <el-option v-for="o in orgOptions" :key="o.id" :value="o.id" :label="'　'.repeat(o.level - 1) + o.name" />
        </el-select>
      </el-form-item>
      <el-form-item label="组织名称">
        <el-input v-model="form.name" placeholder="如：数媒协会" />
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
