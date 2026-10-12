/**
 * 赛事主线接口（B 线：模板 T01–T05 + 比赛 C01–C09 + 待处理池 P01–P03）
 * 对应清单：docs/api/B线-赛事主线-接口清单.md
 * 规则三级继承：模板 defaultRules → 比赛 overrideRules → 赛道 overrideRules（JSON 只存被覆盖的键）
 * ⚠️ 规则键名全组唯一（§0 六键），禁止私自新增
 */
import { del, get, post, put } from './http'

/** 规则值对象：键名见 §0（memberLimit/prefillLimit 为数值，其余布尔） */
export type RulesMap = Record<string, number | boolean>

export interface ContestTemplate {
  id: string
  name: string
  code: string
  category: string
  description: string
  defaultRules: RulesMap
  enabled: boolean
  /** 被引用计数（>0 时禁止删除 4012） */
  refCount: number
}

export type ContestStatus = 'DRAFT' | 'PUBLISHED' | 'REGISTERING' | 'RUNNING' | 'FINISHED' | 'ARCHIVED'

export interface ContestListItem {
  id: string
  name: string
  semesterId: string
  semesterName: string
  status: ContestStatus
  signupStart: string
  signupEnd: string
  contestStart: string
  contestEnd: string
  trackCount: number
  teamCount: number
}

/** 生效规则项（C02）：三级继承合并结果 + 来源标注 */
export interface EffectiveRule {
  key: string
  value: number | boolean
  source: 'TEMPLATE' | 'CONTEST' | 'TRACK'
}

export interface ContestTrack {
  id: string
  name: string
  code: string
  description: string
  overrideRules: RulesMap
  /** 该赛道的生效规则（含赛道覆盖） */
  rules: EffectiveRule[]
  teamCount: number
}

export interface ContestDetail {
  id: string
  name: string
  semesterId: string
  semesterName: string
  templateId: string
  templateName: string
  status: ContestStatus
  signupStart: string
  signupEnd: string
  contestStart: string
  contestEnd: string
  contact: string
  description: string
  overrideRules: RulesMap
  /** 比赛级生效规则（模板+比赛合并） */
  rules: EffectiveRule[]
  tracks: ContestTrack[]
}

export interface ContestForm {
  templateId: string
  semesterId: string
  name: string
  signupStart: string
  signupEnd: string
  contestStart: string
  contestEnd: string
  contact: string
  description: string
  overrideRules: RulesMap
}

export interface PoolItem {
  id: string
  title: string
  source: string
  fetchedAt: string
  content: string
  /** 解析线索：时间/人数上限等 */
  clues: { signupTime?: string; memberLimit?: number; organizer?: string }
  status: 'PENDING' | 'CONFIRMED' | 'IGNORED'
  confirmedContestId?: string
}

// ── 模板 ──
export function apiTemplates(category?: string): Promise<ContestTemplate[]> {
  return get('/api/contest-templates', category ? { category } : undefined)
}
export function apiCreateTemplate(body: { name: string; code: string; category: string; description: string; defaultRules: RulesMap }): Promise<ContestTemplate> {
  return post('/api/contest-templates', body)
}
export function apiUpdateTemplate(id: string, body: { name: string; code: string; category: string; description: string; defaultRules: RulesMap }): Promise<ContestTemplate> {
  return put('/api/contest-templates/' + id, body)
}
/** 被比赛引用拒绝（4012） */
export function apiDeleteTemplate(id: string): Promise<null> {
  return del('/api/contest-templates/' + id)
}

// ── 比赛 ──
export function apiContests(q: { semesterId?: string; status?: string }): Promise<ContestListItem[]> {
  return get('/api/contests', q as Record<string, unknown>)
}
export function apiContestDetail(id: string): Promise<ContestDetail> {
  return get('/api/contests/' + id)
}
export function apiCreateContest(body: ContestForm): Promise<ContestDetail> {
  return post('/api/contests', body)
}
export function apiUpdateContest(id: string, body: ContestForm): Promise<ContestDetail> {
  return put('/api/contests/' + id, body)
}
/** 仅 DRAFT 可删（4001） */
export function apiDeleteContest(id: string): Promise<null> {
  return del('/api/contests/' + id)
}
/** 状态流转：按序不可跳（4011）；DRAFT→PUBLISHED 有发布前检查（4001 逐项指出缺项） */
export function apiContestStatus(id: string, to: ContestStatus): Promise<null> {
  return post('/api/contests/' + id + '/status', { to })
}
export function apiCreateTrack(id: string, body: { name: string; code: string; description: string; overrideRules: RulesMap }): Promise<ContestTrack> {
  return post('/api/contests/' + id + '/tracks', body)
}
export function apiUpdateTrack(id: string, trackId: string, body: { name: string; code: string; description: string; overrideRules: RulesMap }): Promise<ContestTrack> {
  return put('/api/contests/' + id + '/tracks/' + trackId, body)
}
/** 有赛队挂载该赛道时拒绝 */
export function apiDeleteTrack(id: string, trackId: string): Promise<null> {
  return del('/api/contests/' + id + '/tracks/' + trackId)
}

// ── 待处理比赛池 ──
export function apiContestPool(status?: string): Promise<PoolItem[]> {
  return get('/api/contests/pool', status ? { status } : undefined)
}
/** 确认为正式比赛：按解析内容预填（可改）创建 contest */
export function apiConfirmPoolItem(id: string, body: ContestForm): Promise<ContestDetail> {
  return post('/api/contests/pool/' + id + '/confirm', body)
}
export function apiIgnorePoolItem(id: string): Promise<null> {
  return post('/api/contests/pool/' + id + '/ignore')
}
