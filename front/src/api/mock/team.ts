/**
 * 赛队 mock（B 线 TE01–TE13）
 * ⚠️ 演示：组队/加人/移除不直接生效，写入 mock 审批单（approval.ts）；队员确认直接流转（演示 UNCONFIRMED→CONFIRMED）
 * 视角裁剪（TE02/TE11）：管理端/队长/指导教师见全字段；普通队员仅本人全字段+他人公开字段；非本队 4008
 * 校验：4002 队名同比赛重复 / 4003 超上限 / 4004 重复参赛仅拦已确认且规则禁止时 / 4005 已在队 / 4006 非教师 / 4009 有阻断拒解散 / 4010 队长不可移除
 * 辅助函数（查找/视角判定/列表详情转换/规则取数）拆在 mock/team-util.ts
 * TODO(联调)：后端赛队接口就绪后删除本文件；审批复核时序漏洞由后端在审批通过时按最新关系复核
 */
import { fail, ok, registerMock } from './registry'
import { findMockUserByToken } from './auth'
import { meAsMember } from './member'
import { contests } from './contest-data'
import { pushApproval } from './approval'
import { teams } from './team-data'
import { contestRuleBool, contestRuleNumber, findTeam, toDetail, toListItem, viewerRoleOf } from './team-util'
import { teamVisible } from './scope'
import type { TeamListItem, TeamMemberSearchItem } from '@/api/team'
import { members } from './member-data'

registerMock({
  method: 'get',
  path: '/api/teams',
  reply: (ctx) => {
    const u = findMockUserByToken(ctx.token)
    const me = meAsMember(ctx.token)
    const contestId = String(ctx.query.contestId ?? '')
    const keyword = String(ctx.query.keyword ?? '').trim()
    const out: TeamListItem[] = []
    for (const t of teams) {
      if (contestId && t.contestId !== contestId) continue
      if (keyword && t.name.indexOf(keyword) < 0 && t.projectName.indexOf(keyword) < 0) continue
      // 部门管理员：仅本部门子树队伍（按队长组织归属）
      if (!teamVisible(t.leaderId, ctx.token)) continue
      // 学生仅见自己所在的队伍
      if (u && u.role === 'STUDENT') {
        if (!me) continue
        let inTeam = false
        for (const m of t.members) {
          if (m.memberId === me.id && (m.status === 'CONFIRMED' || m.status === 'UNCONFIRMED')) inTeam = true
        }
        if (!inTeam) continue
      }
      out.push(toListItem(t))
    }
    return ok(out)
  }
})

registerMock({
  method: 'get',
  path: '/api/teams/:id',
  reply: (ctx) => {
    const t = findTeam(ctx.params.id)
    if (!t) return fail(1004, '赛队不存在')
    if (!viewerRoleOf(t, ctx.token)) return fail(4008, '非本队成员，禁止访问赛队详情')
    return ok(toDetail(t, ctx.token))
  }
})

registerMock({
  method: 'post',
  path: '/api/teams',
  reply: (ctx) => {
    const contestId = String(ctx.body.contestId ?? '')
    const name = String(ctx.body.name ?? '').trim()
    if (!name) return fail(1001, '队名不能为空')
    let contest = null
    for (const c of contests) {
      if (c.id === contestId) contest = c
    }
    if (!contest) return fail(1004, '比赛不存在')
    if (contest.status !== 'REGISTERING') return fail(4001, '比赛当前不在报名期')
    for (const t of teams) {
      if (t.contestId === contestId && t.name === name && t.status === 'ACTIVE') return fail(4002, '队名已存在')
    }
    const me = meAsMember(ctx.token)
    // 4004：比赛规则不允许重复参赛（allowRepeatJoin=否）时，本人已"确认"参加同比赛其他队伍才拦截；
    // 待确认不算已报名、规则允许重复参赛则完全不拦（lzh 2026-10-10 拍板）
    if (me && !contestRuleBool(contestId, 'allowRepeatJoin')) {
      for (const t of teams) {
        if (t.contestId !== contestId || t.status !== 'ACTIVE') continue
        for (const m of t.members) {
          if (m.memberId === me.id && m.status === 'CONFIRMED') {
            return fail(4004, '该比赛不允许重复参赛（allowRepeatJoin=否）')
          }
        }
      }
    }
    const prefill = Array.isArray(ctx.body.prefillMemberIds) ? (ctx.body.prefillMemberIds as string[]) : []
    const prefillLimit = contestRuleNumber(contestId, 'prefillLimit', 2)
    if (prefill.length > prefillLimit) return fail(4003, '预填成员数超过上限（' + prefillLimit + '）')
    const ap = pushApproval({
      bizType: 'TEAM_CREATE', bizId: contestId + ':' + name,
      title: (me ? me.name : '未知') + ' · 创建赛队「' + name + '」',
      detail: '比赛：' + contest.name + '；项目：' + String(ctx.body.projectName ?? ''),
      applicantName: me ? me.name : '未知'
    })
    return ok({ approvalId: ap.id })
  }
})

registerMock({
  method: 'put',
  path: '/api/teams/:id',
  reply: (ctx) => {
    const t = findTeam(ctx.params.id)
    if (!t) return fail(1004, '赛队不存在')
    const name = String(ctx.body.name ?? '').trim()
    if (name && name !== t.name) {
      for (const o of teams) {
        if (o.contestId === t.contestId && o.name === name && o.status === 'ACTIVE') return fail(4002, '队名已存在')
      }
      t.name = name
    }
    t.projectName = String(ctx.body.projectName ?? t.projectName)
    return ok(null)
  }
})

