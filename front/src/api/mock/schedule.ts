/**
 * 排班 mock 接口层（SC01–SC11，契约：《排班管理-接口清单》§3–§6）
 * 数据与空课计算在 ./schedule-data
 * 排班校验链（SC02/SC03 共用，按序拦截，见清单 §4）：
 *   8009 人员不属于该子组织 → 8007 时段未启用 → 8006 超人数上限 → 8005 同周同人同组织已有 → 8008 课表冲突（带 force 放行并打标记）
 * 数据范围：SC01 部门管理员仅可查本子树组织的排班；SC05 学生只看本人
 * TODO(联调)：后端排班接口就绪后删除本文件
 */
import { fail, ok, registerMock } from './registry'
import type { Result } from '@/api/types'
import { findMockUserByToken } from './auth'
import { meAsMember } from './member'
import { deptScopeOrgIds, orgInScope } from './scope'
import { tree } from './org-data'
import { members } from './member-data'
import { semesters } from './semester'
import { usedIdemKeys } from './approval-data'
import { coursesAt, freeMembersAt, memberFreeAt, scheduleRules, schedules } from './schedule-data'
import type { MockScheduleRule } from './schedule-data'

let scheduleSeq = 4
let ruleSeq = 11

/** 子组织 id 集（含子孙；org-data 树内查找：先定位节点再整棵收集） */
function subtreeIds(orgId: string): string[] {
  const find = (nodes: typeof tree): (typeof tree)[0] | null => {
    for (const n of nodes) {
      if (n.id === orgId) return n
      const hit = find(n.children)
      if (hit) return hit
    }
    return null
  }
  const root = find(tree)
  const out: string[] = []
  if (!root) return out
  const collect = (n: (typeof tree)[0]) => {
    out.push(n.id)
    for (const c of n.children) collect(c)
  }
  collect(root)
  return out
}

/** 规则匹配：子组织+学期特定优先，回退子组织默认（semesterId=null） */
function ruleAt(subOrgId: string, semesterId: string, weekday: number, slotIndex: number): MockScheduleRule | null {
  let fallback: MockScheduleRule | null = null
  for (const r of scheduleRules) {
    if (r.subOrgId !== subOrgId || r.weekday !== weekday || r.slotIndex !== slotIndex) continue
    if (r.semesterId === semesterId) return r
    if (r.semesterId === null) fallback = r
  }
  return fallback
}

/** SC02/SC03 校验链：全部通过返回 null，否则返回错误 Result */
function validateAssign(semesterId: string, weekNo: number, subOrgId: string, memberId: string, weekday: number, slotIndex: number, force: boolean, excludeId: string): Result | null {
  const member = members.find((m) => m.id === memberId)
  if (!member) return fail(1004, '人员不存在')
  // 8009 人员不属于该子组织（含其子树）
  if (subtreeIds(subOrgId).indexOf(member.orgId) < 0) return fail(8009, '排班人员不属于该子组织')
  // 8007 时段未启用
  const rule = ruleAt(subOrgId, semesterId, weekday, slotIndex)
  if (!rule || !rule.enabled) return fail(8007, '该时段未启用排班')
  // 8006 该时段人数达上限
  let count = 0
  for (const s of schedules) {
    if (s.semesterId === semesterId && s.weekNo === weekNo && s.subOrgId === subOrgId && s.weekday === weekday && s.slotIndex === slotIndex && s.status === 'ACTIVE' && s.id !== excludeId) count++
  }
  if (count >= rule.maxPerson) return fail(8006, '该时段排班人数已达上限')
  // 8005 同周同人同子组织已有排班（拍板：一周一个班）
  for (const s of schedules) {
    if (s.semesterId === semesterId && s.weekNo === weekNo && s.subOrgId === subOrgId && s.memberId === memberId && s.status === 'ACTIVE' && s.id !== excludeId) {
      return fail(8005, '该周次该人在该子组织已有排班')
    }
  }
  // 8008 课表冲突（未带 force 拦截）
  const conflicts = coursesAt(member.className, weekNo, weekday, slotIndex)
  if (conflicts.length > 0 && !force) return fail(8008, '排班与课表冲突（' + conflicts.join('、') + '），确认强制保存请带 force')
  return null
}

