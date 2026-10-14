<script setup lang="ts">
/**
 * 字典管理面板（SY01–SY10）
 * 左侧类型列表（条目数/待审核数徽标）+ 右侧条目表格（筛选/新增/改名/启停/删除/审核）
 * 铁规则：预设类型不可删（1006）；停用优先于删除；用户补充词条须审核通过才进 DR01
 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiAuditDictItemAdmin, apiCreateDictItem, apiCreateDictType, apiDeleteDictItem, apiDeleteDictType, apiDictAdminItems, apiDictTypes, apiToggleDictItem, apiUpdateDictItem } from '@/api/system'
import type { DictItemAdmin, DictType } from '@/api/system'
import { AUDIT_STATUS_LABEL, DICT_SOURCE_LABEL } from '@/config/labels'

const types = ref<DictType[]>([])
const currentCode = ref('')
const items = ref<DictItemAdmin[]>([])
const auditFilter = ref('')
const keyword = ref('')
const loading = ref(false)

async function loadTypes() {
  types.value = await apiDictTypes()
  if (!currentCode.value && types.value.length > 0) currentCode.value = types.value[0].dictCode
}

async function loadItems() {
  if (!currentCode.value) return
  loading.value = true
  try {
    items.value = await apiDictAdminItems(currentCode.value, {
      auditStatus: auditFilter.value || undefined,
      keyword: keyword.value || undefined
    })
  } finally {
    loading.value = false
  }
}

function pick(code: string) {
  currentCode.value = code
  void loadItems()
}

// ── 类型新建/删除 ──
async function onCreateType() {
  let code = ''
  let name = ''
  try {
    const r1 = await ElMessageBox.prompt('字典编码（大写蛇形，创建后不可改）', '新建字典类型', {
      inputPlaceholder: '如 PROJECT_TAG', inputValidator: (v: string) => (/^[A-Z][A-Z0-9_]*$/.test(v) ? true : '编码须大写蛇形')
    })
    code = r1.value.trim()
    const r2 = await ElMessageBox.prompt('字典名称', '新建字典类型', { inputPlaceholder: '如 项目标签', inputValidator: (v: string) => (v && v.trim() ? true : '名称不能为空') })
    name = r2.value.trim()
  } catch {
    return
  }
  await apiCreateDictType({ dictCode: code, dictName: name })
  ElMessage.success('类型已创建')
  await loadTypes()
  pick(code)
}

async function onDeleteType(t: DictType) {
  try {
    await ElMessageBox.confirm('删除类型「' + t.dictName + '」？须先清空条目（预设类型不可删）', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await apiDeleteDictType(t.id)
  ElMessage.success('已删除')
  currentCode.value = ''
  await loadTypes()
  await loadItems()
}

// ── 条目操作 ──
async function onCreateItem() {
  if (!currentCode.value) return
  let value = ''
  let label = ''
  try {
    const r1 = await ElMessageBox.prompt('条目值（大写蛇形）', '新增条目', { inputPlaceholder: '如 嵌入式开发', inputValidator: (v: string) => (v && v.trim() ? true : '不能为空') })
    value = r1.value.trim()
    const r2 = await ElMessageBox.prompt('显示名', '新增条目', { inputPlaceholder: '如 嵌入式开发', inputValidator: (v: string) => (v && v.trim() ? true : '不能为空') })
    label = r2.value.trim()
  } catch {
    return
  }
  await apiCreateDictItem(currentCode.value, { itemValue: value, itemLabel: label })
  ElMessage.success('条目已新增（直接生效）')
  await loadTypes()
  await loadItems()
}

async function onRename(it: DictItemAdmin) {
  let label = ''
  try {
    const r = await ElMessageBox.prompt('修改显示名', '编辑条目', { inputValue: it.itemLabel, inputValidator: (v: string) => (v && v.trim() ? true : '不能为空') })
    label = r.value.trim()
  } catch {
    return
  }
  await apiUpdateDictItem(it.id, { itemLabel: label })
  await loadItems()
}

async function onToggle(it: DictItemAdmin) {
  await apiToggleDictItem(it.id, it.status !== 'ENABLED')
  ElMessage.success(it.status === 'ENABLED' ? '已停用（业务下拉不再出现）' : '已启用')
  await loadItems()
}

async function onDeleteItem(it: DictItemAdmin) {
  try {
    await ElMessageBox.confirm('删除条目「' + it.itemLabel + '」？历史数据中的该值将原文兜底展示', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await apiDeleteDictItem(it.id)
  await loadTypes()
  await loadItems()
}

async function onAudit(it: DictItemAdmin, pass: boolean) {
  try {
    await ElMessageBox.confirm((pass ? '通过' : '驳回') + '词条「' + it.itemLabel + '」？', '审核确认', { type: 'warning' })
  } catch {
    return
  }
  await apiAuditDictItemAdmin(it.id, pass ? 'APPROVED' : 'REJECTED')
  ElMessage.success(pass ? '已通过（进入业务下拉）' : '已驳回')
  await loadTypes()
  await loadItems()
}

function stampClass(auditStatus: string): string {
  if (auditStatus === 'PENDING') return 'warn'
  if (auditStatus === 'APPROVED') return 'ok'
  return 'bad'
}

onMounted(async () => {
  await loadTypes()
  await loadItems()
})
</script>

<template>
  <div class="dict-grid">
    <div class="panel type-panel">
      <div class="panel-head">
        <h6>字典类型</h6>
        <button class="btn-act" @click="onCreateType">新建</button>
      </div>
      <div v-for="t in types" :key="t.id" class="type-row" :class="{ active: currentCode === t.dictCode }" @click="pick(t.dictCode)">
        <span class="type-name">{{ t.dictName }}<i class="mono">{{ t.dictCode }}</i></span>
        <span class="type-meta mono">
          {{ t.itemCount }} 项<em v-if="t.pendingCount > 0" class="pend">待审 {{ t.pendingCount }}</em>
          <button v-if="!t.preset" class="btn-act" @click.stop="onDeleteType(t)">删</button>
        </span>
      </div>
    </div>

    <div class="panel">
      <div class="fbar" style="margin-bottom: 10px">
        <select v-model="auditFilter" class="input-line" @change="loadItems">
          <option value="">全部审核状态</option>
          <option v-for="(label, v) in AUDIT_STATUS_LABEL" :key="v" :value="v">{{ label }}</option>
        </select>
        <input v-model="keyword" class="input-line" style="width: 140px" placeholder="值 / 显示名" @keyup.enter="loadItems" />
        <button class="btn-line" @click="loadItems">查询</button>
        <span style="flex: 1"></span>
        <button class="btn-ink" @click="onCreateItem">新增条目</button>
      </div>
      <table class="ledger">
        <thead>
          <tr><th>值</th><th>显示名</th><th>来源</th><th>审核</th><th>状态</th><th style="width: 200px">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="it in items" :key="it.id">
            <td class="mono" style="font-size: 12px">{{ it.itemValue }}</td>
            <td style="color: var(--vio-ink); font-weight: 600">{{ it.itemLabel }}<span v-if="it.createdByName" class="mono" style="font-weight: 400; font-size: 11px; color: var(--vio-ink-3)"> by {{ it.createdByName }}</span></td>
            <td>{{ DICT_SOURCE_LABEL[it.source] }}</td>
            <td><span class="stamp" :class="stampClass(it.auditStatus)">{{ AUDIT_STATUS_LABEL[it.auditStatus] }}</span></td>
            <td><span class="stamp" :class="it.status === 'ENABLED' ? 'ok' : 'dim'">{{ it.status === 'ENABLED' ? '启用' : '停用' }}</span></td>
            <td>
              <template v-if="it.auditStatus === 'PENDING'">
                  <button class="btn-act" @click="onAudit(it, true)">通过</button>
                  <button class="btn-act" @click="onAudit(it, false)">驳回</button>
              </template>
              <button class="btn-act" @click="onRename(it)">改名</button>
              <button class="btn-act" @click="onToggle(it)">{{ it.status === 'ENABLED' ? '停用' : '启用' }}</button>
              <button class="btn-act" @click="onDeleteItem(it)">删</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && items.length === 0" class="empty-note">暂无条目</p>
    </div>
  </div>
</template>

<style scoped>
.dict-grid { display: grid; grid-template-columns: 260px 1fr; gap: 14px; align-items: start; }
.panel-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.panel-head h6 { font-size: 12px; color: var(--vio-ink-3); letter-spacing: 0.08em; }
.type-row { padding: 9px 10px; border: 1px solid transparent; cursor: pointer; display: flex; flex-direction: column; gap: 3px; }
.type-row:hover { background: var(--vio-wash); }
.type-row.active { border-color: var(--vio-blue-ink); background: var(--vio-wash); }
.type-name { font-size: 13px; color: var(--vio-ink); font-weight: 600; }
.type-name i { font-style: normal; font-size: 11px; color: var(--vio-ink-3); margin-left: 8px; }
.type-meta { font-size: 11px; color: var(--vio-ink-3); display: flex; align-items: center; gap: 8px; }
.pend { font-style: normal; color: var(--vio-seal); font-weight: 700; }
.stamp.bad { color: var(--vio-danger); border-color: var(--vio-danger); }
.stamp.dim { color: var(--vio-ink-3); border-color: var(--vio-rule); }
</style>
