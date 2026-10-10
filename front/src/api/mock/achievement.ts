/**
 * 成果 mock（C 线 A01–A08）
 * 流程：学生队长提交 → PENDING + ACHIEVEMENT_SUBMIT 审批单；管理员录入直接 APPROVED；
 *    审批通过触发 §5 联动——材料任务自动补"佐证材料"必交项并重算齐全率（PRD §3.7/§3.8）
 * 校验：5007 仅队长可提交或传佐证 / 5008 队伍有未通过成果时禁导出获奖清单
 * TODO(联调)：后端成果接口就绪后删除本文件
 */
import { fail, ok, registerMock } from './registry'
import { findMockUserByToken } from './auth'
import { meAsMember } from './member'
import { teams } from './team-data'
import { achievements, nextAchId } from './achievement-data'
import { approvals, pushApproval } from './approval'
import { allTasks } from './material'
import { nextReqId, requirements } from './material-data'
import { recomputeTeam } from './material-submit'
import type { AchievementItem, AchievementType } from '@/api/achievement'
import type { MockAchievement } from './achievement-data'

const VALID_TYPES = ['AWARD', 'RATING', 'QUALIFICATION', 'ACTIVITY', 'OTHER']

function findTeamById(id: string) {
  for (const t of teams) {
    if (t.id === id) return t
  }
  return null
}

function findAch(id: string): MockAchievement | null {
  for (const a of achievements) {
    if (a.id === id) return a
  }
  return null
}

function toItem(a: MockAchievement): AchievementItem {
  const tm = findTeamById(a.teamId)
  return {
    id: a.id, teamId: a.teamId, teamName: tm ? tm.name : '（已解散）', type: a.type,
    name: a.name, level: a.level, achievedAt: a.achievedAt, status: a.status,
    submittedBy: a.submittedBy, hasEvidence: !!a.evidenceFileId
  }
}

/** 当前登录人是否某队队长 */
function isLeaderOf(tmId: string, memberId: string): boolean {
  const tm = findTeamById(tmId)
  return !!tm && tm.leaderId === memberId
}

/** 当前登录人是否某队在册成员 */
function isActiveMemberOf(tmId: string, memberId: string): boolean {
  const tm = findTeamById(tmId)
  if (!tm) return false
  for (const m of tm.members) {
    if (m.memberId === memberId && (m.status === 'CONFIRMED' || m.status === 'UNCONFIRMED')) return true
  }
  return false
}

/** §5 联动：审批通过的成果 → 所在比赛材料任务补一条"佐证材料"必交项并重算齐全率 */
function applyEvidenceLinkage(teamId: string): void {
  const tm = findTeamById(teamId)
  if (!tm) return
  let task = null
  for (const t of allTasks()) {
    if (t.contestId === tm.contestId) task = t
  }
  if (!task) return
  let hasEvidence = false
  for (const r of requirements) {
    if (r.taskId === task.id && r.type === 'EVIDENCE') hasEvidence = true
  }
  if (!hasEvidence) {
    requirements.push({
      id: nextReqId(), taskId: task.id, type: 'EVIDENCE', required: true,
      formats: ['pdf', 'jpg', 'zip'], namingRule: '队名-成果名-佐证', maxSizeMb: 20
    })
  }
  recomputeTeam(teamId)
}

/** 审批通过/驳回后同步审批单状态（审批中心列表联动） */
function flipApproval(achId: string, status: 'APPROVED' | 'REJECTED'): void {
  for (const ap of approvals) {
    if (ap.bizType === 'ACHIEVEMENT_SUBMIT' && ap.bizId === achId) ap.status = status
  }
}

registerMock({
  method: 'get',
  path: '/api/achievements',
  reply: (ctx) => {
    const u = findMockUserByToken(ctx.token)
    const teamId = String(ctx.query.teamId ?? '')
    const status = String(ctx.query.status ?? '')
    const keyword = String(ctx.query.keyword ?? '').trim()
    const me = meAsMember(ctx.token)
    // 学生仅见本人所在队伍的成果（PRD §3.8 数据可见性）
    const myTeamIds: string[] = []
    if (u && u.role === 'STUDENT' && me) {
      for (const tm of teams) {
        if (tm.status === 'ACTIVE' && isActiveMemberOf(tm.id, me.id)) myTeamIds.push(tm.id)
      }
    }
    const out: AchievementItem[] = []
    for (const a of achievements) {
      if (teamId && a.teamId !== teamId) continue
      if (status && a.status !== status) continue
      if (keyword && a.name.indexOf(keyword) < 0 && a.level.indexOf(keyword) < 0) continue
      if (myTeamIds.length > 0 && myTeamIds.indexOf(a.teamId) < 0) continue
      out.push(toItem(a))
    }
    return ok(out)
  }
})

registerMock({
  method: 'get',
  path: '/api/achievements/:id',
  reply: (ctx) => {
    const a = findAch(ctx.params.id)
    if (!a) return fail(1004, '成果不存在')
    const u = findMockUserByToken(ctx.token)
    const me = meAsMember(ctx.token)
    const privileged = (u && u.role !== 'STUDENT') || (me && isActiveMemberOf(a.teamId, me.id))
    if (!privileged) return fail(5007, '仅本队成员与管理端可查看成果详情')
    return ok({
      ...toItem(a), description: a.description, evidenceFileId: a.evidenceFileId,
      evidenceFileName: a.evidenceFileName, createdAt: a.createdAt,
      viewerIsLeader: !!me && isLeaderOf(a.teamId, me.id)
    })
  }
})

