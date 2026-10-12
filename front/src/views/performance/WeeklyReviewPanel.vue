<script setup lang="ts">
/**
 * 周报审核工作台（绩效管理端面板一，PF06 列表 + PF07 打分 + PF08 驳回）
 * 打分抽屉：维度表单项随该子组织启用规则渲染（8046 未配置拦截）；各维度分不可超满分（8044 前端先挡）
 * 驳回原因必填；打分带幂等键（重复提交 1005）
 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiPerfRules, apiRejectWeekly, apiScoreWeekly, apiWeeklies, apiWeeklyDetail } from '@/api/performance'
import type { PerfRule, WeeklyDetail, WeeklyItem } from '@/api/performance'
import { WEEKLY_STATUS_LABEL } from '@/config/labels'

const list = ref<WeeklyItem[]>([])
const loading = ref(false)
const weekNoFilter = ref('')
const statusFilter = ref('')
const keyword = ref('')

async function load() {
  loading.value = true
  try {
    list.value = await apiWeeklies({
      weekNo: weekNoFilter.value ? Number(weekNoFilter.value) : undefined,
      status: statusFilter.value || undefined,
      memberKeyword: keyword.value || undefined
    })
  } finally {
    loading.value = false
  }
}

// ── 打分抽屉 ──
const drawerVisible = ref(false)
const detail = ref<WeeklyDetail | null>(null)
const rules = ref<PerfRule[]>([])
const scoreForm = ref<Array<{ dimensionKey: string; score: number | null; commentText: string }>>([])
const scoreComment = ref('')
const submitting = ref(false)

async function openScore(id: string) {
  const d = await apiWeeklyDetail(id)
  // 打分维度随该子组织启用规则走（8046 未配置时后端拦截，前端同步提示）
  const all = await apiPerfRules({ subOrgId: d.subOrgId })
  rules.value = all.filter((r) => r.enabled)
  scoreForm.value = rules.value.map((r) => ({ dimensionKey: r.dimensionKey, score: null, commentText: '' }))
  scoreComment.value = ''
  detail.value = d
  drawerVisible.value = true
}

async function submitScore() {
  if (!detail.value || submitting.value) return
  for (let i = 0; i < scoreForm.value.length; i++) {
    const f = scoreForm.value[i]
    const r = rules.value[i]
    if (f.score === null || isNaN(f.score)) {
      ElMessage.warning('请给「' + r.dimensionName + '」打分')
      return
    }
    if (f.score < 0 || f.score > r.maxScore) {
      ElMessage.warning('「' + r.dimensionName + '」得分须在 0–' + r.maxScore + ' 之间')
      return
    }
  }
  try {
    await ElMessageBox.confirm('确认提交打分？提交后按当前权重快照计入学期汇总', '打分确认', { type: 'warning' })
  } catch {
    return
  }
  submitting.value = true
  try {
    await apiScoreWeekly(detail.value.id, {
      scores: scoreForm.value.map((f) => ({ dimensionKey: f.dimensionKey, score: Number(f.score), commentText: f.commentText })),
      scoreComment: scoreComment.value
    })
    ElMessage.success('已打分并计入汇总')
    drawerVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

async function onReject(row: WeeklyItem) {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt('填写驳回原因（将展示给学生）', '驳回确认', {
      type: 'warning',
      inputPlaceholder: '驳回原因（必填）',
      inputValidator: (v: string) => (v && v.trim() ? true : '驳回原因不能为空')
    })
    reason = value.trim()
  } catch {
    return
  }
  await apiRejectWeekly(row.id, reason)
  ElMessage.success('已驳回')
  await load()
}

function stampClass(status: string): string {
  if (status === 'SUBMITTED') return 'warn'
  if (status === 'SCORED') return 'ok'
  return 'bad'
}

onMounted(load)
</script>

<template>
  <div>
    <div class="fbar">
      <input v-model="weekNoFilter" class="input-line" style="width: 110px" placeholder="周次（数字）" @keyup.enter="load" />
      <select v-model="statusFilter" class="input-line" @change="load">
        <option value="">全部状态</option>
        <option v-for="(label, v) in WEEKLY_STATUS_LABEL" :key="v" :value="v">{{ label }}</option>
      </select>
      <input v-model="keyword" class="input-line" style="width: 140px" placeholder="成员姓名" @keyup.enter="load" />
      <button class="btn-line" @click="load">查询</button>
    </div>

    <div class="panel">
      <table class="ledger">
        <thead>
          <tr><th>成员</th><th>子组织</th><th>周次</th><th>状态</th><th>总分</th><th>提交时间</th><th style="width: 140px">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="w in list" :key="w.id">
            <td style="color: var(--vio-ink); font-weight: 600">{{ w.memberName }}</td>
            <td>{{ w.subOrgName }}</td>
            <td class="mono">W{{ w.weekNo }}</td>
            <td><span class="stamp" :class="stampClass(w.status)">{{ WEEKLY_STATUS_LABEL[w.status] }}</span></td>
            <td class="mono">{{ w.totalScore ?? '—' }}</td>
            <td class="mono" style="font-size: 12px">{{ w.submittedAt }}</td>
            <td>
              <template v-if="w.status === 'SUBMITTED'">
                <button class="btn-act" @click="openScore(w.id)">打分</button>
                <button class="btn-act" @click="onReject(w)">驳回</button>
              </template>
              <button v-else class="btn-act" @click="openScore(w.id)">详情</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && list.length === 0" class="empty-note">暂无周报</p>
    </div>

    <el-drawer v-model="drawerVisible" :title="detail ? detail.memberName + ' · W' + detail.weekNo + ' 周报' : '周报'" size="480px">
      <template v-if="detail">
        <p class="wc">{{ detail.workContent }}</p>
        <p v-if="detail.rejectReason" class="reject-note">驳回原因：{{ detail.rejectReason }}</p>

        <template v-if="detail.status === 'SUBMITTED'">
          <div class="zone">
            <h6>按维度打分（快照随单固化，规则后续变更不影响本单）</h6>
            <div v-for="(f, i) in scoreForm" :key="f.dimensionKey" class="score-row">
              <span class="score-name">{{ rules[i].dimensionName }}<i class="mono">权重 {{ rules[i].weight }} · 满分 {{ rules[i].maxScore }}</i></span>
              <input v-model.number="f.score" type="number" class="input-line score-input" :max="rules[i].maxScore" min="0" />
            </div>
            <textarea v-model="scoreComment" class="input-line" rows="2" placeholder="总评（选填）" style="width: 100%; margin-top: 10px"></textarea>
            <button class="btn-ink" style="margin-top: 12px" :disabled="submitting" @click="submitScore">提交打分</button>
          </div>
        </template>
        <template v-else-if="detail.status === 'SCORED'">
          <div class="zone">
            <h6>维度得分（总分 {{ detail.totalScore }}）</h6>
            <div v-for="s in detail.scores" :key="s.dimensionKey" class="score-row">
              <span class="score-name">{{ s.dimensionName }}<i class="mono">权重 {{ s.weight }}</i></span>
              <b class="mono">{{ s.score }} / {{ s.maxScore }}</b>
            </div>
            <p v-if="detail.scoreComment" class="wc" style="margin-top: 10px">总评：{{ detail.scoreComment }}</p>
          </div>
        </template>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.wc { font-size: 13px; line-height: 1.8; color: var(--vio-ink-2); background: var(--vio-wash); padding: 10px 12px; }
.reject-note { font-size: 12.5px; color: var(--vio-danger); }
.zone { margin-top: 18px; padding-top: 12px; border-top: 1px solid var(--vio-rule-soft); }
.zone h6 { font-size: 12px; color: var(--vio-ink-3); margin-bottom: 10px; letter-spacing: 0.08em; }
.score-row { display: flex; align-items: center; justify-content: space-between; padding: 8px 0; border-bottom: 1px dashed var(--vio-rule-soft); }
.score-name { font-size: 13px; color: var(--vio-ink); }
.score-name i { font-style: normal; font-size: 11px; color: var(--vio-ink-3); margin-left: 8px; }
.score-input { width: 90px; text-align: right; }
</style>
