<script setup lang="ts">
/**
 * 系统参数面板（CF01/CF02）
 * 参数由建库种子维护（不提供增删）；editable=0 的行只读（改会 1006）
 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiConfigs, apiUpdateConfig } from '@/api/system'
import type { SysConfig } from '@/api/system'

const list = ref<SysConfig[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = await apiConfigs()
  } finally {
    loading.value = false
  }
}

async function onEdit(c: SysConfig) {
  let value = ''
  try {
    const r = await ElMessageBox.prompt('修改「' + c.configName + '」的值', '修改参数', {
      inputValue: c.configValue,
      inputValidator: (v: string) => (v !== undefined && String(v).trim() ? true : '值不能为空')
    })
    value = String(r.value).trim()
  } catch {
    return
  }
  await apiUpdateConfig(c.configKey, value)
  ElMessage.success('已保存')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="panel">
    <table class="ledger">
      <thead>
        <tr><th>参数键</th><th>名称</th><th>当前值</th><th>说明</th><th style="width: 80px">操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="c in list" :key="c.configKey">
          <td class="mono" style="font-size: 12px">{{ c.configKey }}</td>
          <td style="color: var(--vio-ink); font-weight: 600">{{ c.configName }}</td>
          <td class="mono">{{ c.configValue }}</td>
          <td style="font-size: 12px; color: var(--vio-ink-3)">{{ c.remark }}</td>
          <td>
            <button v-if="c.editable" class="btn-act" @click="onEdit(c)">修改</button>
            <span v-else class="mono" style="font-size: 11px; color: var(--vio-ink-3)">只读</span>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-if="!loading && list.length === 0" class="empty-note">暂无参数</p>
  </div>
</template>
