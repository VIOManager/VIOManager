<script setup lang="ts">
/**
 * 绩效汇总排行（绩效管理端面板二，PF09 + PF10）
 * 按总分降序（后端排好），含子组织内排名与自动标签；导出走 PF10（Excel，mock 回提示）
 */
import { onMounted, computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiPerfExport, apiPerfSummaries } from '@/api/performance'
import type { SummaryRow } from '@/api/performance'
import { apiOrgTreeSafe } from '@/api/org'
import type { OrgNode } from '@/api/org'
import { PERF_TAG_LABEL } from '@/config/labels'
import ScrollLoader from '@/components/ScrollLoader.vue'

const list = ref<SummaryRow[]>([])
/** 本地滚动分页：PF09 返回全量数组，前端切片显示（每批 20 条）；排名是后端算好的字段不受切片影响 */
const loadedCount = ref(20)
const visible = computed(() => list.value.slice(0, loadedCount.value))
const noMore = computed(() => loadedCount.value >= list.value.length)
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
    loadedCount.value = 20
  } finally {
    loading.value = false
  }
}

/** 滚动到底扩大切片（ScrollLoader 的 reach 回调） */
function loadMore() {
  if (loading.value || noMore.value) return
  loadedCount.value += 20
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
  const tree = await apiOrgTreeSafe()
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
          <tr v-for="s in visible" :key="s.memberId">
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
      <ScrollLoader :loading="loading" :no-more="noMore" :loaded="visible.length" :total="list.length" @reach="loadMore" />
    </div>
  </div>
</template>

<style scoped>
.tag {
  display: inline-block;
  font-family: var(--vio-mono);
  font-size: 10.5px;
  font-weight: 700;
  border: 2px solid var(--vio2-bd);
  background: var(--vio2-lilac);
  color: #101010;
  padding: 0 7px 1px;
  margin-right: 4px;
}
.dim { color: var(--vio2-ink-3); }
</style>
