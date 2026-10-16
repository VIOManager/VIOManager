<script setup lang="ts">
/**
 * 我的绩效页（/my/performance，学生端，PF01–PF05）
 * 汇总卡（总分/排名/趋势/标签）+ 周报列表 + 提交（PF02 幂等）与驳回后重提（PF03）
 * 同周只能有一份周报（8041）；驳回不删记录，改内容后原记录重进待审核
 */
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiMyPerfSummary, apiMyWeeklies, apiResubmitWeekly, apiSubmitWeekly, apiWeeklyDetail } from '@/api/performance'
import type { MyPerfSummary, WeeklyDetail, WeeklyItem } from '@/api/performance'
import { PERF_TAG_LABEL, WEEKLY_STATUS_LABEL } from '@/config/labels'
import type { UploadResp } from '@/api/file'
import UploadField from '@/components/UploadField.vue'

const summary = ref<MyPerfSummary | null>(null)
const list = ref<WeeklyItem[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [s, w] = await Promise.all([apiMyPerfSummary(), apiMyWeeklies()])
    summary.value = s
    list.value = w
  } finally {
    loading.value = false
  }
}

// ── 提交 / 重提弹窗 ──
const formVisible = ref(false)
const editingId = ref('') // 空=新提交；有值=驳回后重提
const weekNo = ref<number | null>(null)
const workContent = ref('')
const attachFileId = ref('')
const attachFileName = ref('')
const submitting = ref(false)

function openSubmit() {
  editingId.value = ''
  weekNo.value = null
  workContent.value = ''
  attachFileId.value = ''
  attachFileName.value = ''
  formVisible.value = true
}

async function openResubmit(row: WeeklyItem) {
  const d = await apiWeeklyDetail(row.id)
  editingId.value = row.id
  weekNo.value = d.weekNo
  workContent.value = d.workContent
  attachFileId.value = d.attachFileId
  attachFileName.value = d.attachFileName
  formVisible.value = true
}

function onUploaded(f: UploadResp) {
  attachFileId.value = f.fileId
  attachFileName.value = f.originalName
}