registerMock({
  method: 'post',
  path: '/api/achievements',
  reply: (ctx) => {
    const teamId = String(ctx.body.teamId ?? '')
    const type = String(ctx.body.type ?? '')
    const name = String(ctx.body.name ?? '').trim()
    const level = String(ctx.body.level ?? '').trim()
    const achievedAt = String(ctx.body.achievedAt ?? '')
    if (VALID_TYPES.indexOf(type) < 0) return fail(1001, '成果类型不合法')
    if (!name || !level || !achievedAt) return fail(1001, '成果名称、级别与时间不能为空')
    const tm = findTeamById(teamId)
    if (!tm || tm.status !== 'ACTIVE') return fail(1004, '赛队不存在或已解散')
    const u = findMockUserByToken(ctx.token)
    const me = meAsMember(ctx.token)
    // 学生队长提交走审批；管理员录入直接生效（PRD §3.8）
    let status: 'PENDING' | 'APPROVED' = 'PENDING'
    if (u && u.role !== 'STUDENT') {
      status = 'APPROVED'
    } else {
      if (!me || !isLeaderOf(teamId, me.id)) return fail(5007, '仅队长可提交成果')
    }
    const ach: MockAchievement = {
      id: nextAchId(), teamId, type: type as AchievementType, name, level, achievedAt,
      description: String(ctx.body.description ?? ''), status,
      submittedBy: me ? me.name : (u ? u.realName : '未知'),
      evidenceFileId: String(ctx.body.evidenceFileId ?? ''), evidenceFileName: String(ctx.body.evidenceFileName ?? ''),
      createdAt: '2026-10-10 14:00:00'
    }
    achievements.push(ach)
    if (status === 'PENDING') {
      pushApproval({
        bizType: 'ACHIEVEMENT_SUBMIT', bizId: ach.id,
        title: ach.submittedBy + ' · ' + tm.name + '提交成果：' + ach.name,
        detail: ach.level + ' · 待管理员审批', applicantName: ach.submittedBy
      })
    } else {
      applyEvidenceLinkage(teamId)
    }
    return ok({ id: ach.id, status })
  }
})

registerMock({
  method: 'post',
  path: '/api/achievements/:id/review',
  reply: (ctx) => {
    const a = findAch(ctx.params.id)
    if (!a) return fail(1004, '成果不存在')
    const u = findMockUserByToken(ctx.token)
    if (!u || u.role === 'STUDENT') return fail(1003, '仅管理端可审批成果')
    if (a.status !== 'PENDING') return fail(1001, '该成果不在待审批状态')
    if (String(ctx.body.action) === 'APPROVE') {
      a.status = 'APPROVED'
      flipApproval(a.id, 'APPROVED')
      applyEvidenceLinkage(a.teamId)
      return ok(null)
    }
    a.status = 'REJECTED'
    flipApproval(a.id, 'REJECTED')
    return ok(null)
  }
})

registerMock({
  method: 'put',
  path: '/api/achievements/:id/evidence',
  reply: (ctx) => {
    const a = findAch(ctx.params.id)
    if (!a) return fail(1004, '成果不存在')
    const me = meAsMember(ctx.token)
    const u = findMockUserByToken(ctx.token)
    // 佐证覆盖式上传仅限当前队长（PRD §3.8）
    if (u && u.role === 'STUDENT' && (!me || !isLeaderOf(a.teamId, me.id))) {
      return fail(5007, '佐证材料仅限当前队长上传')
    }
    a.evidenceFileId = String(ctx.body.fileId ?? '')
    a.evidenceFileName = String(ctx.body.fileName ?? '')
    return ok(null)
  }
})

registerMock({
  method: 'delete',
  path: '/api/achievements/:id',
  reply: (ctx) => {
    const a = findAch(ctx.params.id)
    if (!a) return fail(1004, '成果不存在')
    if (a.status === 'APPROVED') return fail(1001, '已通过的成果不可删除')
    achievements.splice(achievements.indexOf(a), 1)
    for (let i = approvals.length - 1; i >= 0; i--) {
      if (approvals[i].bizType === 'ACHIEVEMENT_SUBMIT' && approvals[i].bizId === a.id) approvals.splice(i, 1)
    }
    return ok(null)
  }
})

registerMock({
  method: 'post',
  path: '/api/achievements/export',
  reply: (ctx) => {
    const teamId = String(ctx.body.teamId ?? '')
    const tm = findTeamById(teamId)
    if (!tm) return fail(1004, '赛队不存在')
    // 5008：获奖清单为正式凭据，队伍存在未通过（待审批/已驳回）成果时禁止导出
    for (const a of achievements) {
      if (a.teamId === teamId && a.status !== 'APPROVED') return fail(5008, '该队伍存在未通过审批的成果，通过后再导出')
    }
    const count = achievements.filter((a) => a.teamId === teamId && a.type === 'AWARD').length
    return ok({ note: '「' + tm.name + '」获奖清单导出任务已生成（共 ' + count + ' 项获奖，mock 演示）' })
  }
})

registerMock({
  method: 'get',
  path: '/api/my/achievements',
  reply: (ctx) => {
    const me = meAsMember(ctx.token)
    if (!me) return ok({ teams: [], items: [] })
    const myTeams: Array<{ teamId: string; teamName: string; isLeader: boolean }> = []
    for (const tm of teams) {
      if (tm.status === 'ACTIVE' && isActiveMemberOf(tm.id, me.id)) {
        myTeams.push({ teamId: tm.id, teamName: tm.name, isLeader: tm.leaderId === me.id })
      }
    }
    const items = achievements.filter((a) => myTeams.some((t) => t.teamId === a.teamId)).map(toItem)
    return ok({ teams: myTeams, items })
  }
})