/** SC06 矩阵（仅管理端） */
registerMock({
  method: 'get',
  path: '/api/schedules/free-slots/matrix',
  reply: (ctx) => {
    const u = findMockUserByToken(ctx.token)
    if (!u || u.role === 'STUDENT') return fail(1003, '仅管理员可查空课')
    const weekNo = Number(ctx.query.weekNo ?? 0)
    const out = []
    for (let weekday = 1; weekday <= 7; weekday++) {
      for (let slotIndex = 1; slotIndex <= 5; slotIndex++) {
        const free = freeMembersAt(weekNo, weekday, slotIndex)
        const gradeDist: Record<string, number> = {}
        for (const m of free) {
          const g = m.studentNo.length >= 4 ? m.studentNo.slice(0, 4) : '未知'
          gradeDist[g] = (gradeDist[g] ?? 0) + 1
        }
        out.push({ weekday, slotIndex, freeCount: free.length, gradeDist })
      }
    }
    return ok(out)
  }
})

/** SC07 单格空课名单 */
registerMock({
  method: 'get',
  path: '/api/schedules/free-slots',
  reply: (ctx) => {
    const u = findMockUserByToken(ctx.token)
    if (!u || u.role === 'STUDENT') return fail(1003, '仅管理员可查空课')
    const weekNo = Number(ctx.query.weekNo ?? 0)
    const weekday = Number(ctx.query.weekday ?? 0)
    const slotIndex = Number(ctx.query.slotIndex ?? 0)
    const out = []
    for (const m of freeMembersAt(weekNo, weekday, slotIndex)) {
      out.push({ memberId: m.id, name: m.name, className: m.className, grade: m.studentNo.length >= 4 ? m.studentNo.slice(0, 4) : '未知' })
    }
    return ok(out)
  }
})

/** SC05 我的排班（学生；注册在 /api/schedules/:id 族之前避免被吃） */
registerMock({
  method: 'get',
  path: '/api/schedules/my',
  reply: (ctx) => {
    const me = meAsMember(ctx.token)
    if (!me) return ok([])
    const weekNo = Number(ctx.query.weekNo ?? 0)
    const out = []
    for (const s of schedules) {
      if (s.memberId !== me.id || s.status !== 'ACTIVE') continue
      if (weekNo && s.weekNo !== weekNo) continue
      out.push({ id: s.id, weekNo: s.weekNo, subOrgName: orgNameOf(s.subOrgId), weekday: s.weekday, slotIndex: s.slotIndex, dutyName: s.dutyName, remark: s.remark })
    }
    out.sort((a, b) => a.weekNo - b.weekNo || a.weekday - b.weekday || a.slotIndex - b.slotIndex)
    return ok(out)
  }
})

function orgNameOf(orgId: string): string {
  const walk = (nodes: typeof tree): string => {
    for (const n of nodes) {
      if (n.id === orgId) return n.name
      const hit = walk(n.children)
      if (hit) return hit
    }
    return ''
  }
  return walk(tree)
}

