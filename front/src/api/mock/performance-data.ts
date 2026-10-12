/**
 * 绩效统计 mock 数据层（接口层在 ./performance.ts，分层惯例同 team-data.ts）
 * 对应契约：《绩效统计-接口清单》（2026-10-12，错误码 8040 起）
 * 核心机制（清单 §3/§4）：
 *   1. 打分落快照：weight/maxScore 随打分时点固化进 scores 行，规则变更不影响历史分
 *   2. 打分/驳回后 recomputeAll 重算学期汇总、子组织内排名与近 4 周趋势
 *   3. 内置标签阈值（斜率>0 升 / <0 降 / 子组织前 20% 功劳 / 连续 4 周贡献 / 单周≥满分 90% 高效）
 * 演示数据：数媒协会与数媒二楼各一套规则；王雨桐 2 周已打分 + 1 周驳回；孙嘉仪/赵天宇各 1 周待审核
 * TODO(联调)：后端绩效接口就绪后删除本文件
 */
import { members } from './member-data'

export interface MockPerfRule {
  id: string
  subOrgId: string
  /** null = 子组织默认规则（学期特定规则优先，无则回退默认） */
  semesterId: string | null
  dimensionKey: string
  dimensionName: string
  weight: number
  maxScore: number
  enabled: boolean
  sortNo: number
}

export interface MockScoreLine {
  dimensionKey: string
  dimensionName: string
  score: number
  maxScore: number
  weight: number
  commentText: string
}

export interface MockWeekly {
  id: string
  memberId: string
  memberName: string
  subOrgId: string
  subOrgName: string
  semesterId: string
  weekNo: number
  workContent: string
  attachFileId: string
  attachFileName: string
  status: 'SUBMITTED' | 'SCORED' | 'REJECTED'
  submittedAt: string
  rejectReason: string
  scoreComment: string
  scores: MockScoreLine[]
  totalScore: number | null
}

export interface MockPerfSummary {
  memberId: string
  semesterId: string
  subOrgId: string
  submitCount: number
  scoredCount: number
  totalScore: number
  avgScore: number
  bestWeekNo: number
  bestWeekScore: number
  rankInOrg: number
  trendSlope: number
  tags: string[]
}

/** 打分规则种子：o11 三维 / o111 二维（默认规则，semesterId=null） */
export const perfRules: MockPerfRule[] = [
  { id: 'pr1', subOrgId: 'o11', semesterId: null, dimensionKey: 'TECH_OUTPUT', dimensionName: '技术产出', weight: 0.4, maxScore: 100, enabled: true, sortNo: 1 },
  { id: 'pr2', subOrgId: 'o11', semesterId: null, dimensionKey: 'COLLABORATION', dimensionName: '协作贡献', weight: 0.3, maxScore: 100, enabled: true, sortNo: 2 },
  { id: 'pr3', subOrgId: 'o11', semesterId: null, dimensionKey: 'ATTENDANCE', dimensionName: '出勤', weight: 0.3, maxScore: 100, enabled: true, sortNo: 3 },
  { id: 'pr4', subOrgId: 'o111', semesterId: null, dimensionKey: 'TECH_OUTPUT', dimensionName: '技术产出', weight: 0.5, maxScore: 100, enabled: true, sortNo: 1 },
  { id: 'pr5', subOrgId: 'o111', semesterId: null, dimensionKey: 'ATTENDANCE', dimensionName: '出勤', weight: 0.5, maxScore: 100, enabled: true, sortNo: 2 }
]

/** 取某子组织生效规则（学期特定优先，回退默认；按 sortNo 排序） */
export function rulesOf(subOrgId: string, semesterId: string): MockPerfRule[] {
  const specific: MockPerfRule[] = []
  const fallback: MockPerfRule[] = []
  for (const r of perfRules) {
    if (r.subOrgId !== subOrgId) continue
    if (r.semesterId === semesterId) specific.push(r)
    if (r.semesterId === null) fallback.push(r)
  }
  const picked = specific.length > 0 ? specific : fallback
  return picked.sort((a, b) => a.sortNo - b.sortNo)
}

