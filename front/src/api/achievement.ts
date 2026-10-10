/**
 * 成果接口（C 线 A01–A08，暂定清单）
 * 对应方案：《前端ABC模块页面开发-优化方案》PC3；PRD §3.8
 * 关键约束：队长提交 → 待审批 + ACHIEVEMENT_SUBMIT 审批单；管理员录入直接生效；
 *    审批通过触发 §5 联动（材料任务自动补"佐证材料"必交项并重算齐全率）
 * 错误码（暂定）：5007 仅队长可提交/传佐证 / 5008 队伍有未通过成果时禁导出获奖清单
 */
import { del, get, post, put } from './http'

export type AchievementType = 'AWARD' | 'RATING' | 'QUALIFICATION' | 'ACTIVITY' | 'OTHER'
export type AchievementStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface AchievementItem {
  id: string
  teamId: string
  teamName: string
  type: AchievementType
  /** 成果名称（作品/项目名） */
  name: string
  /** 级别（省级一等奖 / 校级优秀 等） */
  level: string
  achievedAt: string
  status: AchievementStatus
  submittedBy: string
  hasEvidence: boolean
}

export interface AchievementDetail extends AchievementItem {
  description: string
  evidenceFileId: string
  evidenceFileName: string
  createdAt: string
  /** 当前登录人是否该队队长（佐证上传按钮显隐依据，后端判定） */
  viewerIsLeader: boolean
}

/** 学生端"我的成果"：本人所在队伍 + 队伍成果清单 */
export interface MyAchievements {
  teams: Array<{ teamId: string; teamName: string; isLeader: boolean }>
  items: AchievementItem[]
}

export function apiAchievements(q?: { teamId?: string; status?: string; keyword?: string }): Promise<AchievementItem[]> {
  return get('/api/achievements', (q ?? {}) as Record<string, unknown>)
}
export function apiAchievementDetail(id: string): Promise<AchievementDetail> {
  return get('/api/achievements/' + id)
}
/** 提交成果：学生队长 → PENDING + 审批单；管理员 → 直接 APPROVED（均带 §5 联动） */
export function apiCreateAchievement(body: {
  teamId: string
  type: AchievementType
  name: string
  level: string
  achievedAt: string
  description: string
  evidenceFileId?: string
  evidenceFileName?: string
}): Promise<{ id: string; status: AchievementStatus }> {
  return post('/api/achievements', body)
}
/** 管理员审批（A04）：APPROVE 触发 §5 联动 */
export function apiReviewAchievement(id: string, action: 'APPROVE' | 'REJECT', comment: string): Promise<null> {
  return post('/api/achievements/' + id + '/review', { action, comment })
}
/** 佐证材料覆盖上传（A05）：仅队长（5007） */
export function apiUploadEvidence(id: string, fileId: string, fileName: string): Promise<null> {
  return put('/api/achievements/' + id + '/evidence', { fileId, fileName })
}
/** 删除未通过的成果（提交人可撤回） */
export function apiDeleteAchievement(id: string): Promise<null> {
  return del('/api/achievements/' + id)
}
/** 获奖导出（A07）：队伍存在未通过成果时 5008 */
export function apiExportAwards(teamId: string): Promise<{ note: string }> {
  return post('/api/achievements/export', { teamId })
}
/** 学生端我的成果（A08） */
export function apiMyAchievements(): Promise<MyAchievements> {
  return get('/api/my/achievements')
}
