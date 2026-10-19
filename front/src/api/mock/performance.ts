/**
 * 绩效统计 mock 接口层（PF01–PF12，契约：《绩效统计-接口清单》错误码 8040 起）
 * 数据与汇总计算在 ./performance-data
 * 数据范围：管理侧（PF06–PF12）学生一律 1003；部门管理员按子树裁剪（周报的 subOrgId 在子树内）
 * 校验链（PF02）：8041 同周已有 / 8047 周次超学期教学周数；打分校验 8046→8043→8044→8042 按序
 * 幂等：PF02/PF07 要求 X-Idempotency-Key（缺 1001、重复键 1005），与审批中心同一套 usedIdemKeys
 * TODO(联调)：后端绩效接口就绪后删除本文件
 */
import { fail, ok, registerMock } from './registry'
import { findMockUserByToken } from './auth'
import { meAsMember } from './member'
import { deptScopeOrgIds, orgInScope } from './scope'
import { semesters } from './semester'
import { usedIdemKeys } from './approval-data'
import { nowText } from './approval-data'
import { members } from './member-data'
import { perfRules, perfSummaries, recomputeAll, rulesOf, weeklies } from './performance-data'
import type { MockPerfSummary, MockScoreLine, MockWeekly } from './performance-data'
import type { WeeklyItem } from '@/api/performance'

let weeklySeq = 8
let ruleSeq = 6

/** 当前 ACTIVE 学期（无则兜底 s2） */
function activeSemester() {
  for (const s of semesters) {
    if (s.status === 'ACTIVE') return s
  }
  return semesters[1]
}

function toItem(w: MockWeekly): WeeklyItem {
  return {
    id: w.id, memberId: w.memberId, memberName: w.memberName, subOrgId: w.subOrgId, subOrgName: w.subOrgName,
    weekNo: w.weekNo, status: w.status, totalScore: w.totalScore, submittedAt: w.submittedAt
  }
}

/** 管理侧权限：学生 1003；部门管理员周报的 subOrgId 须在子树内 */
function adminScope(token: string): string[] | null | 'DENY' {
  const u = findMockUserByToken(token)
  if (!u || u.role === 'STUDENT') return 'DENY'
  return deptScopeOrgIds(token)
}

// ── 学生侧 ──

registerMock({
  method: 'get',
  path: '/api/performance/my/weeklies',
  reply: (ctx) => {
    const me = meAsMember(ctx.token)
    if (!me) return ok([])
    const status = String(ctx.query.status ?? '')
    const out: WeeklyItem[] = []
    for (const w of weeklies) {
      if (w.memberId !== me.id) continue
      if (status && w.status !== status) continue
      out.push(toItem(w))
    }
    out.sort((a, b) => b.weekNo - a.weekNo)
    return ok(out)
  }
})

registerMock({
  method: 'post',
  path: '/api/performance/weeklies',
  reply: (ctx) => {
    if (!ctx.idemKey) return fail(1001, '缺少幂等键（X-Idempotency-Key）')
    if (usedIdemKeys.has(ctx.idemKey)) return fail(1005, '请勿重复提交')
    const me = meAsMember(ctx.token)
    if (!me) return fail(1003, '仅学生成员可提交周报')
    const weekNo = Number(ctx.body.weekNo ?? 0)
    const sem = activeSemester()
    if (!weekNo || weekNo < 1 || weekNo > sem.totalWeeks) return fail(8047, '周次超出学期教学周数')
    const workContent = String(ctx.body.workContent ?? '').trim()
    if (!workContent) return fail(1001, '工作内容不能为空')
    for (const w of weeklies) {
      if (w.memberId === me.id && w.semesterId === sem.id && w.weekNo === weekNo) {
        return fail(8041, w.status === 'REJECTED' ? '该周已有被驳回周报，请修改后重新提交' : '该周已提交过周报')
      }
    }
    usedIdemKeys.add(ctx.idemKey)
    const w: MockWeekly = {
      id: 'pw' + weeklySeq++, memberId: me.id, memberName: me.name,
      subOrgId: me.orgId, subOrgName: me.orgName, semesterId: sem.id, weekNo,
      workContent, attachFileId: String(ctx.body.attachFileId ?? ''), attachFileName: '',
      status: 'SUBMITTED', submittedAt: nowText(), rejectReason: '', scoreComment: '', scores: [], totalScore: null
    }
    weeklies.unshift(w)
    recomputeAll()
    return ok({ id: w.id })
  }
})

