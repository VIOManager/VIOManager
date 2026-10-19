/**
 * 成果接口（C 线 A01–A08，暂定清单）
 * 对应方案：《前端ABC模块页面开发-优化方案》PC3；PRD §3.8
 * 关键约束：队长提交 → 待审批 + ACHIEVEMENT_SUBMIT 审批单；管理员录入直接生效；
 *    审批通过触发 §5 联动（材料任务自动补"佐证材料"必交项并重算齐全率）
 * 错误码（暂定）：5007 仅队长可提交/传佐证 / 5008 队伍有未通过成果时禁导出获奖清单
 */
import { get, post, postIdem, delIdem, download } from './http'

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
  return postIdem('/api/achievements', body, crypto.randomUUID())
}
/** 管理员审批（A04）：⚠️ 后端无此路由，当前已无调用方（2026-10-19 起详情抽屉改走「审批中心」）。
 *  接口清单 §5 口径：成果审批由 ACHIEVEMENT_SUBMIT 审批单承载，在审批中心批准/驳回，
 *  回调里才置 APPROVED 并触发材料 §5 联动。保留本函数仅作契约记录，勿再接到 UI 上。 */
export function apiReviewAchievement(id: string, action: 'APPROVE' | 'REJECT', comment: string): Promise<null> {
  return post('/api/achievements/' + id + '/review', { action, comment })
}
/** 佐证材料覆盖上传（A05，后端为 POST）：仅队长（5007） */
export function apiUploadEvidence(id: string, fileId: string, fileName: string): Promise<null> {
  return postIdem('/api/achievements/' + id + '/evidence', { fileId, fileName }, crypto.randomUUID())
}
/** 删除未通过的成果（提交人可撤回） */
export function apiDeleteAchievement(id: string): Promise<null> {
  return delIdem('/api/achievements/' + id, crypto.randomUUID())
}
/** 获奖导出（A07，后端为 GET 文件流）：队伍存在未通过成果时 5008 */
export function apiExportAwards(teamId: string): Promise<void> {
  return download('/api/achievements/export' + (teamId ? '?teamId=' + encodeURIComponent(teamId) : ''), '获奖导出.xlsx')
}
/** 学生端我的成果（A08，后端路径 /api/achievements/my/contributions） */
export function apiMyAchievements(): Promise<MyAchievements> {
  return get('/api/achievements/my/contributions')
}
