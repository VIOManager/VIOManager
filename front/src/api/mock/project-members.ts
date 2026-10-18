/**
 * 项目管理 mock 接口层·成员与指导教师（PR06–PR10，从 project.ts 拆出控制行数）
 * 不走审批（PRD §3.9 审批清单不含项目加人）；写操作仅项目所有人（7002）
 */
import { fail, ok, registerMock } from './registry'
import { meAsMember } from './member'
import { members } from './member-data'
import { nowText } from './approval-data'
import { projectAdvisors, projectMembers, visibleTo } from './project-data'
import { findProject, viewCtx } from './project'
// ── PR06–PR10 成员与指导教师（不走审批）──

registerMock({
  method: 'get',
  path: '/api/projects/:id/members',
  reply: (ctx) => {
    const p = findProject(ctx.params.id)
    if (!p) return fail(7001, '项目不存在')
    if (!visibleTo(p, viewCtx(ctx.token))) return fail(7003, '无权查看该项目')
    // 出参对齐真后端契约：memberName/projectRole/joinAt（内部存量是 name/role/joinedAt）
    return ok(projectMembers.filter((m) => m.projectId === p.id).map((m) => ({
      memberId: m.memberId,
      memberName: m.name,
      projectRole: m.role,
      joinAt: m.joinedAt
    })))
  }
})

registerMock({
  method: 'post',
  path: '/api/projects/:id/members',
  reply: (ctx) => {
    const p = findProject(ctx.params.id)
    if (!p) return fail(7001, '项目不存在')
    const vc = viewCtx(ctx.token)
    if (!vc.meId || p.ownerId !== vc.meId) return fail(7002, '仅项目所有人可操作')
    const memberId = String(ctx.body.memberId ?? '')
    const target = members.find((m) => m.id === memberId)
    if (!target) return fail(1004, '人员不存在')
    for (const m of projectMembers) {
      if (m.projectId === p.id && m.memberId === memberId) return fail(7005, '该人员已是项目成员')
    }
    projectMembers.push({ projectId: p.id, memberId, name: target.name, role: 'MEMBER', joinedAt: nowText() })
    return ok(null)
  }
})

registerMock({
  method: 'delete',
  path: '/api/projects/:id/members/:memberId',
  reply: (ctx) => {
    const p = findProject(ctx.params.id)
    if (!p) return fail(7001, '项目不存在')
    const vc = viewCtx(ctx.token)
    if (!vc.meId || p.ownerId !== vc.meId) return fail(7002, '仅项目所有人可操作')
    for (const m of projectMembers) {
      if (m.projectId === p.id && m.memberId === ctx.params.memberId) {
        if (m.role === 'OWNER') return fail(7002, '所有人不可移除')
        projectMembers.splice(projectMembers.indexOf(m), 1)
        return ok(null)
      }
    }
    return fail(1004, '成员不存在')
  }
})

registerMock({
  method: 'post',
  path: '/api/projects/:id/advisors',
  reply: (ctx) => {
    const p = findProject(ctx.params.id)
    if (!p) return fail(7001, '项目不存在')
    const vc = viewCtx(ctx.token)
    if (!vc.meId || p.ownerId !== vc.meId) return fail(7002, '仅项目所有人可操作')
    const memberId = String(ctx.body.memberId ?? '')
    const target = members.find((m) => m.id === memberId)
    if (!target) return fail(1004, '人员不存在')
    if (target.memberType !== 'TEACHER') return fail(7006, '指导教师必须是教师类型人员')
    for (const a of projectAdvisors) {
      if (a.projectId === p.id && a.memberId === memberId) return fail(7005, '该教师已在项目')
    }
    projectAdvisors.push({ projectId: p.id, memberId, name: target.name, college: target.college })
    return ok(null)
  }
})

registerMock({
  method: 'delete',
  path: '/api/projects/:id/advisors/:memberId',
  reply: (ctx) => {
    const p = findProject(ctx.params.id)
    if (!p) return fail(7001, '项目不存在')
    const vc = viewCtx(ctx.token)
    if (!vc.meId || p.ownerId !== vc.meId) return fail(7002, '仅项目所有人可操作')
    for (const a of projectAdvisors) {
      if (a.projectId === p.id && a.memberId === ctx.params.memberId) {
        projectAdvisors.splice(projectAdvisors.indexOf(a), 1)
        return ok(null)
      }
    }
    return fail(1004, '指导教师不存在')
  }
})