registerMock({
  method: 'put',
  path: '/api/performance/weeklies/:id',
  reply: (ctx) => {
    const me = meAsMember(ctx.token)
    for (const w of weeklies) {
      if (w.id !== ctx.params.id) continue
      if (!me || w.memberId !== me.id) return fail(8045, '仅本人可操作自己的周报')
      if (w.status !== 'REJECTED') return fail(8042, '周报当前状态不允许该操作')
      const workContent = String(ctx.body.workContent ?? '').trim()
      if (!workContent) return fail(1001, '工作内容不能为空')
      w.workContent = workContent
      w.attachFileId = String(ctx.body.attachFileId ?? w.attachFileId)
      w.status = 'SUBMITTED'
      w.rejectReason = ''
      w.submittedAt = nowText()
      recomputeAll()
      return ok(null)
    }
    return fail(8040, '周报不存在')
  }
})

registerMock({
  method: 'get',
  path: '/api/performance/weeklies/:id',
  reply: (ctx) => {
    const me = meAsMember(ctx.token)
    const scope = adminScope(ctx.token)
    for (const w of weeklies) {
      if (w.id !== ctx.params.id) continue
      const own = me && w.memberId === me.id
      const admin = scope !== 'DENY' && orgInScope(w.subOrgId, scope)
      if (!own && !admin) return fail(8045, '仅本人或管理端可查看周报')
      return ok({ ...toItem(w), workContent: w.workContent, attachFileId: w.attachFileId, attachFileName: w.attachFileName, scoreComment: w.scoreComment, rejectReason: w.rejectReason, scores: w.scores })
    }
    return fail(8040, '周报不存在')
  }
})

registerMock({
  method: 'get',
  path: '/api/performance/my/summary',
  reply: (ctx) => {
    const me = meAsMember(ctx.token)
    if (!me) return ok(null)
    const sem = activeSemester()
    let mine: MockPerfSummary | null = null
    for (const s of perfSummaries) {
      if (s.memberId === me.id && s.semesterId === sem.id) mine = s
    }
    if (!mine) {
      return ok({ semesterId: sem.id, semesterName: sem.name, totalScore: 0, avgScore: 0, scoredCount: 0, submitCount: 0, bestWeekNo: 0, bestWeekScore: 0, rankInOrg: 0, orgMemberCount: 0, trendSlope: 0, tags: [] })
    }
    let orgCount = 0
    for (const s of perfSummaries) {
      if (s.subOrgId === mine.subOrgId && s.semesterId === sem.id) orgCount++
    }
    return ok({ ...mine, semesterName: sem.name, orgMemberCount: orgCount })
  }
})

// ── 管理侧 ──

registerMock({
  method: 'get',
  path: '/api/performance/weeklies',
  reply: (ctx) => {
    const scope = adminScope(ctx.token)
    if (scope === 'DENY') return fail(1003, '仅管理端可查看周报列表')
    const weekNo = Number(ctx.query.weekNo ?? 0)
    const status = String(ctx.query.status ?? '')
    const subOrgId = String(ctx.query.subOrgId ?? '')
    const kw = String(ctx.query.memberKeyword ?? '').trim()
    const out: WeeklyItem[] = []
    for (const w of weeklies) {
      if (!orgInScope(w.subOrgId, scope)) continue
      if (weekNo && w.weekNo !== weekNo) continue
      if (status && w.status !== status) continue
      if (subOrgId && w.subOrgId !== subOrgId) continue
      if (kw && w.memberName.indexOf(kw) < 0) continue
      out.push(toItem(w))
    }
    out.sort((a, b) => (a.submittedAt < b.submittedAt ? 1 : -1))
    return ok(out)
  }
})

registerMock({
  method: 'post',
  path: '/api/performance/weeklies/:id/score',
  reply: (ctx) => {
    if (!ctx.idemKey) return fail(1001, '缺少幂等键（X-Idempotency-Key）')
    if (usedIdemKeys.has(ctx.idemKey)) return fail(1005, '请勿重复提交')
    const scope = adminScope(ctx.token)
    if (scope === 'DENY') return fail(1003, '仅管理端可打分')
    for (const w of weeklies) {
      if (w.id !== ctx.params.id) continue
      if (!orgInScope(w.subOrgId, scope)) return fail(1003, '仅管理端可打分')
      if (w.status !== 'SUBMITTED') return fail(8042, '周报当前状态不允许该操作')
      const rules = rulesOf(w.subOrgId, w.semesterId).filter((r) => r.enabled)
      if (rules.length === 0) return fail(8046, '该子组织未配置绩效规则，请先配置')
      const scores = Array.isArray(ctx.body.scores) ? (ctx.body.scores as Array<{ dimensionKey: string; score: number; commentText?: string }>) : []
      if (scores.length !== rules.length) return fail(8043, '打分维度与该子组织规则不符')
      const lines: MockScoreLine[] = []
      let total = 0
      for (const r of rules) {
        let hit: { dimensionKey: string; score: number; commentText?: string } | null = null
        for (const s of scores) {
          if (s.dimensionKey === r.dimensionKey) hit = s
        }
        if (!hit) return fail(8043, '打分维度与该子组织规则不符')
        const score = Number(hit.score)
        if (isNaN(score) || score < 0) return fail(1001, '得分不合法')
        if (score > r.maxScore) return fail(8044, '维度得分超过满分')
        lines.push({ dimensionKey: r.dimensionKey, dimensionName: r.dimensionName, score, maxScore: r.maxScore, weight: r.weight, commentText: String(hit.commentText ?? '') })
        total += score * r.weight
      }
      usedIdemKeys.add(ctx.idemKey)
      w.scores = lines
      w.totalScore = Math.round(total * 100) / 100
      w.scoreComment = String(ctx.body.scoreComment ?? '')
      w.status = 'SCORED'
      recomputeAll()
      return ok(null)
    }
    return fail(8040, '周报不存在')
  }
})

