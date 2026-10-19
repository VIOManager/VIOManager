<script setup lang="ts">
/**
 * 概览统计页（三端共用壳，数据范围由后端按角色裁剪 —— PRD §3.16）
 * 对应方案：Step 2 大框阶段先落地版式；S2 粗野重涂（2026-10-15 方案 E 样张）
 * 学生端（/my/overview）：指标带五格全部接真（聚合 我的赛队/成果/材料/审批红点，零新增契约）
 * 管理端（/overview）：指标带与部门数据卡仍为静态演示（全平台/部门统计接口未出，骨架遗留），
 *    待办审批卡已接审批中心 P01；学期刻度尺三端接真（S01 列表），异常时静态兜底
 * ⚠️ 注意：PRD §3.16 要求"HUD 风格"，定调为粗野贴纸后以"指标贴纸带 + 学期刻度尺"表达
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
      semWeeks.value = s.totalWeeks
      // startDate 可能为 null（种子学期没填起止日）或带时分秒（后端 Jackson 全局时间格式）——
      // 统一截前 10 位日期解析；拿不到合法日期保持静态兜底周数，不把 NaN 写上刻度尺
      const day = (s.startDate ?? '').slice(0, 10)
      const start = new Date(day + 'T00:00:00').getTime()
      if (!day || Number.isNaN(start)) return
      const days = Math.floor((Date.now() - start) / 86400000)
      const w = Math.min(s.totalWeeks, Math.max(1, Math.floor(days / 7) + 1))
      semWeek.value = w
      semPct.value = Math.round((w / s.totalWeeks) * 1000) / 10
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
// 身份（角色）变化时按新口径重载
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
    <div class="ov-head">
      <div class="ov-title">
        <h3>{{ user.role === 'STUDENT' ? '我的概览' : '概览统计' }}</h3>
        <span class="tag mono">OVERVIEW · 2026F</span>
      </div>
      <div class="ov-meta mono">
        <span class="chip">数据范围：{{ user.role === 'SUPER_ADMIN' ? '全平台' : user.role === 'DEPT_ADMIN' ? '本部门' : '仅本人参与' }}</span>
        <span class="chip">{{ semName }} · 第 {{ semWeek }} 周</span>
      </div>
    </div>

    <!-- 指标贴纸带 -->
    <div class="metric-block">
      <div v-for="(m, i) in metrics" :key="m.label" class="mb-cell" :class="{ warn: m.warn, tiltL: i % 2 === 0, tiltR: i % 2 === 1 }">
        <i class="mono">{{ m.label }}</i>
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
        <h5><span>{{ user.role === 'STUDENT' ? '我的待办' : '待办审批' }}</span><router-link class="btn-mini" to="/approval">进入审批中心 →</router-link></h5>
        <table class="mini-ledger">
          <tbody>
            <tr v-for="t in todos" :key="t.text">
              <td class="mono" style="width: 72px"><em class="pill">{{ t.type }}</em></td>
              <td>{{ t.text }}</td>
              <td class="mono dim" style="width: 110px">{{ t.time }}</td>
              <td style="width: 74px"><router-link class="btn-mini" to="/approval">处理</router-link></td>
            </tr>
          </tbody>
        </table>
        <p v-if="todos.length === 0" class="dim" style="font-size: 12.5px; padding: 10px">暂无待办，去审批中心看看历史单据</p>
      </div>
      <div v-if="user.role !== 'STUDENT'" class="ov-card tilt">
        <h5><span>各部门数据</span><router-link class="btn-mini" to="/org">组织管理 →</router-link></h5>
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
.ov-head { display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: 12px; }
.ov-title { display: flex; align-items: center; gap: 14px; }
.ov-head h3 {
  font-family: var(--vio2-din);
  font-weight: 900;
  font-size: 34px;
  letter-spacing: 0.02em;
  color: var(--vio2-ink);
}
.tag {
  background: var(--vio2-yellow);
  color: #101010;
  border: 2px solid var(--vio2-bd);
  box-shadow: 3px 3px 0 var(--vio2-shadow);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
  padding: 2px 9px 3px;
  transform: rotate(-2deg);
}
.ov-meta { display: flex; gap: 8px; align-items: center; }
.ov-meta .chip {
  font-size: 10.5px;
  color: var(--vio2-ink-2);
  border: 2px solid var(--vio2-bd);
  background: var(--vio2-panel);
  padding: 3px 10px;
  letter-spacing: 0.06em;
}
/* 指标贴纸带：五格贴纸，交替微倾 */
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
.tiltL { transform: rotate(-0.5deg); }
.tiltR { transform: rotate(0.5deg); }
.mb-cell i { display: block; font-style: normal; font-size: 10px; font-weight: 700; letter-spacing: 0.16em; color: var(--vio2-ink-3); }
.mb-cell b { display: block; font-family: var(--vio2-din); font-size: 34px; font-weight: 900; margin: 2px 0 1px; color: var(--vio2-ink); }
.mb-cell span { font-size: 10.5px; color: var(--vio2-ink-3); }
.mb-cell.warn { background: var(--vio2-pink); }
.mb-cell.warn i, .mb-cell.warn span { color: #101010; }
.mb-cell.warn b { color: #101010; }
.mb-cell.warn::after { background: var(--vio2-panel); }
/* 学期刻度尺 */
.ruler-card {
  background: var(--vio2-panel);
  border: 3px solid var(--vio2-bd);
  box-shadow: 6px 6px 0 var(--vio2-shadow);
  padding: 18px 22px 14px;
  margin-bottom: 20px;
}
.ruler-head { display: flex; justify-content: space-between; font-size: 13.5px; font-weight: 700; color: var(--vio2-ink); margin-bottom: 14px; }
.ruler-head span:last-child { color: var(--vio2-ink-3); font-size: 11px; font-weight: 400; }
.ruler { position: relative; height: 24px; background: var(--vio2-panel-2); border: 2px solid var(--vio2-bd); }
.ruler-fill {
  position: absolute;
  inset: 0 auto 0 0;
  background: repeating-linear-gradient(-45deg, var(--vio2-yellow) 0 8px, var(--vio2-panel-2) 8px 16px);
  border-right: 2px solid var(--vio2-bd);
}
.ruler .now { position: absolute; top: -9px; width: 0; height: 42px; border-left: 3px solid var(--vio2-pink); }
.ruler .now b { position: absolute; top: -22px; left: -16px; font-size: 11px; font-weight: 900; color: var(--vio2-pink); }
.ruler .milestone { position: absolute; top: 0; width: 0; height: 24px; border-left: 2px dashed var(--vio2-ink-3); }
.ruler .milestone b { position: absolute; top: -20px; left: -14px; font-size: 10px; font-weight: 700; color: var(--vio2-ink-2); }
.ruler .milestone span { position: absolute; top: 28px; left: -26px; font-size: 10px; color: var(--vio2-ink-3); white-space: nowrap; font-style: normal; }
.ruler-scale { display: flex; justify-content: space-between; margin-top: 24px; font-size: 9.5px; color: var(--vio2-ink-3); }
/* 待办 / 部门卡 */
.ov-grid { display: grid; grid-template-columns: 1.5fr 1fr; gap: 20px; }
.ov-grid.single { grid-template-columns: 1fr; }
.ov-card {
  background: var(--vio2-panel);
  border: 3px solid var(--vio2-bd);
  box-shadow: 6px 6px 0 var(--vio2-shadow);
  padding: 16px 20px 12px;
}
.ov-card.tilt { transform: rotate(0.4deg); }
.ov-card h5 {
  font-family: var(--vio2-din);
  font-size: 16px;
  font-weight: 900;
  letter-spacing: 0.04em;
  color: var(--vio2-ink);
  padding-bottom: 10px;
  border-bottom: 3px solid var(--vio2-bd);
  margin-bottom: 4px;
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.btn-mini {
  font-family: var(--vio-mono);
  font-size: 11px;
  font-weight: 700;
  color: var(--vio2-ink);
  text-decoration: none;
  border: 2px solid var(--vio2-bd);
  background: var(--vio2-yellow);
  padding: 2px 9px 3px;
}
.btn-mini:hover { background: var(--vio2-lilac); }
.mini-ledger { width: 100%; border-collapse: collapse; font-size: 12.5px; }
.mini-ledger th {
  text-align: left;
  font-weight: 700;
  font-size: 10px;
  color: var(--vio2-ink-3);
  letter-spacing: 0.14em;
  padding: 9px 10px 7px;
  border-bottom: 2px solid var(--vio2-bd);
}
.mini-ledger td { padding: 10px 10px; border-bottom: 2px solid var(--vio2-row-line); color: var(--vio2-ink-2); }
.mini-ledger tr:last-child td { border-bottom: none; }
.mini-ledger .num { text-align: right; }
.pill {
  font-style: normal;
  font-size: 10px;
  font-weight: 700;
  color: var(--vio2-ink);
  background: var(--vio2-panel-2);
  border: 2px solid var(--vio2-bd);
  padding: 1px 6px;
}
.dim { color: var(--vio2-ink-3); }
</style>
