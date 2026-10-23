/**
 * 赛队审批回调（模拟后端 ApprovalHandler Bean：审批中心单据终结后按 bizType 路由至此）
 * 对应契约：《审批中心-接口清单》§5；两段式见清单 §3——TEAM_ADD_MEMBER 批准后成员以 UNCONFIRMED 入队，
 *    队员本人的确认/拒绝走赛队自己的 confirm 接口，不回审批中心开第二张单
 * TEAM_CREATE 为创建型单据：bizId 提交时为 null，批准建队后回填新队 ID（清单 §5 契约 3）
 * TODO(联调)：后端审批回调就绪后删除本文件
 */
import { nowText, registerApprovalHandler } from './approval-data'
import type { MockApproval } from './approval-data'
import { teams } from './team-data'
import type { MockTeamMember } from './team-data'
import { members } from './member-data'
import type { MockMember } from './member-data'
import { contests } from './contest-data'
import { findTeam, violatesRepeatJoin } from './team-util'

let teamSeq = 100

/** 快照解析（payloadJson 为 JSON 字符串，坏数据当空快照） */
function parsePayload(a: MockApproval): Record<string, unknown> {
  try {
    return JSON.parse(a.payloadJson || '{}') as Record<string, unknown>
  } catch {
    return {}
  }
}

function memberOf(memberId: string): MockMember | null {
  for (const m of members) {
    if (m.id === memberId) return m
  }
  return null
}

function toTeamMember(m: MockMember, role: 'LEADER' | 'MEMBER', status: MockTeamMember['status']): MockTeamMember {
  return {
    memberId: m.id, name: m.name, role, status,
    college: m.college, className: m.className, phone: m.phone, idCardLast4: m.idCardLast4
  }
}

/** 批准组队申请 → 真正建队（批准时按最新关系复核队名撞车，对应后端"审批通过时复核"） */
registerApprovalHandler('TEAM_CREATE', {
  onApproved: (a) => {
    const p = parsePayload(a)
    const contestId = String(p.contestId ?? '')
    const name = String(p.name ?? '')
    const leader = memberOf(a.applicantMemberId)
    if (!leader || !name) return
    for (const t of teams) {
      if (t.contestId === contestId && t.name === name && t.status === 'ACTIVE') return
    }
    // 4004 复核：审批在途期间申请人已确认同比赛他队且规则禁止重复参赛，不再建队（对齐 TE04 批准复核语义）
    if (violatesRepeatJoin(contestId, leader.id, '')) return
    let contestName = ''
    for (const c of contests) {
      if (c.id === contestId) contestName = c.name
    }
    const list: MockTeamMember[] = [toTeamMember(leader, 'LEADER', 'CONFIRMED')]
    const prefill = Array.isArray(p.prefillMemberIds) ? (p.prefillMemberIds as string[]) : []
    for (const pid of prefill) {
      const m = memberOf(String(pid))
      if (m && m.id !== leader.id) list.push(toTeamMember(m, 'MEMBER', 'UNCONFIRMED'))
    }
    const id = 't' + teamSeq++
    teams.push({
      id, name, projectName: String(p.projectName ?? ''), contestId, contestName,
      trackId: '', trackName: '', leaderId: leader.id, leaderName: leader.name,
      status: 'ACTIVE', materialStatus: '待完善', materialCompleteRate: 0,
      members: list, advisors: [], createdAt: nowText()
    })
    a.bizId = id
  }
})

/** 批准加人申请 → 成员以 UNCONFIRMED 入队（两段式：本人确认走赛队 confirm 接口） */
registerApprovalHandler('TEAM_ADD_MEMBER', {
  onApproved: (a) => {
    const p = parsePayload(a)
    const t = findTeam(String(p.teamId ?? ''))
    if (!t) return
    const memberId = String(p.memberId ?? '')
    for (const m of t.members) {
      if (m.memberId === memberId && (m.status === 'CONFIRMED' || m.status === 'UNCONFIRMED')) return
    }
    // 4004 复核：审批在途期间目标人已确认同比赛他队且规则禁止，不再入队（清单 TE04"审批时按最新成员关系复核"）
    if (violatesRepeatJoin(t.contestId, memberId, t.id)) return
    const target = memberOf(memberId)
    if (!target) return
    t.members.push(toTeamMember(target, 'MEMBER', 'UNCONFIRMED'))
  }
})

/** 批准移除队员 → 成员置 REMOVED */
registerApprovalHandler('TEAM_REMOVE_MEMBER', {
  onApproved: (a) => {
    const p = parsePayload(a)
    const t = findTeam(String(p.teamId ?? ''))
    if (!t) return
    const memberId = String(p.memberId ?? '')
    for (const m of t.members) {
      if (m.memberId === memberId && (m.status === 'CONFIRMED' || m.status === 'UNCONFIRMED')) m.status = 'REMOVED'
    }
  }
})

/** 批准指导教师申请 → 教师入队（防重） */
registerApprovalHandler('TEAM_ADD_ADVISOR', {
  onApproved: (a) => {
    const p = parsePayload(a)
    const t = findTeam(String(p.teamId ?? ''))
    if (!t) return
    const memberId = String(p.memberId ?? '')
    for (const ad of t.advisors) {
      if (ad.memberId === memberId) return
    }
    const target = memberOf(memberId)
    if (!target) return
    t.advisors.push({ memberId: target.id, name: target.name, college: target.college })
  }
})