registerMock({
  method: 'post',
  path: '/api/performance/weeklies/:id/reject',
  reply: (ctx) => {
    const scope = adminScope(ctx.token)
    if (scope === 'DENY') return fail(1003, '仅管理端可驳回')
    const reason = String(ctx.body.reason ?? '').trim()
    if (!reason) return fail(1001, '驳回原因不能为空')
    for (const w of weeklies) {
      if (w.id !== ctx.params.id) continue
      if (!orgInScope(w.subOrgId, scope)) return fail(1003, '仅管理端可驳回')
      if (w.status !== 'SUBMITTED') return fail(8042, '周报当前状态不允许该操作')
      w.status = 'REJECTED'
      w.rejectReason = reason
      recomputeAll()
      return ok(null)
    }
    return fail(8040, '周报不存在')
  }
})

registerMock({
  method: 'get',
  path: '/api/performance/summaries',
  reply: (ctx) => {
    const scope = adminScope(ctx.token)
    if (scope === 'DENY') return fail(1003, '仅管理端可查看汇总')
    const subOrgId = String(ctx.query.subOrgId ?? '')
    const kw = String(ctx.query.memberKeyword ?? '').trim()
    const out: Array<Record<string, unknown>> = []
    for (const s of perfSummaries) {
      if (!orgInScope(s.subOrgId, scope)) continue
      if (subOrgId && s.subOrgId !== subOrgId) continue
      let name = ''
      let orgName = ''
      for (const m of members) {
        if (m.id === s.memberId) {
          name = m.name
          orgName = m.orgName
        }
      }
      if (kw && name.indexOf(kw) < 0) continue
      out.push({ ...s, memberName: name, subOrgName: orgName })
    }
    out.sort((a, b) => (Number(a.totalScore) < Number(b.totalScore) ? 1 : -1))
    return ok(out)
  }
})

registerMock({
  method: 'get',
  path: '/api/performance/export',
  reply: (ctx) => {
    const scope = adminScope(ctx.token)
    if (scope === 'DENY') return fail(1003, '仅管理端可导出')
    const groupBy = String(ctx.query.groupBy ?? 'MEMBER')
    return ok({ note: '绩效汇总导出任务已生成（' + (groupBy === 'ORG' ? '按子组织聚合' : '按成员聚合') + '，共 ' + perfSummaries.length + ' 条，mock 演示）' })
  }
})

registerMock({
  method: 'get',
  path: '/api/performance/rules',
  reply: (ctx) => {
    const scope = adminScope(ctx.token)
    if (scope === 'DENY') return fail(1003, '仅管理端可查看规则')
    const subOrgId = String(ctx.query.subOrgId ?? '')
    const semesterId = String(ctx.query.semesterId ?? '')
    const picked = rulesOf(subOrgId, semesterId || activeSemester().id)
    return ok(picked)
  }
})

registerMock({
  method: 'put',
  path: '/api/performance/rules',
  reply: (ctx) => {
    const scope = adminScope(ctx.token)
    if (scope === 'DENY') return fail(1003, '仅管理端可保存规则')
    const subOrgId = String(ctx.body.subOrgId ?? '')
    if (!orgInScope(subOrgId, scope)) return fail(1003, '仅管理端可保存规则')
    const semesterId = ctx.body.semesterId ? String(ctx.body.semesterId) : null
    const items = Array.isArray(ctx.body.items) ? (ctx.body.items as Array<Record<string, unknown>>) : []
    // upsert：该 (子组织, 学期) 维度全量替换
    for (let i = perfRules.length - 1; i >= 0; i--) {
      if (perfRules[i].subOrgId === subOrgId && perfRules[i].semesterId === semesterId) perfRules.splice(i, 1)
    }
    let sort = 1
    for (const it of items) {
      perfRules.push({
        id: 'pr' + ruleSeq++, subOrgId, semesterId,
        dimensionKey: String(it.dimensionKey ?? ''), dimensionName: String(it.dimensionName ?? ''),
        weight: Number(it.weight ?? 0), maxScore: Number(it.maxScore ?? 100),
        enabled: it.enabled !== false, sortNo: sort++
      })
    }
    return ok(null)
  }
})