async function submit() {
  if (submitting.value) return
  if (!weekNo.value || weekNo.value < 1) {
    ElMessage.warning('请填写周次')
    return
  }
  if (!workContent.value.trim()) {
    ElMessage.warning('工作内容不能为空')
    return
  }
  submitting.value = true
  try {
    if (editingId.value) {
      await apiResubmitWeekly(editingId.value, { workContent: workContent.value, attachFileId: attachFileId.value || undefined })
      ElMessage.success('已重新提交，待审核')
    } else {
      await apiSubmitWeekly({ weekNo: weekNo.value, workContent: workContent.value, attachFileId: attachFileId.value || undefined })
      ElMessage.success('周报已提交，待审核')
    }
    formVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

// ── 详情抽屉 ──
const drawerVisible = ref(false)
const detail = ref<WeeklyDetail | null>(null)

async function openDetail(id: string) {
  detail.value = await apiWeeklyDetail(id)
  drawerVisible.value = true
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
    <div class="dossier-head">
      <h3>我的绩效</h3>
      <div class="dossier-meta mono">
        <span v-if="summary">{{ summary.semesterName }}</span>
        <span>学生视角 · PF01–PF05</span>
      </div>
    </div>

    <div v-if="summary" class="metric-block">
      <div class="mb-cell">
        <i>学期总分</i>
        <b class="mono">{{ summary.totalScore }}</b>
        <span class="mono">已评 {{ summary.scoredCount }} 周</span>
      </div>
      <div class="mb-cell">
        <i>平均分</i>
        <b class="mono">{{ summary.avgScore }}</b>
        <span class="mono">{{ summary.bestWeekNo ? '最高 W' + summary.bestWeekNo + ' · ' + summary.bestWeekScore : '暂无已评周' }}</span>
      </div>
      <div class="mb-cell">
        <i>子组织排名</i>
        <b class="mono">{{ summary.rankInOrg || '—' }}</b>
        <span class="mono">共 {{ summary.orgMemberCount }} 人参与</span>
      </div>
      <div class="mb-cell" :class="{ warn: summary.trendSlope < 0 }">
        <i>近 4 周趋势</i>
        <b class="mono">{{ summary.trendSlope > 0 ? '+' : '' }}{{ summary.trendSlope }}</b>
        <span class="mono">{{ summary.trendSlope > 0 ? '活跃度上升' : summary.trendSlope < 0 ? '活跃度下降' : '持平' }}</span>
      </div>
      <div class="mb-cell">
        <i>标签</i>
        <b style="font-size: 15px; font-family: var(--vio-body)">
          <span v-for="t in summary.tags" :key="t" class="tag">{{ PERF_TAG_LABEL[t] ?? t }}</span>
          <span v-if="summary.tags.length === 0" style="color: var(--vio-ink-3)">—</span>
        </b>
        <span class="mono">打分后自动生成</span>
      </div>
    </div>

    <div class="fbar">
      <button class="btn-ink" @click="openSubmit">提交本周周报</button>
      <span class="fs" style="font-size: 12px; color: var(--vio-ink-3)">每周一份；被驳回的周报修改后可重新提交</span>
    </div>

    <div class="panel">
      <table class="ledger">
        <thead>
          <tr><th style="width: 70px">周次</th><th>状态</th><th>总分</th><th>提交时间</th><th style="width: 150px">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="w in list" :key="w.id">
            <td class="mono" style="font-weight: 700">W{{ w.weekNo }}</td>
            <td><span class="stamp" :class="stampClass(w.status)">{{ WEEKLY_STATUS_LABEL[w.status] }}</span></td>
            <td class="mono">{{ w.totalScore ?? '—' }}</td>
            <td class="mono" style="font-size: 12px">{{ w.submittedAt }}</td>
            <td>
              <button class="btn-act" @click="openDetail(w.id)">详情</button>
              <button v-if="w.status === 'REJECTED'" class="btn-act" @click="openResubmit(w)">修改重提</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && list.length === 0" class="empty-note">还没有周报，点上方按钮提交第一周</p>
    </div>

    <el-dialog v-model="formVisible" :title="editingId ? '修改并重新提交（W' + weekNo + '）' : '提交周报'" width="520px">
      <div class="form-col">
        <label>周次（教学周）</label>
        <input v-model.number="weekNo" type="number" min="1" max="18" class="input-line" :disabled="!!editingId" placeholder="如 6" />
        <label>本周工作内容</label>
        <textarea v-model="workContent" class="input-line" rows="5" placeholder="做了什么、产出是什么（驳回常见原因：内容过简）"></textarea>
        <label>附件（选填）</label>
        <UploadField biz-type="PERFORMANCE_WEEKLY" accept=".pdf,.docx,.zip,.jpg,.png" :max-mb="20" :button-text="attachFileName ? '已选：' + attachFileName : '上传附件'" @uploaded="onUploaded" />
      </div>
      <template #footer>
        <button class="btn-line" @click="formVisible = false">取消</button>
        <button class="btn-ink" :disabled="submitting" @click="submit">{{ editingId ? '重新提交' : '提交' }}</button>
      </template>
    </el-dialog>

    <el-drawer v-model="drawerVisible" :title="detail ? 'W' + detail.weekNo + ' 周报详情' : '周报详情'" size="440px">
      <template v-if="detail">
        <span class="stamp" :class="stampClass(detail.status)">{{ WEEKLY_STATUS_LABEL[detail.status] }}</span>
        <p class="wc">{{ detail.workContent }}</p>
        <p v-if="detail.attachFileName" class="mono" style="font-size: 12px">附件：{{ detail.attachFileName }}</p>
        <p v-if="detail.rejectReason" class="reject-note">驳回原因：{{ detail.rejectReason }}</p>
        <div v-if="detail.status === 'SCORED'" class="zone">
          <h6>维度得分（总分 {{ detail.totalScore }}）</h6>
          <div v-for="s in detail.scores" :key="s.dimensionKey" class="score-row">
            <span>{{ s.dimensionName }}<i class="mono">权重 {{ s.weight }}</i></span>
            <b class="mono">{{ s.score }} / {{ s.maxScore }}</b>
          </div>
          <p v-if="detail.scoreComment" class="wc" style="margin-top: 10px">总评：{{ detail.scoreComment }}</p>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
/* 指标贴纸带（与概览页 S2 版式同款：五格贴纸交替微倾 + 角标黄三角） */
.metric-block { display: grid; grid-template-columns: repeat(5, 1fr); gap: 14px; margin: 22px 0 20px; }
.mb-cell {
  background: var(--vio2-panel);
  border: 3px solid var(--vio2-bd);
  box-shadow: 5px 5px 0 var(--vio2-shadow);
  padding: 12px 15px 11px;
  position: relative;
  overflow: hidden;
}
.mb-cell::after {
  content: "";
  position: absolute;
  right: -18px;
  top: -18px;
  width: 38px;
  height: 38px;
  transform: rotate(45deg);
  background: var(--vio2-yellow);
  border-left: 3px solid var(--vio2-bd);
  border-bottom: 3px solid var(--vio2-bd);
}
.mb-cell:nth-child(odd) { transform: rotate(-0.5deg); }
.mb-cell:nth-child(even) { transform: rotate(0.5deg); }
.mb-cell i { display: block; font-style: normal; font-family: var(--vio-mono); font-size: 10px; font-weight: 700; letter-spacing: 0.16em; color: var(--vio2-ink-3); }
.mb-cell b { display: block; font-family: var(--vio2-din); font-size: 30px; font-weight: 900; margin: 2px 0 1px; color: var(--vio2-ink); }
.mb-cell span { font-size: 10.5px; color: var(--vio2-ink-3); }
.mb-cell.warn { background: var(--vio2-pink); }
.mb-cell.warn i, .mb-cell.warn span { color: #101010; }
.mb-cell.warn b { color: #101010; }
.mb-cell.warn::after { background: var(--vio2-panel); }
.tag { display: inline-block; font-family: var(--vio-mono); font-size: 10.5px; font-weight: 700; border: 2px solid var(--vio2-bd); background: var(--vio2-lilac); color: #101010; padding: 0 7px 1px; margin-right: 4px; }
.form-col { display: flex; flex-direction: column; gap: 6px; }
.form-col label { font-size: 12px; color: var(--vio2-ink-2); margin-top: 6px; }
.wc { font-size: 13px; line-height: 1.8; color: var(--vio2-ink-2); background: var(--vio2-panel-2); border: 2px solid var(--vio2-bd); padding: 10px 12px; margin-top: 12px; }
.reject-note { font-size: 12.5px; color: var(--vio2-pink); font-weight: 700; }
.zone { margin-top: 18px; padding-top: 12px; border-top: 3px solid var(--vio2-bd); }
.zone h6 { font-family: var(--vio2-din); font-size: 13px; font-weight: 900; color: var(--vio2-ink); margin-bottom: 10px; letter-spacing: 0.06em; }
.score-row { display: flex; align-items: center; justify-content: space-between; padding: 8px 0; border-bottom: 2px solid var(--vio2-row-line); font-size: 13px; }
.score-row i { font-style: normal; font-size: 11px; color: var(--vio2-ink-3); margin-left: 8px; }
</style>
