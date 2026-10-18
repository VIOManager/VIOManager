/**
 * 项目管理 mock 接口层·扩展（PR16–PR31：比赛节点 / 项目任务 / 项目文件 / 查看申请授权）
 * 契约：《项目管理-接口清单》（错误码 7xxx）
 * 节点 PR20 提交审核 → 审批中心 MILESTONE_APPROVE 单（审批人=超管，按推荐口径）；
 * 查看申请 PR29 → PROJECT_VIEW 单（审批责任组织=项目所属组织）——两线回调在 ./project-approval.ts
 * TODO(联调)：后端项目接口就绪后删除本文件
 */
import { fail, ok, registerMock } from './registry'
import { findMockUserByToken } from './auth'
import { meAsMember } from './member'
import { members } from './member-data'
import { usedIdemKeys, nowText, pushApproval } from './approval-data'
import { findProject } from './project'
import { applyMilestoneApproved } from './project-approval'
import { milestones, projectTasks } from './project-data'
import type { MockMilestone, MockProjectTask } from './project-data'

let milestoneSeq = 4
let taskSeq = 3

function adminOnly(token: string): boolean {
  const u = findMockUserByToken(token)
  return !!u && u.role !== 'STUDENT'
}

function findMilestone(id: string): MockMilestone | null {
  for (const m of milestones) {
    if (m.id === id) return m
  }
  return null
}

// ── PR16–PR20 比赛时间节点 ──

registerMock({
  method: 'get',
  path: '/api/contests/:id/milestones',
  reply: (ctx) => {
    const u = findMockUserByToken(ctx.token)
    const isAdmin = !!u && u.role !== 'STUDENT'
    const status = String(ctx.query.status ?? '')
    const nodeType = String(ctx.query.nodeType ?? '')
    const out: MockMilestone[] = []
    for (const m of milestones) {
      if (m.contestId !== ctx.params.id) continue
      if (!isAdmin && m.status !== 'APPROVED') continue
      if (status && m.status !== status) continue
      if (nodeType && m.nodeType !== nodeType) continue
      out.push(m)
    }
    out.sort((a, b) => (a.nodeTime < b.nodeTime ? -1 : 1))
    return ok(out)
  }
})

registerMock({
  method: 'post',
  path: '/api/contests/:id/milestones',
  reply: (ctx) => {
    if (!adminOnly(ctx.token)) return fail(1003, '仅管理员可维护时间节点')
    const name = String(ctx.body.name ?? '').trim()
    const nodeTime = String(ctx.body.nodeTime ?? '').trim()
    if (!name || !nodeTime) return fail(1001, '节点名称与时间不能为空')
    const m: MockMilestone = {
      id: 'ms' + milestoneSeq++, contestId: ctx.params.id,
      nodeType: String(ctx.body.nodeType ?? 'OTHER'), name, nodeTime,
      description: String(ctx.body.description ?? ''),
      status: 'DRAFT', rejectReason: '', approvedAt: '', approvedBy: '', createdAt: nowText(), synced: false
    }
    milestones.push(m)
    // 超管创建即生效（2026-10-16 拍板，与真后端同口径）：免审批流直接 APPROVED + 同步任务
    const u = findMockUserByToken(ctx.token)
    if (u && u.role === 'SUPER_ADMIN') applyMilestoneApproved(m, u.realName)
    return ok({ id: m.id })
  }
})

registerMock({
  method: 'put',
  path: '/api/milestones/:id',
  reply: (ctx) => {
    if (!adminOnly(ctx.token)) return fail(1003, '仅管理员可维护时间节点')
    const m = findMilestone(ctx.params.id)
    if (!m) return fail(7001, '节点不存在')
    if (m.status !== 'DRAFT' && m.status !== 'REJECTED') return fail(7009, '时间节点当前状态不允许该操作')
    if (ctx.body.name) m.name = String(ctx.body.name).trim()
    if (ctx.body.nodeTime) m.nodeTime = String(ctx.body.nodeTime)
    if (ctx.body.nodeType) m.nodeType = String(ctx.body.nodeType)
    if (ctx.body.description !== undefined) m.description = String(ctx.body.description)
    return ok(null)
  }
})

registerMock({
  method: 'delete',
  path: '/api/milestones/:id',
  reply: (ctx) => {
    if (!adminOnly(ctx.token)) return fail(1003, '仅管理员可维护时间节点')
    const m = findMilestone(ctx.params.id)
    if (!m) return fail(7001, '节点不存在')
    // 与真后端同口径：PENDING 恒不可删（审批单引用）；DRAFT 可删；APPROVED/REJECTED 仅超管（直生效修正路径）
    const u = findMockUserByToken(ctx.token)
    const isSuper = !!u && u.role === 'SUPER_ADMIN'
    if (m.status === 'PENDING' || (m.status !== 'DRAFT' && !isSuper)) {
      return fail(7009, '时间节点当前状态不允许该操作')
    }
    milestones.splice(milestones.indexOf(m), 1)
    return ok(null)
  }
})

