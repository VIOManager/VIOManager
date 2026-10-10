/**
 * 赛队 mock 的视图与校验辅助（从 mock/team.ts 拆出，控制单文件行数）
 * 职责：队伍查找 / 登录人视角判定（TE02）/ 列表与详情转换（含成员字段视角裁剪）/ 比赛规则数字键取数
 */
import { findMockUserByToken } from './auth'
import { meAsMember } from './member'
import { contests, templates } from './contest-data'
import { teams } from './team-data'
import type { MockTeam, MockTeamMember } from './team-data'
import type { TeamDetail, TeamListItem, TeamMemberItem } from '@/api/team'

export function findTeam(id: string): MockTeam | null {
  for (const t of teams) {
    if (t.id === id) return t
  }
  return null
}

/** 视角判定：非学生角色一律 ADMIN；学生按 队长/指导教师/在队成员 判定，非本队返回 null（→4008） */
export function viewerRoleOf(t: MockTeam, token: string): TeamDetail['viewerRole'] | null {
  const u = findMockUserByToken(token)
  if (!u) return null
  if (u.role !== 'STUDENT') return 'ADMIN'
  const me = meAsMember(token)
  if (!me) return null
  if (t.leaderId === me.id) return 'LEADER'
  for (const a of t.advisors) {
    if (a.memberId === me.id) return 'ADVISOR'
  }
  for (const m of t.members) {
    if (m.memberId === me.id && (m.status === 'CONFIRMED' || m.status === 'UNCONFIRMED')) return 'MEMBER'
  }
  return null
}

export function toListItem(t: MockTeam): TeamListItem {
  const active = t.members.filter((m) => m.status === 'CONFIRMED' || m.status === 'UNCONFIRMED')
  return {
    id: t.id, name: t.name, projectName: t.projectName, contestId: t.contestId, contestName: t.contestName,
    trackName: t.trackName, leaderName: t.leaderName, memberCount: active.length, advisorCount: t.advisors.length,
    status: t.status, materialStatus: t.materialStatus, materialCompleteRate: t.materialCompleteRate
  }
}

/** 成员行按视角裁剪：管理/队长/指导教师全量；队员仅本人全量，他人仅姓名/角色/状态 */
function memberView(m: MockTeamMember, vr: TeamDetail['viewerRole'] | null, meId: string): TeamMemberItem {
  const full = vr === 'ADMIN' || vr === 'LEADER' || vr === 'ADVISOR' || m.memberId === meId
  if (full) {
    return { memberId: m.memberId, name: m.name, role: m.role, status: m.status, college: m.college, className: m.className, phone: m.phone, idCardLast4: m.idCardLast4 }
  }
  return { memberId: m.memberId, name: m.name, role: m.role, status: m.status }
}

export function toDetail(t: MockTeam, token: string): TeamDetail {
  const vr = viewerRoleOf(t, token)
  const me = meAsMember(token)
  const meId = me ? me.id : ''
  return {
    id: t.id, name: t.name, projectName: t.projectName, contestId: t.contestId, contestName: t.contestName,
    trackId: t.trackId, trackName: t.trackName, leaderId: t.leaderId, leaderName: t.leaderName,
    status: t.status, materialStatus: t.materialStatus, materialCompleteRate: t.materialCompleteRate,
    members: t.members.filter((m) => m.status !== 'REMOVED' && m.status !== 'EXITED').map((m) => memberView(m, vr, meId)),
    advisors: t.advisors, createdAt: t.createdAt, viewerRole: vr ?? 'MEMBER'
  }
}

/** 比赛合并规则取值：模板 defaultRules → 比赛 overrideRules（组队校验不用赛道级覆盖） */
function mergedRule(contestId: string, key: string): number | boolean | undefined {
  for (const c of contests) {
    if (c.id !== contestId) continue
    if (Object.prototype.hasOwnProperty.call(c.overrideRules, key)) return c.overrideRules[key]
    for (const tp of templates) {
      if (tp.id === c.templateId && Object.prototype.hasOwnProperty.call(tp.defaultRules, key)) {
        return tp.defaultRules[key]
      }
    }
    return undefined
  }
  return undefined
}

/** 数字键（memberLimit / prefillLimit）；prefillLimit 三人队默认 2（lzh 2026-10-10 拍板） */
export function contestRuleNumber(contestId: string, key: string, fallback: number): number {
  const v = mergedRule(contestId, key)
  return typeof v === 'number' ? v : fallback
}

/** 布尔键（allowRepeatJoin / crossTeamLeader 等），未配置按 false 处理 */
export function contestRuleBool(contestId: string, key: string): boolean {
  return mergedRule(contestId, key) === true
}
