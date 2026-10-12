/**
 * 项目管理 mock 接口层·赛队挂载（PR11–PR14，从 project.ts 拆出控制行数）
 * 挂载校验：仅队长（读 B 线 team 数据，只读）/ 项目可见（§2）/ 同学期 7008 / 同队同项目不重复 7007
 */
import { fail, ok, registerMock } from './registry'
import { meAsMember } from './member'
import { nowText } from './approval-data'
import { contests } from './contest-data'
import { teams } from './team-data'
import { teamProjectRefs, visibleTo } from './project-data'
import { findProject, viewCtx } from './project'

let refSeq = 3
// ── PR11–PR14 赛队挂载 ──

function refToView(r: (typeof teamProjectRefs)[0]) {
  const t = teams.find((x) => x.id === r.teamId)
  return {
    teamId: r.teamId, teamName: t ? t.name : r.teamId,
    contestName: t ? t.contestName : '', trackName: t ? t.trackName : '',
    attachedByName: r.attachedByName, attachedAt: r.attachedAt, status: r.status,
    projectId: r.projectId
  }
}

registerMock({
  method: 'get',
  path: '/api/projects/:id/teams',
  reply: (ctx) => {
    const p = findProject(ctx.params.id)
    if (!p) return fail(7001, '项目不存在')
    if (!visibleTo(p, viewCtx(ctx.token))) return fail(7003, '无权查看该项目')
    return ok(teamProjectRefs.filter((r) => r.projectId === p.id).map((r) => {
      const t = teams.find((x) => x.id === r.teamId)
      return { teamId: r.teamId, teamName: t ? t.name : r.teamId, contestName: t ? t.contestName : '', trackName: t ? t.trackName : '', attachedByName: r.attachedByName, attachedAt: r.attachedAt, status: r.status }
    }))
  }
})

registerMock({
  method: 'get',
  path: '/api/teams/:id/projects',
  reply: (ctx) => {
    const list = []
    for (const r of teamProjectRefs) {
      if (r.teamId !== ctx.params.id || r.status !== 'ACTIVE') continue
      const p = findProject(r.projectId)
      if (!p) continue
      list.push({ ...refToView(r), name: p.name, ownerName: p.ownerName, status: r.status })
    }
    return ok(list)
  }
})

registerMock({
  method: 'post',
  path: '/api/teams/:id/projects',
  reply: (ctx) => {
    const t = teams.find((x) => x.id === ctx.params.id)
    if (!t) return fail(1004, '赛队不存在')
    const me = meAsMember(ctx.token)
    if (!me || t.leaderId !== me.id) return fail(7002, '仅该队队长可挂载项目')
    const projectId = String(ctx.body.projectId ?? '')
    const p = findProject(projectId)
    if (!p) return fail(7001, '项目不存在')
    if (!visibleTo(p, viewCtx(ctx.token))) return fail(7003, '无权查看该项目')
    // 7008 同学期：项目学期 vs 赛队比赛的学期（赛队无独立学期字段，随比赛）
    const contest = contests.find((c) => c.id === t.contestId)
    if (contest && p.semesterId !== contest.semesterId) return fail(7008, '挂载的项目与赛队不在同一学期')
    for (const r of teamProjectRefs) {
      if (r.teamId === t.id && r.projectId === projectId && r.status === 'ACTIVE') return fail(7007, '该赛队已挂载此项目')
    }
    teamProjectRefs.push({ id: 'tpr' + refSeq++, teamId: t.id, projectId, trackId: String(ctx.body.trackId ?? ''), attachedByName: me.name, attachedAt: nowText(), status: 'ACTIVE' })
    return ok(null)
  }
})

registerMock({
  method: 'delete',
  path: '/api/teams/:id/projects/:projectId',
  reply: (ctx) => {
    const t = teams.find((x) => x.id === ctx.params.id)
    if (!t) return fail(1004, '赛队不存在')
    const me = meAsMember(ctx.token)
    if (!me || t.leaderId !== me.id) return fail(7002, '仅该队队长可解绑')
    for (const r of teamProjectRefs) {
      if (r.teamId === t.id && r.projectId === ctx.params.projectId && r.status === 'ACTIVE') {
        r.status = 'RELEASED'
        return ok(null)
      }
    }
    return fail(1004, '挂载关系不存在')
  }
})