/** SC01 周排班表（7×5 格子视图，部门管理员仅本子树） */
registerMock({
  method: 'get',
  path: '/api/schedules',
  reply: (ctx) => {
    const u = findMockUserByToken(ctx.token)
    if (!u || u.role === 'STUDENT') return fail(1003, '仅管理员可查周排班表')
    const semesterId = String(ctx.query.semesterId ?? '')
    const weekNo = Number(ctx.query.weekNo ?? 0)
    const subOrgId = String(ctx.query.subOrgId ?? '')
    const scope = deptScopeOrgIds(ctx.token)
    if (scope !== null && !orgInScope(subOrgId, scope)) return fail(1003, '仅可查本部门子树的排班')
    const out = []
    for (let weekday = 1; weekday <= 7; weekday++) {
      for (let slotIndex = 1; slotIndex <= 5; slotIndex++) {
        const entries = []
        for (const s of schedules) {
          if (s.semesterId === semesterId && s.weekNo === weekNo && s.subOrgId === subOrgId && s.weekday === weekday && s.slotIndex === slotIndex && s.status === 'ACTIVE') {
            entries.push({ id: s.id, memberId: s.memberId, memberName: s.memberName, className: s.className, dutyName: s.dutyName, courseConflict: s.courseConflict, conflictNote: s.conflictNote, remark: s.remark })
          }
        }
        out.push({ weekday, slotIndex, entries })
      }
    }
    return ok(out)
  }
})

/** SC02 批量排班（逐条过校验链，任一失败整体不落库；带幂等头） */
registerMock({
  method: 'post',
  path: '/api/schedules',
  reply: (ctx) => {
    const u = findMockUserByToken(ctx.token)
    if (!u || u.role === 'STUDENT') return fail(1003, '仅管理员可排班')
    if (!ctx.idemKey) return fail(1001, '缺少幂等键（X-Idempotency-Key）')
    if (usedIdemKeys.has(ctx.idemKey)) return fail(1005, '请勿重复提交')
    const semesterId = String(ctx.body.semesterId ?? '')
    const weekNo = Number(ctx.body.weekNo ?? 0)
    const subOrgId = String(ctx.body.subOrgId ?? '')
    const scope = deptScopeOrgIds(ctx.token)
    if (scope !== null && !orgInScope(subOrgId, scope)) return fail(1003, '仅可排本部门子树的班')
    const sem = semesters.find((s) => s.id === semesterId)
    if (sem && (weekNo < 1 || weekNo > sem.weekCount)) return fail(8010, '周次超出学期教学周数')
    const assignments = Array.isArray(ctx.body.assignments) ? (ctx.body.assignments as Array<{ memberId: string; weekday: number; slotIndex: number; dutyName: string }>) : []
    const force = ctx.body.force === true
    for (const a of assignments) {
      const bad = validateAssign(semesterId, weekNo, subOrgId, String(a.memberId), Number(a.weekday), Number(a.slotIndex), force, '')
      if (bad) return bad
    }
    usedIdemKeys.add(ctx.idemKey)
    for (const a of assignments) {
      const member = members.find((m) => m.id === String(a.memberId))
      const conflicts = member ? coursesAt(member.className, weekNo, Number(a.weekday), Number(a.slotIndex)) : []
      schedules.push({
        id: 'sc' + scheduleSeq++, semesterId, weekNo, subOrgId,
        memberId: String(a.memberId), memberName: member ? member.name : '未知', className: member ? member.className : '',
        weekday: Number(a.weekday), slotIndex: Number(a.slotIndex), dutyName: String(a.dutyName ?? '值班'),
        courseConflict: conflicts.length > 0, conflictNote: conflicts.length > 0 ? '与「' + conflicts.join('、') + '」冲突（强制排入）' : '',
        remark: '', status: 'ACTIVE'
      })
    }
    return ok(null)
  }
})

/** SC03 编辑单条（改时段重过校验链） */
registerMock({
  method: 'put',
  path: '/api/schedules/:id',
  reply: (ctx) => {
    const u = findMockUserByToken(ctx.token)
    if (!u || u.role === 'STUDENT') return fail(1003, '仅管理员可编辑排班')
    for (const s of schedules) {
      if (s.id !== ctx.params.id || s.status !== 'ACTIVE') continue
      const weekday = Number(ctx.body.weekday ?? s.weekday)
      const slotIndex = Number(ctx.body.slotIndex ?? s.slotIndex)
      if (weekday !== s.weekday || slotIndex !== s.slotIndex) {
        const bad = validateAssign(s.semesterId, s.weekNo, s.subOrgId, s.memberId, weekday, slotIndex, true, s.id)
        if (bad) return bad
      }
      s.weekday = weekday
      s.slotIndex = slotIndex
      s.dutyName = String(ctx.body.dutyName ?? s.dutyName)
      s.remark = String(ctx.body.remark ?? s.remark)
      return ok(null)
    }
    return fail(1004, '排班不存在')
  }
})

