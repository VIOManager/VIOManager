<script setup lang="ts">
/**
 * 概览统计页（三端共用壳，数据范围由后端按角色裁剪 —— PRD §3.16）
 * 对应方案：Step 2 大框阶段先落地版式；视觉样张：frame.html F1
 * 学生端（/my/overview）：指标带五格全部接真（聚合 我的赛队/成果/材料/审批红点，零新增契约）
 * 管理端（/overview）：指标带与部门数据卡仍为静态演示（全平台/部门统计接口未出，骨架遗留），
 *    待办审批卡已接审批中心 P01；学期刻度尺三端接真（S01 列表），异常时静态兜底
 * ⚠️ 注意：PRD §3.16 要求"HUD 风格"，定调为图纸档案后以"图签指标带 + 学期刻度尺"表达，
 *    若 lzh 改选 HUD 特例皮肤，本页单独换皮，不影响全站
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { useApprovalStore } from '@/stores/approval'
import { apiApprovalList } from '@/api/approval'
import { apiTeams } from '@/api/team'
import { apiMyAchievements } from '@/api/achievement'
import { apiMyMaterials } from '@/api/material'
import { apiSemestersSafe } from '@/api/semester'
import { APPROVAL_TYPE_LABEL } from '@/config/labels'

const user = useUserStore()
const approval = useApprovalStore()

// ── 学生端指标带：聚合我的赛队 / 我的成果 / 我的材料 / 审批红点 ──
const stu = ref({ teams: 0, teamNames: '暂无队伍', ach: 0, achPending: 0, matMissing: 0, matTeams: 0 })

async function loadStudent() {
  if (user.role !== 'STUDENT') return
  const [teams, ach, mats] = await Promise.all([apiTeams({}), apiMyAchievements(), apiMyMaterials()])
  const names: string[] = []
  for (const t of teams) names.push(t.name)
  let achPending = 0
  for (const a of ach.items) {
    if (a.status === 'PENDING') achPending++
  }
  let matMissing = 0
  let matTeams = 0
  for (const m of mats) {
    if (m.requiredMissing > 0) {
      matMissing += m.requiredMissing
      matTeams++
    }
  }
  stu.value = {
    teams: teams.length,
    teamNames: names.join(' · ') || '暂无队伍',
    ach: ach.items.length,
    achPending,
    matMissing,
    matTeams
  }
}

/** 三端指标位：学生=真实聚合；管理端=静态演示（统计接口未出）——数字口径刻意分角色，演示权限差异 */
const metrics = computed(() => {
  if (user.role === 'STUDENT') {
    return [
      { label: '我的队伍', value: String(stu.value.teams), sub: stu.value.teamNames },
      { label: '我的成果', value: String(stu.value.ach), sub: '待审批 ' + stu.value.achPending },
      { label: '待我处理', value: String(approval.todoCount), sub: '入队邀请 · 待确认', warn: approval.todoCount > 0 },
      { label: '材料待补', value: String(stu.value.matMissing), sub: stu.value.matTeams + ' 支队伍待补', warn: stu.value.matMissing > 0 },
      { label: '申请结果', value: String(approval.resultCount), sub: '审批中心未读' }
    ]
  }
  if (user.role === 'DEPT_ADMIN') {
    return [
      { label: '在册成员', value: '107', sub: '数媒协会系 · 本月 +3' },
      { label: '进行中比赛', value: '8', sub: '报名中 3' },
      { label: '活跃赛队', value: '30', sub: '本月 +4' },
      { label: '待审批', value: '5', sub: '需今日处理 2', warn: true },
      { label: '材料待补', value: '2', sub: '截止 W8 周五', warn: true }
    ]
  }
  return [
    { label: '在册成员', value: '186', sub: '本月 +4' },
    { label: '进行中比赛', value: '12', sub: '报名中 5' },
    { label: '活跃赛队', value: '47', sub: '本月 +6' },
    { label: '待审批', value: '9', sub: '需今日处理 3', warn: true },
    { label: '材料待补', value: '5', sub: '截止 W8 周五', warn: true }
  ]
})

// ── 待办卡接真：管理端=审批中心 TODO 前 4 条；学生=TARGET 待我确认 ──
interface TodoRow {
  type: string
  text: string
  time: string
}
const todos = ref<TodoRow[]>([])

async function loadTodos() {
  try {
    const r = await apiApprovalList({
      view: user.role === 'STUDENT' ? 'TARGET' : 'TODO',
      status: 'PENDING',
      page: 1,
      size: 4
    })
    const rows: TodoRow[] = []
    for (const a of r.list) {
      rows.push({ type: APPROVAL_TYPE_LABEL[a.bizType] ?? '审批', text: a.bizTitle, time: a.createdAt.slice(5, 16) })
    }
    todos.value = rows
  } catch {
    todos.value = []
  }
}

