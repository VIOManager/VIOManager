/**
 * 比赛与待处理池 mock（B 线 C01–C09 + P01–P03；模板在 ./contest-templates）
 * ⚠️ 演示数据见 ./contest-data；规则三级合并在 C02 里按 模板→比赛→赛道 计算并标注来源
 * 校验：4012 模板被引用拒删 / 4001 非 DRAFT 拒删 + 发布前检查 / 4011 状态不可跳转 / 赛道有队拒删
 * TODO(联调)：后端赛事接口就绪后删除本文件
 */
import { fail, ok, registerMock } from './registry'
import { findMockUserByToken } from './auth'
import { contests, poolItems, templates } from './contest-data'
import type { MockContest, MockTrack } from './contest-data'
import type { ContestDetail, ContestListItem, ContestStatus, EffectiveRule, RulesMap } from '@/api/contest'

let seq = 10
let trackSeq = 10

/** 规则六键（B 线 §0，全组唯一） */
const RULE_KEYS = ['memberLimit', 'prefillLimit', 'allowRepeatJoin', 'crossTeamLeader', 'maxAdvisorCount', 'crossTrackLimit']

/** 三级继承合并：模板默认 → 比赛覆盖 → 赛道覆盖，逐键标注来源 */
function mergeRules(c: MockContest, track: MockTrack | null): EffectiveRule[] {
  let template = null
  for (const t of templates) {
    if (t.id === c.templateId) template = t
  }
  const defaults = template ? template.defaultRules : {}
  const out: EffectiveRule[] = []
  for (const k of RULE_KEYS) {
    let value: number | boolean = defaults[k] ?? 0
    let source: EffectiveRule['source'] = 'TEMPLATE'
    if (Object.prototype.hasOwnProperty.call(c.overrideRules, k)) {
      value = c.overrideRules[k]
      source = 'CONTEST'
    }
    if (track && Object.prototype.hasOwnProperty.call(track.overrideRules, k)) {
      value = track.overrideRules[k]
      source = 'TRACK'
    }
    out.push({ key: k, value, source })
  }
  return out
}

function toListItem(c: MockContest): ContestListItem {
  return {
    id: c.id, name: c.name, semesterId: c.semesterId, semesterName: c.semesterName, status: c.status,
    signupStart: c.signupStart, signupEnd: c.signupEnd, contestStart: c.contestStart, contestEnd: c.contestEnd,
    trackCount: c.tracks.length, teamCount: c.teamCount
  }
}

function toDetail(c: MockContest): ContestDetail {
  return {
    id: c.id, name: c.name, semesterId: c.semesterId, semesterName: c.semesterName,
    templateId: c.templateId, templateName: c.templateName, status: c.status,
    signupStart: c.signupStart, signupEnd: c.signupEnd, contestStart: c.contestStart, contestEnd: c.contestEnd,
    contact: c.contact, description: c.description, overrideRules: c.overrideRules,
    rules: mergeRules(c, null),
    tracks: c.tracks.map((t) => ({ id: t.id, name: t.name, code: t.code, description: t.description, overrideRules: t.overrideRules, rules: mergeRules(c, t), teamCount: t.teamCount }))
  }
}

const STATUS_ORDER: ContestStatus[] = ['DRAFT', 'PUBLISHED', 'REGISTERING', 'RUNNING', 'FINISHED', 'ARCHIVED']

function findContest(id: string): MockContest | null {
  for (const c of contests) {
    if (c.id === id) return c
  }
  return null
}

// ── 比赛 C01–C09 ──
// ⚠️ 注册顺序：/pool 先于 /:id
registerMock({
  method: 'get',
  path: '/api/contests/pool',
  reply: (ctx) => {
    const status = String(ctx.query.status ?? '')
    const list = status ? poolItems.filter((p) => p.status === status) : poolItems
    return ok(list)
  }
})

registerMock({
  method: 'post',
  path: '/api/contests/pool/:id/confirm',
  reply: (ctx) => {
    for (const p of poolItems) {
      if (p.id !== ctx.params.id) continue
      if (p.status !== 'PENDING') return fail(1001, '该条目已处理')
      const created = createContest(ctx.body as Record<string, unknown>)
      if ('code' in created) return fail(created.code, created.message)
      p.status = 'CONFIRMED'
      p.confirmedContestId = created.id
      return ok(toDetail(created))
    }
    return fail(1004, '池条目不存在')
  }
})

registerMock({
  method: 'post',
  path: '/api/contests/pool/:id/ignore',
  reply: (ctx) => {
    for (const p of poolItems) {
      if (p.id !== ctx.params.id) continue
      if (p.status !== 'PENDING') return fail(1001, '该条目已处理')
      p.status = 'IGNORED'
      return ok(null)
    }
    return fail(1004, '池条目不存在')
  }
})

registerMock({
  method: 'get',
  path: '/api/contests',
  reply: (ctx) => {
    const u = findMockUserByToken(ctx.token)
    const semesterId = String(ctx.query.semesterId ?? '')
    const status = String(ctx.query.status ?? '')
    const out: ContestListItem[] = []
    for (const c of contests) {
      // 学生仅可见已发布及之后的状态
      if (u && u.role === 'STUDENT' && c.status === 'DRAFT') continue
      if (semesterId && c.semesterId !== semesterId) continue
      if (status && c.status !== status) continue
      out.push(toListItem(c))
    }
    return ok(out)
  }
})

registerMock({
  method: 'get',
  path: '/api/contests/:id',
  reply: (ctx) => {
    const c = findContest(ctx.params.id)
    return c ? ok(toDetail(c)) : fail(1004, '比赛不存在')
  }
})

