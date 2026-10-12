<script setup lang="ts">
/**
 * 我的排班页（/my/schedule，学生端，SC05）
 * 只看本人班次：周次可选筛选；管理端菜单隐藏本入口（SC05 专供学生）
 */
import { onMounted, ref } from 'vue'
import { apiMySchedules } from '@/api/schedule'
import type { MySchedule } from '@/api/schedule'
import { SLOT_LABEL, WEEKDAY_LABEL } from '@/config/labels'

const list = ref<MySchedule[]>([])
const loading = ref(false)
const weekNo = ref('')

async function load() {
  loading.value = true
  try {
    list.value = await apiMySchedules({ weekNo: weekNo.value ? Number(weekNo.value) : undefined })
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>我的排班</h3>
      <div class="dossier-meta mono">
        <span>学生视角 · 仅本人班次</span>
      </div>
    </div>

    <div class="fbar">
      <input v-model="weekNo" class="input-line" style="width: 110px" placeholder="周次（数字）" @keyup.enter="load" />
      <button class="btn-line" @click="load">查询</button>
      <span class="fs" style="font-size: 12px; color: var(--vio-ink-3)">留空查整学期；排班由部门管理员在周排班表里安排</span>
    </div>

    <div class="panel">
      <table class="ledger">
        <thead>
          <tr><th style="width: 70px">周次</th><th>子组织</th><th>时间</th><th>值班内容</th><th>备注</th></tr>
        </thead>
        <tbody>
          <tr v-for="s in list" :key="s.id">
            <td class="mono" style="font-weight: 700">W{{ s.weekNo }}</td>
            <td>{{ s.subOrgName }}</td>
            <td>{{ WEEKDAY_LABEL[s.weekday] }} {{ SLOT_LABEL[s.slotIndex] }}</td>
            <td style="color: var(--vio-ink); font-weight: 600">{{ s.dutyName }}</td>
            <td>{{ s.remark || '—' }}</td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && list.length === 0" class="empty-note">暂无排班</p>
    </div>
  </div>
</template>
