/**
 * 项目管理 mock 接口层（PR01–PR15 主体；PR16–PR31 在 ./project-ext.ts）
 * 契约：《项目管理-接口清单》（错误码 7xxx）；可见性判定在 ./project-data 的 visibleTo（§2 八条）
 * 写操作=所有人或管理员（2026-10-16 拍板，7002）；PR13 挂载校验队长身份（读 B 线 team 数据，只读）
 * TODO(联调)：后端项目接口就绪后删除本文件
 */
import { fail, ok, registerMock } from './registry'
import { findMockUserByToken } from './auth'
import { meAsMember } from './member'
import { deptScopeOrgIds } from './scope'
import { tree } from './org-data'
import { semesters } from './semester'
import { contests } from './contest-data'
import { usedIdemKeys, nowText } from './approval-data'
import { projectMembers, projects, teamProjectRefs, viewGrants, visibleTo } from './project-data'
import type { MockProject } from './project-data'
import type { ProjectItem } from '@/api/project'

let projectSeq = 5
let refSeq = 3

/** 组织子树 id 集（供可见性第 4/6 条） */
export function orgSubtreeOf(orgId: string): string[] {
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

/** 当前登录人可见性上下文 */
export function viewCtx(token: string) {
  const u = findMockUserByToken(token)
  const me = meAsMember(token)
  return {
    isSuper: !!u && u.role === 'SUPER_ADMIN',
    isDeptAdmin: !!u && u.role === 'DEPT_ADMIN',
    meId: me ? me.id : '',
    meOrgId: me ? me.orgId : '',
    deptScope: deptScopeOrgIds(token),
    orgSubtreeOf
  }
}

export function findProject(id: string): MockProject | null {
  for (const p of projects) {
    if (p.id === id) return p
  }
  return null
}

/** 管理权（2026-10-16 拍板：所有人 or 超管 or 子树内部门管理员）——与真后端 canManage 同口径 */
export function canManageMock(p: MockProject, vc: ReturnType<typeof viewCtx>): boolean {
  return (!!vc.meId && p.ownerId === vc.meId) || vc.isSuper
    || (vc.isDeptAdmin && !!p.orgId && (vc.deptScope?.indexOf(p.orgId) ?? -1) >= 0)
}

function toItem(p: MockProject): ProjectItem {
  return {
    id: p.id, name: p.name, ownerName: p.ownerName, orgName: p.orgName, semesterName: p.semesterName,
    contestName: p.contestName, status: p.status, visibility: p.visibility, startDate: p.startDate, endDate: p.endDate
  }
}

registerMock({
  method: 'get',
  path: '/api/projects',
  reply: (ctx) => {
    const vc = viewCtx(ctx.token)
    const keyword = String(ctx.query.keyword ?? '').trim()
    const status = String(ctx.query.status ?? '')
    const contestId = String(ctx.query.contestId ?? '')
    const out: ProjectItem[] = []
    for (const p of projects) {
      if (!visibleTo(p, vc)) continue
      if (keyword && p.name.indexOf(keyword) < 0) continue
      if (status && p.status !== status) continue
      if (contestId && p.contestId !== contestId) continue
      out.push(toItem(p))
    }
    return ok(out)
  }
})

registerMock({
  method: 'post',
  path: '/api/projects',
  reply: (ctx) => {
    if (!ctx.idemKey) return fail(1001, '缺少幂等键（X-Idempotency-Key）')
    if (usedIdemKeys.has(ctx.idemKey)) return fail(1005, '请勿重复提交')
    const me = meAsMember(ctx.token)
    if (!me) return fail(1003, '仅成员可创建项目')
    const name = String(ctx.body.name ?? '').trim()
    if (!name) return fail(1001, '项目名称不能为空')
    const sem = semesters.find((s) => s.status === 'ACTIVE') ?? semesters[1]
    const orgId = String(ctx.body.orgId ?? me.orgId)
    // 7004 同学期同组织内项目名唯一
    for (const p of projects) {
      if (p.semesterId === sem.id && p.orgId === orgId && p.name === name) return fail(7004, '同学期同组织内项目名已存在')
    }
    usedIdemKeys.add(ctx.idemKey)
    const contest = contests.find((c) => c.id === String(ctx.body.contestId ?? ''))
    const p: MockProject = {
      id: 'p' + projectSeq++, name, description: String(ctx.body.description ?? ''),
      remark: String(ctx.body.remark ?? ''), ownerId: me.id, ownerName: me.name,
      orgId, orgName: me.orgName, semesterId: sem.id, semesterName: sem.name,
      contestId: contest ? contest.id : '', contestName: contest ? contest.name : '', trackId: String(ctx.body.trackId ?? ''),
      status: 'ACTIVE', visibility: (String(ctx.body.visibility ?? 'ORG') as MockProject['visibility']),
      startDate: String(ctx.body.startDate ?? ''), endDate: String(ctx.body.endDate ?? '')
    }
    projects.push(p)
    projectMembers.push({ projectId: p.id, memberId: me.id, name: me.name, role: 'OWNER', joinedAt: nowText() })
    return ok({ id: p.id })
  }
})

registerMock({
  method: 'get',
  path: '/api/projects/attachable',
  reply: (ctx) => {
    const vc = viewCtx(ctx.token)
    const contestId = String(ctx.query.contestId ?? '')
    const out: ProjectItem[] = []
    for (const p of projects) {
      if (p.status !== 'ACTIVE') continue
      // 可挂载池：PUBLIC / ORG 同部门 / 自己参与的
      const involved = vc.meId && (p.ownerId === vc.meId || projectMembers.some((m) => m.projectId === p.id && m.memberId === vc.meId))
      const shared = p.visibility === 'PUBLIC' || (p.visibility === 'ORG' && vc.meOrgId && orgSubtreeOf(p.orgId).indexOf(vc.meOrgId) >= 0)
      if (!involved && !shared) continue
      if (contestId && p.contestId !== contestId) continue
      out.push(toItem(p))
    }
    return ok(out)
  }
})

registerMock({
  method: 'get',
  path: '/api/projects/:id',
  reply: (ctx) => {
    const p = findProject(ctx.params.id)
    if (!p) return fail(7001, '项目不存在')
    const vc = viewCtx(ctx.token)
    if (!visibleTo(p, vc)) return fail(7003, '无权查看该项目')
    // ONCE 授权消耗（命中第 7 条且非本人涉及时）
    if (vc.meId && p.ownerId !== vc.meId && !projectMembers.some((m) => m.projectId === p.id && m.memberId === vc.meId)) {
      for (const g of viewGrants) {
        if (g.projectId === p.id && g.applicantMemberId === vc.meId && g.status === 'ACTIVE' && g.grantType === 'ONCE') {
          g.usedCount++
          if (g.usedCount >= 1) g.status = 'EXPIRED'
        }
      }
    }
    return ok({
      ...toItem(p), description: p.description, remark: p.remark,
      // 出参对齐真后端 PR03：卡片字段 + description/remark + viewerIsOwner/viewerCanManage
      viewerIsOwner: !!vc.meId && p.ownerId === vc.meId,
      viewerCanManage: canManageMock(p, vc)
    })
  }
})

registerMock({
  method: 'put',
  path: '/api/projects/:id',
  reply: (ctx) => {
    const p = findProject(ctx.params.id)
    if (!p) return fail(7001, '项目不存在')
    const vc = viewCtx(ctx.token)
    if (!canManageMock(p, vc)) return fail(7002, '仅项目所有人或管理员可操作')
    if (ctx.body.name) p.name = String(ctx.body.name).trim()
    if (ctx.body.description !== undefined) p.description = String(ctx.body.description)
    if (ctx.body.remark !== undefined) p.remark = String(ctx.body.remark)
    if (ctx.body.visibility) p.visibility = String(ctx.body.visibility) as MockProject['visibility']
    // 状态按序流转 ACTIVE→FINISHED→ARCHIVED
    const next = String(ctx.body.status ?? '')
    if (next) {
      const order = ['ACTIVE', 'FINISHED', 'ARCHIVED']
      if (order.indexOf(next) <= order.indexOf(p.status)) return fail(1001, '项目状态只能按序流转（进行中→已完结→已归档）')
      p.status = next as MockProject['status']
    }
    return ok(null)
  }
})

registerMock({
  method: 'delete',
  path: '/api/projects/:id',
  reply: (ctx) => {
    const p = findProject(ctx.params.id)
    if (!p) return fail(7001, '项目不存在')
    const vc = viewCtx(ctx.token)
    if (!canManageMock(p, vc)) return fail(7002, '仅项目所有人或管理员可操作')
    for (const r of teamProjectRefs) {
      if (r.projectId === p.id && r.status === 'ACTIVE') return fail(7010, '项目有挂载中的赛队，请先解绑')
    }
    const i = projects.indexOf(p)
    if (i >= 0) projects.splice(i, 1)
    return ok(null)
  }
})
