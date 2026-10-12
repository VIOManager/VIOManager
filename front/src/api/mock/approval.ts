/**
 * 审批中心 mock 接口层（P01–P09，契约：《审批中心-接口清单》2026-10-10 正式版）
 * 数据与 SPI 在 ./approval-data（分层惯例同 team.ts/team-data.ts）
 * 数据范围（P01 TODO 视角）：超管全量；部门管理员按审批责任组织子树裁剪（mock/scope.ts 模拟 DataScope）；学生调 TODO 回 1003
 * 幂等（规范 §6 定稿）：P02–P06 缺 X-Idempotency-Key 回 1001，重复键回 1005 不回放
 * ⚠️ 并发（6001 语义）在 mock 无真实并发，仅模拟"单子非 PENDING 再操作"：找不到回 6001、已终结回 6003
 * TODO(联调)：后端审批中心就绪后删除本文件
 */
import { fail, ok, registerMock } from './registry'
import type { Result } from '@/api/types'
import type { MockCtx } from './registry'
import { findMockUserByToken } from './auth'
import { meAsMember } from './member'
import { deptScopeOrgIds, orgInScope } from './scope'
import { addRecord, approvals, fireSettled, nowText, usedIdemKeys } from './approval-data'
import type { MockApproval } from './approval-data'
import type { ApprovalItem } from '@/api/approval'

/** 列表项 VO 转换（清单 §4 ApprovalVO：不含已读位与快照） */
function toItem(a: MockApproval): ApprovalItem {
  return {
    id: a.id, bizType: a.bizType, bizTitle: a.bizTitle, status: a.status,
    applicantName: a.applicantName, targetMemberName: a.targetMemberName,
    applyReason: a.applyReason, handleComment: a.handleComment,
    handledAt: a.handledAt, createdAt: a.createdAt
  }
}

/** P01 三视角过滤后的可见全集（未分页） */
function visibleByView(ctx: MockCtx): MockApproval[] | Result {
  const view = String(ctx.query.view ?? '')
  if (view !== 'TODO' && view !== 'MINE' && view !== 'TARGET') return fail(1001, 'view 传值不合法（TODO/MINE/TARGET）')
  const u = findMockUserByToken(ctx.token)
  const me = meAsMember(ctx.token)
  const out: MockApproval[] = []
  if (view === 'TODO') {
    if (!u || u.role === 'STUDENT') return fail(1003, '学生无管理员待办视角')
    const scope = deptScopeOrgIds(ctx.token)
    for (const a of approvals) {
      if (orgInScope(a.approverOrgId, scope)) out.push(a)
    }
    return out
  }
  if (!me) return out
  for (const a of approvals) {
    if (view === 'MINE' && a.applicantMemberId === me.id) out.push(a)
    if (view === 'TARGET' && a.targetMemberId && a.targetMemberId === me.id) out.push(a)
  }
  return out
}

/** 详情可见性（P09）：超管 / 发起人 / 目标人 / 审批组织子树内管理员 */
function detailVisible(a: MockApproval, ctx: MockCtx): boolean {
  const u = findMockUserByToken(ctx.token)
  const me = meAsMember(ctx.token)
  if (!u) return false
  if (u.role === 'SUPER_ADMIN') return true
  if (me && (a.applicantMemberId === me.id || (a.targetMemberId && a.targetMemberId === me.id))) return true
  if (u.role === 'DEPT_ADMIN') return orgInScope(a.approverOrgId, deptScopeOrgIds(ctx.token))
  return false
}

function findApproval(id: string): MockApproval | null {
  for (const a of approvals) {
    if (a.id === id) return a
  }
  return null
}

registerMock({
  method: 'get',
  path: '/api/approvals',
  reply: (ctx) => {
    const list = visibleByView(ctx)
    if (!Array.isArray(list)) return list
    const status = String(ctx.query.status ?? '')
    const bizType = String(ctx.query.bizType ?? '')
    const keyword = String(ctx.query.keyword ?? '').trim()
    const page = Math.max(1, Number(ctx.query.page ?? 1) || 1)
    const size = Math.min(100, Math.max(1, Number(ctx.query.size ?? 20) || 20))
    const filtered: MockApproval[] = []
    for (const a of list) {
      if (status && a.status !== status) continue
      if (bizType && a.bizType !== bizType) continue
      if (keyword && a.bizTitle.indexOf(keyword) < 0) continue
      filtered.push(a)
    }
    // 创建时间倒序（YYYY-MM-DD HH:mm:ss 字典序即时间序）
    filtered.sort((x, y) => (x.createdAt < y.createdAt ? 1 : -1))
    const start = (page - 1) * size
    const items: ApprovalItem[] = []
    for (const a of filtered.slice(start, start + size)) items.push(toItem(a))
    return ok({ list: items, total: filtered.length, page, size })
  }
})