export const weeklies: MockWeekly[] = [
  {
    id: 'pw1', memberId: 'm7', memberName: '王雨桐', subOrgId: 'o11', subOrgName: '数媒协会', semesterId: 's2', weekNo: 4,
    workContent: '完成校内选拔赛报名材料整理；跟进电波突击队循迹小车传感器调试。',
    attachFileId: '', attachFileName: '',
    status: 'SCORED', submittedAt: '2026-09-25 20:10:00', rejectReason: '', scoreComment: '产出扎实，继续保持',
    scores: [
      { dimensionKey: 'TECH_OUTPUT', dimensionName: '技术产出', score: 85, maxScore: 100, weight: 0.4, commentText: '' },
      { dimensionKey: 'COLLABORATION', dimensionName: '协作贡献', score: 80, maxScore: 100, weight: 0.3, commentText: '' },
      { dimensionKey: 'ATTENDANCE', dimensionName: '出勤', score: 82, maxScore: 100, weight: 0.3, commentText: '' }
    ],
    totalScore: 82.6
  },
  {
    id: 'pw2', memberId: 'm7', memberName: '王雨桐', subOrgId: 'o11', subOrgName: '数媒协会', semesterId: 's2', weekNo: 5,
    workContent: '完成小车 PID 参数整定；给新人讲了一次 keil 调试入门。',
    attachFileId: '', attachFileName: '',
    status: 'SCORED', submittedAt: '2026-10-02 21:00:00', rejectReason: '', scoreComment: '带新人加分',
    scores: [
      { dimensionKey: 'TECH_OUTPUT', dimensionName: '技术产出', score: 90, maxScore: 100, weight: 0.4, commentText: '' },
      { dimensionKey: 'COLLABORATION', dimensionName: '协作贡献', score: 85, maxScore: 100, weight: 0.3, commentText: '' },
      { dimensionKey: 'ATTENDANCE', dimensionName: '出勤', score: 89, maxScore: 100, weight: 0.3, commentText: '' }
    ],
    totalScore: 88.2
  },
  {
    id: 'pw3', memberId: 'm7', memberName: '王雨桐', subOrgId: 'o11', subOrgName: '数媒协会', semesterId: 's2', weekNo: 6,
    workContent: '这周比较忙，打了比赛。',
    attachFileId: '', attachFileName: '',
    status: 'REJECTED', submittedAt: '2026-10-09 19:30:00', rejectReason: '内容过于简略，请补充具体工作项与产出', scoreComment: '',
    scores: [], totalScore: null
  },
  {
    id: 'pw4', memberId: 'm5', memberName: '李明轩', subOrgId: 'o11', subOrgName: '数媒协会', semesterId: 's2', weekNo: 4,
    workContent: '组织电波突击队例会；确定循迹小车总体方案。',
    attachFileId: '', attachFileName: '',
    status: 'SCORED', submittedAt: '2026-09-25 18:00:00', rejectReason: '', scoreComment: '',
    scores: [
      { dimensionKey: 'TECH_OUTPUT', dimensionName: '技术产出', score: 76, maxScore: 100, weight: 0.4, commentText: '' },
      { dimensionKey: 'COLLABORATION', dimensionName: '协作贡献', score: 74, maxScore: 100, weight: 0.3, commentText: '' },
      { dimensionKey: 'ATTENDANCE', dimensionName: '出勤', score: 75, maxScore: 100, weight: 0.3, commentText: '' }
    ],
    totalScore: 75.1
  },
  {
    id: 'pw5', memberId: 'm5', memberName: '李明轩', subOrgId: 'o11', subOrgName: '数媒协会', semesterId: 's2', weekNo: 5,
    workContent: '带队完成赛道试跑；整理试跑数据 12 组。',
    attachFileId: '', attachFileName: '',
    status: 'SCORED', submittedAt: '2026-10-02 19:40:00', rejectReason: '', scoreComment: '',
    scores: [
      { dimensionKey: 'TECH_OUTPUT', dimensionName: '技术产出', score: 80, maxScore: 100, weight: 0.4, commentText: '' },
      { dimensionKey: 'COLLABORATION', dimensionName: '协作贡献', score: 78, maxScore: 100, weight: 0.3, commentText: '' },
      { dimensionKey: 'ATTENDANCE', dimensionName: '出勤', score: 79, maxScore: 100, weight: 0.3, commentText: '' }
    ],
    totalScore: 79.1
  },
  {
    id: 'pw6', memberId: 'm6', memberName: '孙嘉仪', subOrgId: 'o111', subOrgName: '数媒二楼', semesterId: 's2', weekNo: 5,
    workContent: '完成像素工坊小程序首页与商品详情页开发；修复登录态刷新丢失问题。',
    attachFileId: '', attachFileName: '',
    status: 'SUBMITTED', submittedAt: '2026-10-03 16:20:00', rejectReason: '', scoreComment: '',
    scores: [], totalScore: null
  },
  {
    id: 'pw7', memberId: 'm3', memberName: '赵天宇', subOrgId: 'o12', subOrgName: '电控工作室', semesterId: 's2', weekNo: 5,
    workContent: '完成电控培训第三期备课；带 4 名新生完成 HAL 库点灯。',
    attachFileId: '', attachFileName: '',
    status: 'SUBMITTED', submittedAt: '2026-10-03 20:05:00', rejectReason: '', scoreComment: '',
    scores: [], totalScore: null
  }
]

