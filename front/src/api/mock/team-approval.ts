/**
 * 赛队审批回调（模拟后端 ApprovalHandler Bean：审批中心单据终结后按 bizType 路由至此）
 * 对应契约：《审批中心-接口清单》§5；两段式见清单 §3——TEAM_ADD_MEMBER 批准后成员以 UNCONFIRMED 入队，
 *    队员本人的确认/拒绝走赛队自己的 confirm 接口，不回审批中心开第二张单
 * TEAM_CREATE 已退役（2026-10-24 lzh 拍板队长直建免审批）：建队逻辑移至 mock/team.ts 的 POST /api/teams；
 * TEAM_REMOVE_MEMBER 同日拍板A 队长直移免审批：移除逻辑移至 mock/team.ts 的 DELETE 成员口；
 * 剩余两类（加人/指导教师）队长发起、目标人同意即终结，管理员审批仅为可选旁路（前端管理员三栏已不展示）
 * TODO(联调)：后端审批回调就绪后删除本文件
 */
import { registerApprovalHandler } from './approval-data'
import type { MockApproval } from './approval-data'
import { findTeam, violatesRepeatJoin } from './team-util'
import type { MockTeamMember } from './team-data'
import { members } from './member-data'
import type { MockMember } from './member-data'

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