registerMock({
  method: 'post',
  path: '/api/milestones/:id/submit',
  reply: (ctx) => {
    if (!adminOnly(ctx.token)) return fail(1003, '仅管理员可维护时间节点')
    if (!ctx.idemKey) return fail(1001, '缺少幂等键（X-Idempotency-Key）')
    if (usedIdemKeys.has(ctx.idemKey)) return fail(1005, '请勿重复提交')
    const m = findMilestone(ctx.params.id)
    if (!m) return fail(7001, '节点不存在')
    if (m.status !== 'DRAFT' && m.status !== 'REJECTED') return fail(7009, '时间节点当前状态不允许该操作')
    usedIdemKeys.add(ctx.idemKey)
    m.status = 'PENDING'
    m.rejectReason = ''
    const u = findMockUserByToken(ctx.token)
    pushApproval({
      bizType: 'MILESTONE_APPROVE', bizId: m.id,
      bizTitle: (u ? u.realName : '管理员') + ' · 比赛节点审核：' + m.name,
      applyReason: '节点时间：' + m.nodeTime + (m.description ? '；' + m.description : ''),
      payload: { milestoneId: m.id },
      applicantMemberId: '',
      applicantName: u ? u.realName : '管理员',
      approverOrgId: 'o1'
    })
    return ok(null)
  }
})

// ── PR21–PR24 项目任务 ──

registerMock({
  method: 'get',
  path: '/api/projects/:id/tasks',
  reply: (ctx) => {
    const status = String(ctx.query.status ?? '')
    const out: MockProjectTask[] = []
    for (const t of projectTasks) {
      if (t.projectId !== ctx.params.id) continue
      if (status && t.status !== status) continue
      out.push(t)
    }
    out.sort((a, b) => a.sortNo - b.sortNo)
    return ok(out)
  }
})

registerMock({
  method: 'post',
  path: '/api/projects/:id/tasks',
  reply: (ctx) => {
    const p = findProject(ctx.params.id)
    if (!p) return fail(7001, '项目不存在')
    const me = meAsMember(ctx.token)
    if (!me || p.ownerId !== me.id) return fail(7002, '仅项目所有人可操作')
    const title = String(ctx.body.title ?? '').trim()
    if (!title) return fail(1001, '任务标题不能为空')
    const assigneeId = String(ctx.body.assigneeMemberId ?? '')
    let assigneeName = ''
    for (const m of members) {
      if (m.id === assigneeId) assigneeName = m.name
    }
    let maxSort = 0
    for (const t of projectTasks) {
      if (t.projectId === p.id && t.sortNo > maxSort) maxSort = t.sortNo
    }
    const t: MockProjectTask = {
      id: 'pt' + taskSeq++, projectId: p.id, title, description: String(ctx.body.description ?? ''),
      assigneeMemberId: assigneeId, assigneeName, dueDate: String(ctx.body.dueDate ?? ''),
      priority: String(ctx.body.priority ?? 'NORMAL'), status: 'TODO',
      sortNo: Number(ctx.body.sortNo ?? 0) || maxSort + 1, milestoneNodeId: '', completedAt: '', completedByName: ''
    }
    projectTasks.push(t)
    return ok({ id: t.id })
  }
})

registerMock({
  method: 'put',
  path: '/api/projects/:id/tasks/:taskId',
  reply: (ctx) => {
    const p = findProject(ctx.params.id)
    if (!p) return fail(7001, '项目不存在')
    for (const t of projectTasks) {
      if (t.id !== ctx.params.taskId || t.projectId !== p.id) continue
      const me = meAsMember(ctx.token)
      const isOwner = !!me && p.ownerId === me.id
      const isAssignee = !!me && t.assigneeMemberId === me.id
      const onlyDone = ctx.body.status === 'DONE' && Object.keys(ctx.body).length === 1
      if (!isOwner && !(isAssignee && onlyDone)) return fail(7002, '仅所有人可编辑；任务负责人仅可置完成')
      if (ctx.body.status === 'DONE' && t.status !== 'DONE') {
        t.status = 'DONE'
        t.completedAt = nowText()
        t.completedByName = me ? me.name : ''
      } else if (ctx.body.status) {
        t.status = String(ctx.body.status) as MockProjectTask['status']
        if (t.status !== 'DONE') {
          t.completedAt = ''
          t.completedByName = ''
        }
      }
      if (isOwner) {
        if (ctx.body.title) t.title = String(ctx.body.title).trim()
        if (ctx.body.description !== undefined) t.description = String(ctx.body.description)
        if (ctx.body.dueDate !== undefined) t.dueDate = String(ctx.body.dueDate)
        if (ctx.body.priority) t.priority = String(ctx.body.priority)
        if (ctx.body.assigneeMemberId !== undefined) {
          t.assigneeMemberId = String(ctx.body.assigneeMemberId)
          t.assigneeName = ''
          for (const m of members) {
            if (m.id === t.assigneeMemberId) t.assigneeName = m.name
          }
        }
      }
      return ok(null)
    }
    return fail(7012, '任务不存在或不属于该项目')
  }
})

registerMock({
  method: 'delete',
  path: '/api/projects/:id/tasks/:taskId',
  reply: (ctx) => {
    const p = findProject(ctx.params.id)
    if (!p) return fail(7001, '项目不存在')
    const me = meAsMember(ctx.token)
    if (!me || p.ownerId !== me.id) return fail(7002, '仅项目所有人可操作')
    for (const t of projectTasks) {
      if (t.id === ctx.params.taskId && t.projectId === p.id) {
        projectTasks.splice(projectTasks.indexOf(t), 1)
        return ok(null)
      }
    }
    return fail(7012, '任务不存在或不属于该项目')
  }
})