registerMock({
  method: 'get',
  path: '/api/teams/:id/members/search',
  reply: (ctx) => {
    const t = findTeam(ctx.params.id)
    if (!t) return fail(1004, '赛队不存在')
    const keyword = String(ctx.query.keyword ?? '').trim()
    const out: TeamMemberSearchItem[] = []
    for (const m of members) {
      if (keyword && m.name.indexOf(keyword) < 0 && m.studentNo.indexOf(keyword) < 0) continue
      let inTeam = false
      for (const tm of t.members) {
        if (tm.memberId === m.id && (tm.status === 'CONFIRMED' || tm.status === 'UNCONFIRMED')) inTeam = true
      }
      out.push({ memberId: m.id, name: m.name, studentNo: m.studentNo, college: m.college, inTeam })
    }
    return ok(out.slice(0, 10))
  }
})

registerMock({
  method: 'post',
  path: '/api/teams/:id/members',
  reply: (ctx) => {
    const t = findTeam(ctx.params.id)
    if (!t) return fail(1004, '赛队不存在')
    const memberId = String(ctx.body.memberId ?? '')
    const active = t.members.filter((m) => m.status === 'CONFIRMED' || m.status === 'UNCONFIRMED')
    const limit = contestRuleNumber(t.contestId, 'memberLimit', 3)
    if (active.length >= limit) return fail(4003, '队伍人数超上限（' + limit + '）')
    for (const m of active) {
      if (m.memberId === memberId) return fail(4005, '目标人已在队或已有待审批申请')
    }
    let target = null
    for (const m of members) {
      if (m.id === memberId) target = m
    }
    if (!target) return fail(1004, '目标人不存在')
    pushApproval({
      bizType: 'TEAM_ADD_MEMBER', bizId: t.id,
      title: t.leaderName + ' · ' + t.name + '申请加人：' + target.name,
      detail: '申请将' + target.name + '加入「' + t.name + '」',
      applicantName: t.leaderName, targetMemberId: memberId
    })
    return ok(null)
  }
})

registerMock({
  method: 'post',
  path: '/api/teams/:id/members/:memberId/confirm',
  reply: (ctx) => {
    const t = findTeam(ctx.params.id)
    if (!t) return fail(1004, '赛队不存在')
    const me = meAsMember(ctx.token)
    for (const m of t.members) {
      if (m.memberId !== ctx.params.memberId) continue
      if (!me || me.id !== m.memberId) return fail(1003, '只能本人确认')
      if (m.status !== 'UNCONFIRMED') return fail(1001, '当前状态不可确认')
      m.status = String(ctx.body.action) === 'REJECT' ? 'REJECTED' : 'CONFIRMED'
      return ok(null)
    }
    return fail(1004, '成员不存在')
  }
})

registerMock({
  method: 'delete',
  path: '/api/teams/:id/members/:memberId',
  reply: (ctx) => {
    const t = findTeam(ctx.params.id)
    if (!t) return fail(1004, '赛队不存在')
    for (const m of t.members) {
      if (m.memberId !== ctx.params.memberId) continue
      if (m.role === 'LEADER') return fail(4010, '队长不可自我移除或退出')
      pushApproval({
        bizType: 'TEAM_REMOVE_MEMBER', bizId: t.id,
        title: t.leaderName + ' · ' + t.name + '申请移除队员：' + m.name,
        detail: '申请将' + m.name + '移出「' + t.name + '」',
        applicantName: t.leaderName
      })
      return ok(null)
    }
    return fail(1004, '成员不存在')
  }
})

registerMock({
  method: 'post',
  path: '/api/teams/:id/exit',
  reply: (ctx) => {
    const t = findTeam(ctx.params.id)
    if (!t) return fail(1004, '赛队不存在')
    const me = meAsMember(ctx.token)
    for (const m of t.members) {
      if (me && m.memberId === me.id) {
        if (m.role === 'LEADER') return fail(4010, '队长不可自我移除或退出')
        m.status = 'EXITED'
        return ok(null)
      }
    }
    return fail(1004, '你不在该队伍中')
  }
})

registerMock({
  method: 'post',
  path: '/api/teams/:id/dissolve',
  reply: (ctx) => {
    const t = findTeam(ctx.params.id)
    if (!t) return fail(1004, '赛队不存在')
    if (t.hasBlocking) return fail(4009, '队伍存在材料/成果/待审批，禁止解散')
    t.status = 'DISSOLVED'
    return ok(null)
  }
})

registerMock({
  method: 'post',
  path: '/api/teams/:id/advisors',
  reply: (ctx) => {
    const t = findTeam(ctx.params.id)
    if (!t) return fail(1004, '赛队不存在')
    const memberId = String(ctx.body.memberId ?? '')
    let target = null
    for (const m of members) {
      if (m.id === memberId) target = m
    }
    if (!target) return fail(1004, '目标人不存在')
    if (target.memberType !== 'TEACHER') return fail(4006, '目标人不是教师类型')
    for (const a of t.advisors) {
      if (a.memberId === memberId) return fail(4005, '该教师已在队')
    }
    pushApproval({
      bizType: 'TEAM_ADD_ADVISOR', bizId: t.id,
      title: t.leaderName + ' · ' + t.name + '申请指导教师：' + target.name,
      detail: '申请教师' + target.name + '指导「' + t.name + '」',
      applicantName: t.leaderName
    })
    return ok(null)
  }
})

registerMock({
  method: 'delete',
  path: '/api/teams/:id/advisors/:advisorId',
  reply: (ctx) => {
    const t = findTeam(ctx.params.id)
    if (!t) return fail(1004, '赛队不存在')
    for (const a of t.advisors) {
      if (a.memberId === ctx.params.advisorId) {
        t.advisors.splice(t.advisors.indexOf(a), 1)
        return ok(null)
      }
    }
    return fail(1004, '指导教师不存在')
  }
})
