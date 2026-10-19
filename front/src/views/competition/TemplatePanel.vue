<script setup lang="ts">
/**
 * 模板管理面板（T01–T05）：默认规则编辑（六键全录）+ 被引用拒删（4012）
 * 规则变更不影响已创建比赛（创建时快照合并，清单 §2 T04）
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiCreateTemplate, apiDeleteTemplate, apiTemplates, apiUpdateTemplate } from '@/api/contest'
import type { ContestTemplate, RulesMap } from '@/api/contest'
import { RULE_KEY_LABEL, ruleValueText } from '@/config/labels'
import RulesEditor from '@/components/RulesEditor.vue'

const list = ref<ContestTemplate[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const editing = ref<ContestTemplate | null>(null)
const form = reactive({ name: '', code: '', category: '', description: '', defaultRules: {} as RulesMap })
const submitting = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = await apiTemplates()
  } finally {
    loading.value = false
  }
}

/** §0 六键默认值：新建预填；编辑无规则模板时兜底（种子/接口创建的模板 default_rules 可为 null） */
const DEFAULT_RULES: RulesMap = { memberLimit: 3, prefillLimit: 1, allowRepeatJoin: false, crossTeamLeader: false, maxAdvisorCount: 2, crossTrackLimit: true }

function openCreate() {
  editing.value = null
  Object.assign(form, {
    name: '', code: '', category: '', description: '',
    defaultRules: { ...DEFAULT_RULES }
  })
  dialogVisible.value = true
}

function openEdit(t: ContestTemplate) {
  editing.value = t
  Object.assign(form, { name: t.name, code: t.code, category: t.category, description: t.description, defaultRules: t.defaultRules ? { ...t.defaultRules } : { ...DEFAULT_RULES } })
  dialogVisible.value = true
}

async function onSubmit() {
  if (!form.name.trim() || !form.code.trim()) {
    ElMessage.warning('名称与编码必填')
    return
  }
  submitting.value = true
  try {
    if (editing.value) {
      await apiUpdateTemplate(editing.value.id, { ...form })
      ElMessage.success('模板已更新（不影响已创建比赛）')
    } else {
      await apiCreateTemplate({ ...form })
      ElMessage.success('模板已创建')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 提示已由拦截器弹出
  } finally {
    submitting.value = false
  }
}

async function onDelete(t: ContestTemplate) {
  try {
    await ElMessageBox.confirm('确认删除模板「' + t.name + '」？', '删除模板', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch {
    return
  }
  try {
    await apiDeleteTemplate(t.id)
    ElMessage.success('已删除')
    await load()
  } catch {
    // 4012 被引用拒删提示已由拦截器弹出
  }
}

onMounted(load)
</script>

<template>
  <div class="panel">
    <h5 class="panel-h">比赛模板 <button class="btn-ink" style="font-size: 12px; padding: 5px 14px" @click="openCreate">新建模板</button></h5>
    <table class="ledger">
      <thead>
        <tr><th>名称</th><th>编码</th><th>类别</th><th>默认规则摘要</th><th class="num">被引用</th><th style="width: 110px">操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="t in list" :key="t.id">
          <td style="color: var(--vio-ink); font-weight: 600">{{ t.name }}</td>
          <td class="mono">{{ t.code }}</td>
          <td>{{ t.category }}</td>
          <!-- defaultRules 可为 null（种子数据未插该列）：裸取属性会抛 TypeError 中断渲染，
               整个面板变"僵尸"——列表空、无空态提示、新建/编辑点了没反应（2026-10-19 修复） -->
          <td class="fs" style="font-size: 12px">
            <template v-if="t.defaultRules">
              {{ RULE_KEY_LABEL.memberLimit }} {{ ruleValueText(t.defaultRules.memberLimit ?? '—') }} ·
              {{ RULE_KEY_LABEL.allowRepeatJoin }} {{ ruleValueText(t.defaultRules.allowRepeatJoin ?? false) }}
            </template>
            <span v-else>—</span>
          </td>
          <td class="num mono">{{ t.refCount }}</td>
          <td class="nw">
            <button class="btn-act" @click="openEdit(t)">编辑</button>
            <button class="btn-act danger" @click="onDelete(t)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-if="!loading && list.length === 0" class="empty-note">暂无模板</p>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑模板' : '新建模板'" width="620px">
      <el-form label-width="92px" @submit.prevent>
        <el-form-item label="模板名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="编码" required><el-input v-model="form.code" placeholder="如 EDC" /></el-form-item>
        <el-form-item label="类别"><el-input v-model="form.category" placeholder="如 教育部A类" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="默认规则">
          <RulesEditor v-model="form.defaultRules" mode="full" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