export const perfSummaries: MockPerfSummary[] = []

/** 子组织成员总数（排名口径的分母） */
function orgMemberCount(subOrgId: string): number {
  let n = 0
  for (const m of members) {
    if (m.orgId === subOrgId) n++
  }
  return n
}

/**
 * 全量重算汇总（mock 演示简化：打分/驳回后整表重算；真实后端在事务内只算受影响成员）
 * 排名 = 同子组织同学期 totalScore 降序；标签按清单 §4 阈值
 */
export function recomputeAll(): void {
  const map: Record<string, MockPerfSummary> = {}
  for (const w of weeklies) {
    const key = w.memberId + '|' + w.semesterId
    if (!map[key]) {
      map[key] = {
        memberId: w.memberId, semesterId: w.semesterId, subOrgId: w.subOrgId,
        submitCount: 0, scoredCount: 0, totalScore: 0, avgScore: 0,
        bestWeekNo: 0, bestWeekScore: 0, rankInOrg: 0, trendSlope: 0, tags: []
      }
    }
    const s = map[key]
    s.submitCount++
    if (w.status === 'SCORED' && w.totalScore !== null) {
      s.scoredCount++
      s.totalScore = Math.round((s.totalScore + w.totalScore) * 100) / 100
      if (w.totalScore > s.bestWeekScore) {
        s.bestWeekScore = w.totalScore
        s.bestWeekNo = w.weekNo
      }
    }
  }
  const list: MockPerfSummary[] = []
  for (const key in map) {
    const s = map[key]
    s.avgScore = s.scoredCount > 0 ? Math.round((s.totalScore / s.scoredCount) * 100) / 100 : 0
    // 近 4 周趋势斜率（朴素：近 4 个已打分周 首末差/间隔数；不足两周为 0）
    const scored: MockWeekly[] = []
    for (const w of weeklies) {
      if (w.memberId === s.memberId && w.semesterId === s.semesterId && w.status === 'SCORED') scored.push(w)
    }
    scored.sort((a, b) => a.weekNo - b.weekNo)
    const recent = scored.slice(-4)
    s.trendSlope = recent.length >= 2
      ? Math.round(((recent[recent.length - 1].totalScore ?? 0) - (recent[0].totalScore ?? 0)) / (recent.length - 1) * 100) / 100
      : 0
    list.push(s)
  }
  // 子组织内排名 + 标签
  for (const s of list) {
    let rank = 1
    for (const o of list) {
      if (o.subOrgId === s.subOrgId && o.semesterId === s.semesterId && o.totalScore > s.totalScore) rank++
    }
    s.rankInOrg = rank
    const tags: string[] = []
    if (s.trendSlope > 0) tags.push('ACTIVITY_UP')
    if (s.trendSlope < 0) tags.push('ACTIVITY_DOWN')
    if (s.scoredCount >= 4) tags.push('STREAK')
    // 高效周：最近一个已打分周加权总分达满分 90%
    let lastScored: MockWeekly | null = null
    for (const w of weeklies) {
      if (w.memberId === s.memberId && w.semesterId === s.semesterId && w.status === 'SCORED') {
        if (!lastScored || w.weekNo > lastScored.weekNo) lastScored = w
      }
    }
    if (lastScored && isEfficientWeek(lastScored)) tags.push('EFFICIENT_WEEK')
    const total = orgMemberCount(s.subOrgId)
    if (rank <= Math.max(1, Math.ceil(total * 0.2))) tags.push('TOP_CONTRIBUTOR')
    s.tags = tags
  }
  perfSummaries.length = 0
  for (const s of list) perfSummaries.push(s)
}

/** 单周标签：高效周（加权总分 ≥ 满分加权和的 90%），打分时挂到 summary.tags 之外此处只算返回 */
export function isEfficientWeek(w: MockWeekly): boolean {
  if (w.totalScore === null) return false
  let full = 0
  for (const sc of w.scores) full += sc.maxScore * sc.weight
  return full > 0 && w.totalScore >= full * 0.9
}

// 模块加载时先算一遍种子数据的汇总
recomputeAll()