/** 创建共用逻辑（C03 与 P02 确认预填复用） */
function createContest(body: Record<string, unknown>): MockContest | { code: number; message: string } {
  const name = String(body.name ?? '').trim()
  if (!name) return { code: 1001, message: '比赛名称不能为空' }
  let template = null
  for (const t of templates) {
    if (t.id === String(body.templateId ?? '')) template = t
  }
  if (!template) return { code: 1004, message: '模板不存在' }
  const c: MockContest = {
    id: 'c' + seq++, name,
    semesterId: String(body.semesterId ?? 's2'), semesterName: '2026秋季学期',
    templateId: template.id, templateName: template.name, status: 'DRAFT',
    signupStart: String(body.signupStart ?? ''), signupEnd: String(body.signupEnd ?? ''),
    contestStart: String(body.contestStart ?? ''), contestEnd: String(body.contestEnd ?? ''),
    contact: String(body.contact ?? ''), description: String(body.description ?? ''),
    overrideRules: (body.overrideRules as RulesMap) ?? {}, tracks: [], teamCount: 0
  }
  contests.unshift(c)
  template.refCount += 1
  return c
}

registerMock({
  method: 'post',
  path: '/api/contests',
  reply: (ctx) => {
    const r = createContest(ctx.body)
    if ('code' in r) return fail(r.code, r.message)
    return ok(toDetail(r))
  }
})

registerMock({
  method: 'put',
  path: '/api/contests/:id',
  reply: (ctx) => {
    const c = findContest(ctx.params.id)
    if (!c) return fail(1004, '比赛不存在')
    c.name = String(ctx.body.name ?? c.name)
    c.signupStart = String(ctx.body.signupStart ?? c.signupStart)
    c.signupEnd = String(ctx.body.signupEnd ?? c.signupEnd)
    c.contestStart = String(ctx.body.contestStart ?? c.contestStart)
    c.contestEnd = String(ctx.body.contestEnd ?? c.contestEnd)
    c.contact = String(ctx.body.contact ?? c.contact)
    c.description = String(ctx.body.description ?? c.description)
    c.overrideRules = (ctx.body.overrideRules as RulesMap) ?? c.overrideRules
    return ok(toDetail(c))
  }
})

registerMock({
  method: 'delete',
  path: '/api/contests/:id',
  reply: (ctx) => {
    const c = findContest(ctx.params.id)
    if (!c) return fail(1004, '比赛不存在')
    if (c.status !== 'DRAFT') return fail(4001, '仅草稿状态可删除')
    contests.splice(contests.indexOf(c), 1)
    return ok(null)
  }
})

registerMock({
  method: 'post',
  path: '/api/contests/:id/status',
  reply: (ctx) => {
    const c = findContest(ctx.params.id)
    if (!c) return fail(1004, '比赛不存在')
    const to = String(ctx.body.to ?? '') as ContestStatus
    const cur = STATUS_ORDER.indexOf(c.status)
    const next = STATUS_ORDER.indexOf(to)
    if (next !== cur + 1) return fail(4011, '状态流转不可跳转')
    // 发布前检查（DRAFT→PUBLISHED）：逐项指出缺项
    if (to === 'PUBLISHED') {
      const missing: string[] = []
      if (!c.name || !c.contact || !c.signupStart || !c.signupEnd || !c.contestStart || !c.contestEnd) missing.push('基础信息不完整')
      if (c.tracks.length === 0) missing.push('未配置赛道')
      if (missing.length > 0) return fail(4001, '发布前检查未通过：' + missing.join('；'))
    }
    c.status = to
    return ok(null)
  }
})

registerMock({
  method: 'post',
  path: '/api/contests/:id/tracks',
  reply: (ctx) => {
    const c = findContest(ctx.params.id)
    if (!c) return fail(1004, '比赛不存在')
    const name = String(ctx.body.name ?? '').trim()
    if (!name) return fail(1001, '赛道名称不能为空')
    const t: MockTrack = { id: 'tr' + trackSeq++, name, code: String(ctx.body.code ?? ''), description: String(ctx.body.description ?? ''), overrideRules: (ctx.body.overrideRules as RulesMap) ?? {}, teamCount: 0 }
    c.tracks.push(t)
    return ok({ ...t, rules: mergeRules(c, t) })
  }
})

registerMock({
  method: 'put',
  path: '/api/contests/:id/tracks/:trackId',
  reply: (ctx) => {
    const c = findContest(ctx.params.id)
    if (!c) return fail(1004, '比赛不存在')
    for (const t of c.tracks) {
      if (t.id !== ctx.params.trackId) continue
      t.name = String(ctx.body.name ?? t.name)
      t.code = String(ctx.body.code ?? t.code)
      t.description = String(ctx.body.description ?? t.description)
      t.overrideRules = (ctx.body.overrideRules as RulesMap) ?? t.overrideRules
      return ok({ ...t, rules: mergeRules(c, t) })
    }
    return fail(1004, '赛道不存在')
  }
})

registerMock({
  method: 'delete',
  path: '/api/contests/:id/tracks/:trackId',
  reply: (ctx) => {
    const c = findContest(ctx.params.id)
    if (!c) return fail(1004, '比赛不存在')
    for (const t of c.tracks) {
      if (t.id !== ctx.params.trackId) continue
      if (t.teamCount > 0) return fail(4001, '该赛道已有赛队挂载，禁止删除')
      c.tracks.splice(c.tracks.indexOf(t), 1)
      return ok(null)
    }
    return fail(1004, '赛道不存在')
  }
})