// ⚠️ 注册顺序：unread-count 必须先于 /:id（matchHandler 顺序匹配，:id 会吃掉 unread-count 段）
registerMock({
  method: 'get',
  path: '/api/approvals/unread-count',
  reply: (ctx) => {
    const me = meAsMember(ctx.token)
    // 未绑定人员档案的纯管理账号回两个 0（清单 §4 P08）
    if (!me) return ok({ todoCount: 0, resultCount: 0 })
    let todo = 0
    let result = 0
    for (const a of approvals) {
      if (a.targetMemberId === me.id && a.status === 'PENDING' && !a.readTarget) todo++
      if (a.applicantMemberId === me.id && a.status !== 'PENDING' && !a.readApplicant) result++
    }
    return ok({ todoCount: todo, resultCount: result })
  }
})

registerMock({
  method: 'get',
  path: '/api/approvals/:id',
  reply: (ctx) => {
    const a = findApproval(ctx.params.id)
    if (!a) return fail(6001, '审批单不存在或已处理')
    if (!detailVisible(a, ctx)) return fail(6002, '无权查看该审批')
    return ok({ ...toItem(a), bizPayload: a.payloadJson, records: a.records })
  }
})

registerMock({
  method: 'put',
  path: '/api/approvals/read',
  reply: (ctx) => {
    const asRole = String(ctx.body.asRole ?? '')
    if (asRole !== 'APPLICANT' && asRole !== 'TARGET' && asRole !== 'APPROVER') return fail(1001, 'asRole 传值不合法')
    const ids = Array.isArray(ctx.body.ids) ? (ctx.body.ids as string[]) : []
    for (const id of ids) {
      const a = findApproval(String(id))
      if (!a) continue
      if (asRole === 'APPLICANT') a.readApplicant = true
      if (asRole === 'TARGET') a.readTarget = true
      if (asRole === 'APPROVER') a.readApprover = true
    }
    return ok(null)
  }
})

/** P02–P06 公共处理：幂等校验 → 找单 → 状态 → 权限 → 终结迁移 + 流水 + 回调 */
function settle(ctx: MockCtx, action: 'APPROVE' | 'REJECT' | 'CANCEL' | 'AGREE' | 'REFUSE'): Result {
  if (!ctx.idemKey) return fail(1001, '缺少幂等键（X-Idempotency-Key）')
  if (usedIdemKeys.has(ctx.idemKey)) return fail(1005, '请勿重复提交')
  const a = findApproval(ctx.params.id)
  if (!a) return fail(6001, '审批单不存在或已处理')
  if (a.status !== 'PENDING') return fail(6003, '当前审批状态不允许该操作')
  const u = findMockUserByToken(ctx.token)
  const me = meAsMember(ctx.token)
  const comment = String(ctx.body.comment ?? '')
  if (action === 'APPROVE' || action === 'REJECT') {
    if (!u || u.role === 'STUDENT') return fail(1003, '仅管理员可处理审批')
    if (u.role === 'DEPT_ADMIN' && !orgInScope(a.approverOrgId, deptScopeOrgIds(ctx.token))) return fail(6002, '无权处理该审批')
  }
  if (action === 'CANCEL' && (!me || me.id !== a.applicantMemberId)) return fail(6002, '无权处理该审批')
  if ((action === 'AGREE' || action === 'REFUSE') && (!me || me.id !== a.targetMemberId)) return fail(6002, '无权处理该审批')
  usedIdemKeys.add(ctx.idemKey)
  const operatorName = me ? me.name : u ? u.realName : '未知'
  if (action === 'CANCEL') {
    a.status = 'CANCELLED'
    a.readApplicant = true
    addRecord(a, 'CANCEL', operatorName, 'APPLICANT', comment)
  } else if (action === 'APPROVE' || action === 'REJECT') {
    a.status = action === 'APPROVE' ? 'APPROVED' : 'REJECTED'
    a.readApprover = true
    a.readApplicant = false
    addRecord(a, action, operatorName, 'APPROVER', comment)
  } else {
    a.status = action === 'AGREE' ? 'APPROVED' : 'REJECTED'
    a.readTarget = true
    a.readApplicant = false
    addRecord(a, action, operatorName, 'TARGET', comment)
  }
  if (action !== 'CANCEL') {
    a.handleComment = comment
    a.handledBy = operatorName
    a.handledAt = nowText()
    // 按 bizType 路由业务回调（模拟 ApprovalHandler；取消不触发回调，清单 §1）
    fireSettled(a)
  }
  return ok(null)
}

registerMock({ method: 'post', path: '/api/approvals/:id/approve', reply: (ctx) => settle(ctx, 'APPROVE') })
registerMock({ method: 'post', path: '/api/approvals/:id/reject', reply: (ctx) => settle(ctx, 'REJECT') })
registerMock({ method: 'post', path: '/api/approvals/:id/cancel', reply: (ctx) => settle(ctx, 'CANCEL') })
registerMock({ method: 'post', path: '/api/approvals/:id/agree', reply: (ctx) => settle(ctx, 'AGREE') })
registerMock({ method: 'post', path: '/api/approvals/:id/refuse', reply: (ctx) => settle(ctx, 'REFUSE') })