/** SC04 取消排班（置 CANCELLED） */
registerMock({
  method: 'delete',
  path: '/api/schedules/:id',
  reply: (ctx) => {
    const u = findMockUserByToken(ctx.token)
    if (!u || u.role === 'STUDENT') return fail(1003, '仅管理员可取消排班')
    for (const s of schedules) {
      if (s.id === ctx.params.id && s.status === 'ACTIVE') {
        s.status = 'CANCELLED'
        return ok(null)
      }
    }
    return fail(1004, '排班不存在')
  }
})

/** SC10 规则列表（特定优先回退默认） */
registerMock({
  method: 'get',
  path: '/api/schedules/rules',
  reply: (ctx) => {
    const u = findMockUserByToken(ctx.token)
    if (!u || u.role === 'STUDENT') return fail(1003, '仅管理员可查排班规则')
    const subOrgId = String(ctx.query.subOrgId ?? '')
    const semesterId = String(ctx.query.semesterId ?? '')
    const out = []
    for (let weekday = 1; weekday <= 7; weekday++) {
      for (let slotIndex = 1; slotIndex <= 5; slotIndex++) {
        const r = ruleAt(subOrgId, semesterId, weekday, slotIndex)
        out.push({ weekday, slotIndex, enabled: r ? r.enabled : false, minPerson: r ? r.minPerson : 0, maxPerson: r ? r.maxPerson : 0 })
      }
    }
    return ok(out)
  }
})

/** SC11 批量保存规则（upsert：该子组织该学期维度全量替换） */
registerMock({
  method: 'put',
  path: '/api/schedules/rules',
  reply: (ctx) => {
    const u = findMockUserByToken(ctx.token)
    if (!u || u.role === 'STUDENT') return fail(1003, '仅管理员可保存排班规则')
    const subOrgId = String(ctx.body.subOrgId ?? '')
    const semesterId = ctx.body.semesterId ? String(ctx.body.semesterId) : null
    const scope = deptScopeOrgIds(ctx.token)
    if (scope !== null && !orgInScope(subOrgId, scope)) return fail(1003, '仅可配本部门子树的规则')
    const items = Array.isArray(ctx.body.items) ? (ctx.body.items as Array<Record<string, unknown>>) : []
    for (let i = scheduleRules.length - 1; i >= 0; i--) {
      if (scheduleRules[i].subOrgId === subOrgId && scheduleRules[i].semesterId === semesterId) scheduleRules.splice(i, 1)
    }
    for (const it of items) {
      if (it.enabled !== true) continue
      scheduleRules.push({
        id: 'sr' + ruleSeq++, subOrgId, semesterId,
        weekday: Number(it.weekday), slotIndex: Number(it.slotIndex),
        enabled: true, minPerson: Number(it.minPerson ?? 0), maxPerson: Number(it.maxPerson ?? 1)
      })
    }
    return ok(null)
  }
})

registerMock({
  method: 'get',
  path: '/api/schedules/export',
  reply: (ctx) => {
    const u = findMockUserByToken(ctx.token)
    if (!u || u.role === 'STUDENT') return fail(1003, '仅管理员可导出')
    return ok({ note: '周排班表导出任务已生成（W' + String(ctx.query.weekNo ?? '') + '，mock 演示）' })
  }
})

registerMock({
  method: 'get',
  path: '/api/schedules/export-stats',
  reply: (ctx) => {
    const u = findMockUserByToken(ctx.token)
    if (!u || u.role === 'STUDENT') return fail(1003, '仅管理员可导出')
    return ok({ note: '学期排班统计导出任务已生成（每人次数与明细，mock 演示）' })
  }
})