// ── 学期刻度尺：S01 列表找 ACTIVE 学期算教学周（异常或非 ACTIVE 时保持静态兜底）──
const semName = ref('2026 秋季学期')
const semWeek = ref(6)
const semWeeks = ref(16)
const semPct = ref(37.5)

async function loadSemester() {
  try {
    const list = await apiSemestersSafe()
    for (const s of list) {
      if (s.status !== 'ACTIVE') continue
      semName.value = s.name
      semWeeks.value = s.weekCount
      const days = Math.floor((Date.now() - new Date(s.startDate + 'T00:00:00').getTime()) / 86400000)
      const w = Math.min(s.weekCount, Math.max(1, Math.floor(days / 7) + 1))
      semWeek.value = w
      semPct.value = Math.round((w / s.weekCount) * 1000) / 10
      return
    }
  } catch {
    // 兜底静态演示值（接口不可用时版式不塌）
  }
}

async function loadAll() {
  await Promise.all([loadStudent(), loadTodos(), loadSemester(), approval.refresh()])
}
onMounted(loadAll)
// 顶栏调试切换角色后按新角色口径重载
watch(() => user.role, loadAll)

/** 部门数据表：超管看全平台四个子组织，部门管理员只看自己子树（数媒系） */
const depts = computed(() => {
  const all = [
    { name: '数媒协会', members: 62, teams: 18, pending: 3 },
    { name: '数媒二楼', members: 45, teams: 12, pending: 2 },
    { name: '电控工作室', members: 41, teams: 11, pending: 3 },
    { name: '建模组', members: 38, teams: 6, pending: 1 }
  ]
  if (user.role === 'DEPT_ADMIN') return all.filter((d) => d.name === '数媒协会' || d.name === '数媒二楼')
  return all
})
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>{{ user.role === 'STUDENT' ? '我的概览' : '概览统计' }}</h3>
      <div class="dossier-meta mono">
        <span>数据范围：{{ user.role === 'SUPER_ADMIN' ? '全平台' : user.role === 'DEPT_ADMIN' ? '本部门' : '仅本人参与' }}</span>
        <span>{{ semName }} · 第 {{ semWeek }} 周</span>
      </div>
    </div>

    <!-- 图签指标带 -->
    <div class="metric-block">
      <div v-for="m in metrics" :key="m.label" class="mb-cell" :class="{ warn: m.warn }">
        <i>{{ m.label }}</i>
        <b class="mono">{{ m.value }}</b>
        <span class="mono">{{ m.sub }}</span>
      </div>
    </div>

    <!-- 学期进度刻度尺（S01 接真；里程碑为演示静态位，按周数等比换算） -->
    <div class="ruler-card">
      <div class="ruler-head">
        <span>{{ semName }}进度</span>
        <span class="mono">第 {{ semWeek }} 教学周 / 共 {{ semWeeks }} 周 · 已进行 {{ Math.round(semPct) }}%</span>
      </div>
      <div class="ruler" aria-hidden="true">
        <div class="ruler-fill" :style="{ width: semPct + '%' }"></div>
        <i class="now" :style="{ left: semPct + '%' }"><b class="mono">W{{ semWeek }}</b></i>
        <i class="milestone" :style="{ left: Math.min(96, (10 / semWeeks) * 100) + '%' }"><b class="mono">W10</b><span>报名截止</span></i>
        <i class="milestone" :style="{ left: Math.min(96, (12 / semWeeks) * 100) + '%' }"><b class="mono">W12</b><span>中期检查</span></i>
      </div>
      <div class="ruler-scale mono" aria-hidden="true">
        <span v-for="w in semWeeks" :key="w">W{{ w }}</span>
      </div>
    </div>

    <!-- 待办 + 部门数据（学生端隐藏部门数据卡） -->
    <div class="ov-grid" :class="{ single: user.role === 'STUDENT' }">
      <div class="ov-card">
        <h5>{{ user.role === 'STUDENT' ? '我的待办' : '待办审批' }} <router-link to="/approval">进入审批中心</router-link></h5>
        <table class="mini-ledger">
          <tbody>
            <tr v-for="t in todos" :key="t.text">
              <td class="mono" style="width: 64px">{{ t.type }}</td>
              <td>{{ t.text }}</td>
              <td class="mono dim" style="width: 110px">{{ t.time }}</td>
              <td style="width: 92px"><router-link to="/approval">处理</router-link></td>
            </tr>
          </tbody>
        </table>
        <p v-if="todos.length === 0" class="dim" style="font-size: 12.5px; padding: 10px">暂无待办，去审批中心看看历史单据</p>
      </div>
      <div v-if="user.role !== 'STUDENT'" class="ov-card">
        <h5>各部门数据 <router-link to="/org">组织管理</router-link></h5>
        <table class="mini-ledger">
          <thead>
            <tr><th>子组织</th><th class="num">成员</th><th class="num">赛队</th><th class="num">待办</th></tr>
          </thead>
          <tbody>
            <tr v-for="d in depts" :key="d.name">
              <td>{{ d.name }}</td>
              <td class="num mono">{{ d.members }}</td>
              <td class="num mono">{{ d.teams }}</td>
              <td class="num mono">{{ d.pending }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dossier-head { display: flex; justify-content: space-between; align-items: flex-end; padding-bottom: 14px; border-bottom: 2px solid var(--vio-ink); }
.dossier-head h3 { font-family: var(--vio-serif); font-size: 26px; letter-spacing: 0.1em; font-weight: 700; }
.dossier-meta { display: flex; gap: 22px; font-size: 11.5px; color: var(--vio-ink-3); align-items: center; }
.metric-block {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  border: 2px solid var(--vio-ink);
  background: var(--vio-sheet);
  margin: 20px 0 18px;
}
.mb-cell { border-right: 1px solid var(--vio-rule); padding: 14px 18px 12px; }
.mb-cell:last-child { border-right: none; }
.mb-cell i { display: block; font-style: normal; font-family: var(--vio-fangsong); font-size: 12px; letter-spacing: 0.14em; color: var(--vio-ink-3); }
.mb-cell b { display: block; font-family: var(--vio-disp-latin); font-size: 33px; margin: 2px 0; font-variant-numeric: tabular-nums; }
.mb-cell span { font-size: 11px; color: var(--vio-ink-3); }
.mb-cell.warn b { color: var(--vio-seal); }
.ruler-card { background: var(--vio-sheet); border: 1px solid var(--vio-rule); padding: 16px 20px 14px; margin-bottom: 18px; }
.ruler-head { display: flex; justify-content: space-between; font-size: 13px; margin-bottom: 12px; }
.ruler-head span:last-child { color: var(--vio-ink-3); font-size: 11.5px; }
.ruler { position: relative; height: 22px; background: var(--vio-wash); border: 1px solid var(--vio-rule); }
.ruler-fill { position: absolute; inset: 0 auto 0 0; background: repeating-linear-gradient(-45deg, rgba(29, 58, 143, 0.35) 0 4px, rgba(29, 58, 143, 0.18) 4px 8px); }
.ruler .now { position: absolute; top: -7px; width: 0; height: 36px; border-left: 2px solid var(--vio-seal); }
.ruler .now b { position: absolute; top: -20px; left: -14px; font-size: 11px; color: var(--vio-seal); }
.ruler .milestone { position: absolute; top: 0; width: 0; height: 22px; border-left: 1px dashed var(--vio-ink-3); }
.ruler .milestone b { position: absolute; top: -19px; left: -14px; font-size: 10px; color: var(--vio-ink-2); }
.ruler .milestone span { position: absolute; top: 26px; left: -24px; font-size: 10px; color: var(--vio-ink-3); white-space: nowrap; font-style: normal; }
.ruler-scale { display: flex; justify-content: space-between; margin-top: 22px; font-size: 10px; color: var(--vio-ink-3); }
.ov-grid { display: grid; grid-template-columns: 1.5fr 1fr; gap: 18px; }
.ov-grid.single { grid-template-columns: 1fr; }
.ov-card { background: var(--vio-sheet); border: 1px solid var(--vio-rule); padding: 16px 20px; }
.ov-card h5 {
  font-family: var(--vio-serif);
  font-size: 15px;
  letter-spacing: 0.14em;
  padding-bottom: 9px;
  border-bottom: 1px solid var(--vio-ink);
  margin-bottom: 6px;
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.ov-card h5 a { font-family: var(--vio-body); font-size: 12px; font-weight: 400; }
.mini-ledger { width: 100%; border-collapse: collapse; font-size: 12.5px; }
.mini-ledger th { text-align: left; font-family: var(--vio-fangsong); font-weight: 400; font-size: 11.5px; color: var(--vio-ink-3); letter-spacing: 0.1em; padding: 8px 10px; border-bottom: 1px solid var(--vio-rule); }
.mini-ledger td { padding: 9px 10px; border-bottom: 1px solid var(--vio-rule-soft); color: var(--vio-ink-2); }
.mini-ledger tr:last-child td { border-bottom: none; }
.mini-ledger .num { text-align: right; }
.dim { color: var(--vio-ink-3); }
</style>
