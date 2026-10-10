<script setup lang="ts">
/**
 * 技能字典审核面板（A 线 D01–D03）
 * 机制：全站技能下拉唯一数据源是 useDict('SKILL')；用户补充项入库为待审核，
 *    管理员在此通过/驳回，操作后 refresh 让全站下拉立即同步
 * 验证点（方案 FS3/PA1）：下拉含用户补充项；审核后状态变更
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useDict } from '@/composables/useDict'
import { apiAddDictItem, apiAuditDictItem } from '@/api/dict'
import { AUDIT_STATUS_LABEL, DICT_SOURCE_LABEL } from '@/config/labels'

const { items, loading, refresh } = useDict('SKILL')

const newLabel = ref('')
const adding = ref(false)
const auditingId = ref('')

/** 状态戳样式映射（固定枚举的样式类，与 labels.ts 文案表配套） */
const STAMP_CLASS: Record<string, string> = { PENDING: 'warn', APPROVED: 'ok', REJECTED: 'bad' }

async function onAdd() {
  const label = newLabel.value.trim()
  if (!label) return
  adding.value = true
  try {
    await apiAddDictItem('SKILL', label)
    ElMessage.success('已提交补充，待审核通过后全站可选')
    newLabel.value = ''
    await refresh()
  } catch {
    // 重复词条等提示已由 http 拦截器弹出
  } finally {
    adding.value = false
  }
}

async function onAudit(id: string, pass: boolean) {
  auditingId.value = id
  try {
    await apiAuditDictItem(id, pass)
    ElMessage.success(pass ? '已通过入库' : '已驳回')
    await refresh()
  } catch {
    // 业务提示已由 http 拦截器弹出
  } finally {
    auditingId.value = ''
  }
}
</script>

<template>
  <div>
    <div class="fbar">
      <input v-model="newLabel" class="input-line" placeholder="补充新技能词条（提交后待审核）" style="width: 260px" @keyup.enter="onAdd" />
      <button class="btn-line" :disabled="adding" @click="onAdd">提交补充</button>
      <span class="fs" style="font-size: 12px; color: var(--vio-ink-3)">学生端在各表单中也可补充；审核通过前不会进入下拉</span>
    </div>
    <table class="ledger">
      <thead>
        <tr><th>词条</th><th>来源</th><th>补充人</th><th>状态</th><th style="width: 140px">操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="it in items" :key="it.id">
          <td>{{ it.label }}</td>
          <td>{{ DICT_SOURCE_LABEL[it.source] }}</td>
          <td>{{ it.createdByName ?? '—' }}</td>
          <td><span class="stamp" :class="STAMP_CLASS[it.auditStatus]">{{ AUDIT_STATUS_LABEL[it.auditStatus] }}</span></td>
          <td>
            <template v-if="it.auditStatus === 'PENDING'">
              <button class="btn-act" :disabled="auditingId === it.id" @click="onAudit(it.id, true)">通过</button>
              <button class="btn-act danger" :disabled="auditingId === it.id" @click="onAudit(it.id, false)">驳回</button>
            </template>
            <span v-else class="fs" style="color: var(--vio-ink-3); font-size: 12px">—</span>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-if="!loading && items.length === 0" class="empty-note">暂无字典项</p>
  </div>
</template>
