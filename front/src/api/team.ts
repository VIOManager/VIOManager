/**
 * 赛队接口（B 线 TE01–TE13）
 * 对应清单：docs/api/B线-赛事主线-接口清单.md §5
 * 关键约束：组队/加人/移除/加指导教师都生成审批单不直接生效；队员确认后状态 UNCONFIRMED→CONFIRMED
 * ⚠️ 安全：TE02 详情仅本队成员/管理端/指导教师可见，其他人 4008（封堵按 ID 枚举越权）
 */
import { get, postIdem, putIdem, delIdem } from './http'

export type TeamMemberStatus = 'UNCONFIRMED' | 'CONFIRMED' | 'REJECTED' | 'EXITED' | 'REMOVED'
export type TeamViewerRole = 'LEADER' | 'MEMBER' | 'ADVISOR' | 'ADMIN'

export interface TeamListItem {
  id: string
  name: string
  projectName: string
  contestId: string
  contestName: string
  trackName: string
  leaderName: string
  memberCount: number
  advisorCount: number
  status: 'ACTIVE' | 'DISSOLVED'
  materialStatus: string
  materialCompleteRate: number
}

export interface TeamMemberItem {
  memberId: string
  name: string
  role: 'LEADER' | 'MEMBER'
  status: TeamMemberStatus
  /** 以下字段仅 队长/指导教师/管理端/本人 可见（后端裁剪） */
  college?: string
  className?: string
  phone?: string
  idCardLast4?: string
}

export interface TeamAdvisorItem {
  memberId: string
  name: string
  college: string
}

export interface TeamDetail {
  id: string
  name: string
  projectName: string
  contestId: string
  contestName: string
  trackId: string
  trackName: string
  leaderId: string
  leaderName: string
  status: 'ACTIVE' | 'DISSOLVED'
  materialStatus: string
  materialCompleteRate: number
  members: TeamMemberItem[]
  advisors: TeamAdvisorItem[]
  createdAt: string
  /** 当前登录人对本队的视角（后端判定，按钮显隐唯一依据） */
  viewerRole: TeamViewerRole
}

export interface TeamMemberSearchItem {
  memberId: string
  name: string
  studentNo: string
  college: string
  inTeam: boolean
}

// ── 列表与详情 ──
export function apiTeams(q?: { contestId?: string; keyword?: string }): Promise<TeamListItem[]> {
  return get('/api/teams', (q ?? {}) as Record<string, unknown>)
}
export function apiTeamDetail(id: string): Promise<TeamDetail> {
  return get('/api/teams/' + id)
}
/** 创建赛队（TE03 队长直建免审批，2026-10-24 lzh 拍板）：提交即建队，申请人即队长；返回新队 id
 *  TODO(联调)：后端 POST /api/teams 改直建后对齐返回结构（原为 { approvalId }） */
export function apiCreateTeam(body: { contestId: string; trackId: string; name: string; projectName: string; prefillMemberIds: string[] }): Promise<{ id: string }> {
  return postIdem('/api/teams', body, crypto.randomUUID())
}
/** 队长编辑队名/项目名（同比赛队名唯一 4002） */
export function apiUpdateTeam(id: string, body: { name: string; projectName: string }): Promise<null> {
  return putIdem('/api/teams/' + id, body, crypto.randomUUID())
}

// ── 成员生命周期 ──
export function apiSearchTeamMembers(teamId: string, keyword: string): Promise<TeamMemberSearchItem[]> {
  return get('/api/teams/' + teamId + '/members/search', { keyword })
}
/** 队长加人申请（TE04）：4003 人数上限 / 4004 重复参赛 / 4005 已在队 / 4007 已有待审批 */
export function apiAddTeamMember(teamId: string, memberId: string): Promise<null> {
  return postIdem('/api/teams/' + teamId + '/members', { memberId }, crypto.randomUUID())
}
/** 队员本人确认/拒绝（TE06）：action=CONFIRM|REJECT */
export function apiConfirmTeamMember(teamId: string, memberId: string, action: 'CONFIRM' | 'REJECT'): Promise<null> {
  return postIdem('/api/teams/' + teamId + '/members/' + memberId + '/confirm', { action }, crypto.randomUUID())
}
/** 移除成员（TE07，默认走审批；队长不可被移除 4010） */
export function apiRemoveTeamMember(teamId: string, memberId: string): Promise<null> {
  return delIdem('/api/teams/' + teamId + '/members/' + memberId, crypto.randomUUID())
}
/** 非队长主动退出（TE08） */
export function apiExitTeam(teamId: string): Promise<null> {
  return postIdem('/api/teams/' + teamId + '/exit', {}, crypto.randomUUID())
}
/** 解散（TE12）：有材料/成果/待审批拒绝（4009） */
export function apiDissolveTeam(teamId: string, reason: string): Promise<null> {
  return postIdem('/api/teams/' + teamId + '/dissolve', { reason }, crypto.randomUUID())
}
/** 加指导教师（TE09）：仅教师类型（4006） */
export function apiAddAdvisor(teamId: string, memberId: string): Promise<null> {
  return postIdem('/api/teams/' + teamId + '/advisors', { memberId }, crypto.randomUUID())
}
export function apiRemoveAdvisor(teamId: string, advisorId: string): Promise<null> {
  return delIdem('/api/teams/' + teamId + '/advisors/' + advisorId, crypto.randomUUID())
}
