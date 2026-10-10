<script setup lang="ts">
/**
 * 待处理比赛池面板（P01–P03，PRD §3.5 自动收集）
 * 列表展示来源/抓取内容/解析线索；确认 → 抛给父页打开预填创建弹窗；忽略 → 移除
 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiContestPool, apiIgnorePoolItem } from '@/api/contest'
import type { PoolItem } from '@/api/contest'
import { POOL_STATUS_LABEL } from '@/config/labels'

const emit = defineEmits<{ confirm: [item: PoolItem] }>()

const list = ref<PoolItem[]>([])
const loading = ref(false)
const statusFilter = ref('PENDING')

async function load() {
  loading.value = true
  try {
    list.value = await apiContestPool(statusFilter.value || undefined)
  } finally {
    loading.value = false
  }
}

async function onIgnore(item: PoolItem) {
  try {
    await ElMessageBox.confirm('确认忽略「' + item.title + '」？', '忽略', {
      confirmButtonText: '忽略', cancelButtonText: '取消', type: 'warning'
    })
  } catch {
    return
  }
  await apiIgnorePoolItem(item.id)
  ElMessage.success('已忽略')
  await load()
}

/** 解析线索摘要 */
function clueText(item: PoolItem): string {
  const parts: string[] = []
  if (item.clues.signupTime) parts.push('报名 ' + item.clues.signupTime)
  if (item.clues.memberLimit) parts.push('每队≤' + item.clues.memberLimit + ' 人')
  if (item.clues.organizer) parts.push('主办 ' + item.clues.organizer)
  return parts.join(' · ') || '未解析出线索'
}

onMounted(load)

/** 暴露给父页：确认创建的弹窗保存成功后回刷本列表 */
defineExpose({ load })
</script>

<template>
  <div class="panel">
    <h5 class="panel-h">
      待处理池
      <span class="sub">外部来源由后端任务归集（爬虫/RSS/手工），此处只做确认与忽略</span>
    </h5>
    <div class="fbar">
      <select v-model="statusFilter" class="input-line" @change="load">
        <option value="PENDING">待处理</option>
        <option value="CONFIRMED">已确认</option>
        <option value="IGNORED">已忽略</option>
        <option value="">全部</option>
      </select>
    </div>
    <table class="ledger">
      <thead>
        <tr><th>标题</th><th>来源</th><th>抓取时间</th><th>解析线索</th><th>状态</th><th style="width: 130px">操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="p in list" :key="p.id">
          <td style="color: var(--vio-ink); font-weight: 600; max-width: 260px">{{ p.title }}</td>
          <td class="nw">{{ p.source }}</td>
          <td class="mono nw">{{ p.fetchedAt }}</td>
          <td class="fs" style="font-size: 12px">{{ clueText(p) }}</td>
          <td><span class="stamp" :class="p.status === 'PENDING' ? 'warn' : p.status === 'CONFIRMED' ? 'ok' : 'dim'">{{ POOL_STATUS_LABEL[p.status] }}</span></td>
          <td class="nw">
            <template v-if="p.status === 'PENDING'">
              <button class="btn-act" @click="emit('confirm', p)">确认创建</button>
              <button class="btn-act danger" @click="onIgnore(p)">忽略</button>
            </template>
            <span v-else class="fs" style="color: var(--vio-ink-3); font-size: 12px">—</span>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-if="!loading && list.length === 0" class="empty-note">待处理池为空</p>
  </div>
</template>
