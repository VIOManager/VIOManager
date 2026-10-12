/**
 * 绩效统计接口（PF01–PF12）
 * 契约源：《绩效统计-接口清单》（2026-10-12，错误码 8040 起；录入 Apifox 后以 Apifox 为准）
 * 机制要点：
 *   学生每周交周报 → 管理员按子组织打分维度逐项打分（带权重/满分快照）→ 加权总分 + 学期汇总排名 + 标签
 *   绩效审核不走审批中心（PRD §3.13 管理员直审）；驳回不删记录，改后原记录重进待审核
 *   同人同周同子组织仅一份周报（8041）；打分维度须与该子组织启用规则完全一致（8043）
 */
import { get, post, postIdem, put } from './http'

export type WeeklyStatus = 'SUBMITTED' | 'SCORED' | 'REJECTED'

/** 周报列表项（PF01/PF06 共用；管理端多 memberName/subOrgName；subOrgId 冗余供前端拉该子组织打分规则） */
export interface WeeklyItem {
  id: string
  memberId: string
  memberName: string
  subOrgId: string
  subOrgName: string
  weekNo: number
  status: WeeklyStatus
  totalScore: number | null
  submittedAt: string
}

/** 维度得分明细（PF04 详情 / PF07 打分入参共用形状） */
export interface ScoreLine {
  dimensionKey: string
  dimensionName: string
  score: number
  /** 快照：打分当时的满分与权重（规则后续变更不影响历史分） */
  maxScore: number
  weight: number
  commentText: string
}

/** PF04 周报详情 */
export interface WeeklyDetail extends WeeklyItem {
  workContent: string
  attachFileId: string
  attachFileName: string
  scoreComment: string
  rejectReason: string
  scores: ScoreLine[]
}

/** PF05 我的绩效汇总 */
export interface MyPerfSummary {
  semesterId: string
  semesterName: string
  totalScore: number
  avgScore: number
  scoredCount: number
  submitCount: number
  bestWeekNo: number
  bestWeekScore: number
  rankInOrg: number
  orgMemberCount: number
  /** 近 4 周趋势斜率（正=上升） */
  trendSlope: number
  tags: string[]
}

/** PF09 汇总行（评优参考，按 totalScore 降序） */
export interface SummaryRow {
  memberId: string
  memberName: string
  subOrgName: string
  submitCount: number
  scoredCount: number
  totalScore: number
  avgScore: number
  bestWeekNo: number
  rankInOrg: number
  tags: string[]
}

/** PF11 打分规则维度 */
export interface PerfRule {
  dimensionKey: string
  dimensionName: string
  weight: number
  maxScore: number
  enabled: boolean
  sortNo: number
}

// ── 学生侧 ──

export function apiMyWeeklies(q?: { semesterId?: string; status?: string }): Promise<WeeklyItem[]> {
  return get('/api/performance/my/weeklies', (q ?? {}) as Record<string, unknown>)
}

/** PF02 提交周报（subOrgId 服务端取本人所属组织；幂等头） */
export function apiSubmitWeekly(body: { weekNo: number; workContent: string; attachFileId?: string }): Promise<{ id: string }> {
  return postIdem('/api/performance/weeklies', body, crypto.randomUUID())
}

/** PF03 驳回后重新提交（仅本人且 REJECTED） */
export function apiResubmitWeekly(id: string, body: { workContent: string; attachFileId?: string }): Promise<null> {
  return put('/api/performance/weeklies/' + id, body)
}

export function apiWeeklyDetail(id: string): Promise<WeeklyDetail> {
  return get('/api/performance/weeklies/' + id)
}

export function apiMyPerfSummary(): Promise<MyPerfSummary> {
  return get('/api/performance/my/summary')
}

// ── 管理侧（部门管理员本子树、超管全部，数据范围后端裁剪）──

export function apiWeeklies(q?: { semesterId?: string; weekNo?: number; subOrgId?: string; status?: string; memberKeyword?: string }): Promise<WeeklyItem[]> {
  return get('/api/performance/weeklies', (q ?? {}) as Record<string, unknown>)
}

/** PF07 打分：scores 须覆盖该子组织全部启用维度（8043），各维度不超满分（8044）；带幂等头 */
export function apiScoreWeekly(id: string, body: { scores: Array<{ dimensionKey: string; score: number; commentText: string }>; scoreComment: string }): Promise<null> {
  return postIdem('/api/performance/weeklies/' + id + '/score', body, crypto.randomUUID())
}

/** PF08 驳回（reason 必填） */
export function apiRejectWeekly(id: string, reason: string): Promise<null> {
  return post('/api/performance/weeklies/' + id + '/reject', { reason })
}

export function apiPerfSummaries(q?: { semesterId?: string; subOrgId?: string; memberKeyword?: string }): Promise<SummaryRow[]> {
  return get('/api/performance/summaries', (q ?? {}) as Record<string, unknown>)
}

/** PF10 导出（mock 返回提示文本） */
export function apiPerfExport(q: { semesterId: string; subOrgId?: string; groupBy: 'MEMBER' | 'ORG' }): Promise<{ note: string }> {
  return get('/api/performance/export', q as unknown as Record<string, unknown>)
}

// ── 规则配置 ──

export function apiPerfRules(q: { subOrgId: string; semesterId?: string }): Promise<PerfRule[]> {
  return get('/api/performance/rules', q as Record<string, unknown>)
}

/** PF12 批量保存（upsert；semesterId 空=子组织默认规则） */
export function apiSavePerfRules(body: { subOrgId: string; semesterId?: string; items: PerfRule[] }): Promise<null> {
  return put('/api/performance/rules', body)
}
