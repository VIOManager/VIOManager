<script setup lang="ts">
/**
 * 绩效汇总排行（绩效管理端面板二，PF09 + PF10）
 * 按总分降序（后端排好），含子组织内排名与自动标签；导出走 PF10（Excel，mock 回提示）
 */
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiPerfExport, apiPerfSummaries } from '@/api/performance'
import type { SummaryRow } from '@/api/performance'
import { apiOrgTree } from '@/api/org'
import type { OrgNode } from '@/api/org'
import { PERF_TAG_LABEL } from '@/config/labels'

const list = ref<SummaryRow[]>([])
const loading = ref(false)
const keyword = ref('')
const subOrgId = ref('')
const orgs = ref<Array<{ id: string; name: string }>>([])

async function load() {
  loading.value = true
  try {
    list.value = await apiPerfSummaries({
      subOrgId: subOrgId.value || undefined,
      memberKeyword: keyword.value || undefined
    })
  } finally {
    loading.value = false
  }
}

async function onExport() {
  const r = await apiPerfExport({ semesterId: '', subOrgId: subOrgId.value || undefined, groupBy: 'MEMBER' })
  ElMessage.success(r.note)
}

/** 组织树拍平成下拉选项（本部根节点不进绩效口径） */
function flatten(nodes: OrgNode[], out: Array<{ id: string; name: string }>) {
  for (const n of nodes) {
    if (n.orgType !== 'ROOT') out.push({ id: n.id, name: n.name })
    flatten(n.children, out)
  }
}

onMounted(async () => {
  await load()
  const tree = await apiOrgTree()
  const out: Array<{ id: string; name: string }> = []
  flatten(tree, out)
  orgs.value = out
})
</script>

<template>
  <div>
    <div class="fbar">
      <select v-model="subOrgId" class="input-line" @change="load">
        <option value="">全部子组织</option>
        <option v-for="o in orgs" :key="o.id" :value="o.id">{{ o.name }}</option>
      </select>
      <input v-model="keyword" class="input-line" style="width: 140px" placeholder="成员姓名" @keyup.enter="load" />
      <button class="btn-line" @click="load">查询</button>
      <span style="flex: 1"></span>
      <button class="btn-line" @click="onExport">导出汇总（按成员）</button>
    </div>

    <div class="panel">
      <table class="ledger">
        <thead>
          <tr><th style="width: 56px">排名</th><th>成员</th><th>子组织</th><th>已交/已评</th><th>总分</th><th>平均分</th><th>最高周</th><th>标签</th></tr>
        </thead>
        <tbody>
          <tr v-for="s in list" :key="s.memberId">
            <td class="mono" style="font-weight: 700">{{ s.rankInOrg }}</td>
            <td style="color: var(--vio-ink); font-weight: 600">{{ s.memberName }}</td>
            <td>{{ s.subOrgName }}</td>
            <td class="mono">{{ s.submitCount }} / {{ s.scoredCount }}</td>
            <td class="mono" style="font-weight: 700">{{ s.totalScore }}</td>
            <td class="mono">{{ s.avgScore }}</td>
            <td class="mono">{{ s.bestWeekNo ? 'W' + s.bestWeekNo : '—' }}</td>
            <td>
              <span v-for="t in s.tags" :key="t" class="tag">{{ PERF_TAG_LABEL[t] ?? t }}</span>
              <span v-if="s.tags.length === 0" class="dim">—</span>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && list.length === 0" class="empty-note">暂无汇总数据</p>
    </div>
  </div>
</template>

<style scoped>
.tag {
  display: inline-block;
  font-size: 11px;
  border: 1px solid var(--vio-blue-ink);
  color: var(--vio-blue-ink);
  padding: 1px 7px;
  margin-right: 4px;
}
.dim { color: var(--vio-ink-3); }
</style>
