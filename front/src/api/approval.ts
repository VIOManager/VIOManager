/**
 * 审批中心接口（P01–P09）
 * 契约源：《审批中心-接口清单》（2026-10-10 正式版，错误码段 6xxx；录入 Apifox 后以 Apifox 为准）
 * 机制要点：
 *   三视角 view：TODO 管理员待办 / MINE 我发起的 / TARGET 我是目标人
 *   状态机：PENDING 一跳终结（批准或目标人同意任一先操作即终结），四终态
 *   P02–P06 写操作必须带 X-Idempotency-Key 幂等头（postIdem，键随每次点击生成）
 *   红点：P08 只数"发给我的"与"我发起的"，管理员待办不进红点（清单 §4 P08）
 * TODO(联调)：真实后端就绪后本文件字段不变，仅需按 Apifox 复核一遍
 */
import { get, postIdem, put } from './http'
import type { PageResult } from './types'

/** 三视角（P01 必填参数） */
export type ApprovalView = 'TODO' | 'MINE' | 'TARGET'

/**
 * 队长自治审批类型（2026-10-24 lzh 拍板）：队伍类与项目类单子归队长在队伍侧管理，
 * 管理员审批中心（TODO/已通过/已驳回）不展示、类型下拉不提供——管理员只管成果提交与学期切换。
 * ⚠️ 后端仍按原口径产出这些单子：MINE/TARGET 视角与队伍详情页照常处理不受影响；
 *    组队申请（TEAM_CREATE）已拍板队长直建免审批——后端改路由后该类型整体退役（交接说明已排给后端会话）
 */
export const APPROVAL_CAPTAIN_TYPES: string[] = [
  'TEAM_CREATE',
  'TEAM_ADD_MEMBER',
  'TEAM_REMOVE_MEMBER',
  'TEAM_ADD_ADVISOR',
  'TEAM_DISSOLVE',
  'PROJECT_VIEW',
  'MILESTONE_APPROVE'
]

/** 审批单状态（PENDING 为唯一活动态） */
export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'EXPIRED'

/** P07 已读标记的身份位 */
export type ApprovalRole = 'APPLICANT' | 'TARGET' | 'APPROVER'

/** P01 列表项（ApprovalVO） */
export interface ApprovalItem {
  id: string
  bizType: string
  bizTitle: string
  status: ApprovalStatus
  applicantName: string
  targetMemberName: string
  applyReason: string
  handleComment: string
  handledAt: string
  createdAt: string
}

/** P09 流水行（approval_record；operatorRole 取值见 labels 的 APPROVAL_ROLE_LABEL） */
export interface ApprovalRecord {
  action: string
  operatorName: string
  operatorRole: string
  commentText: string
  createdAt: string
}

/** P09 详情 = 列表字段 + 申请快照 + 操作流水（时间正序） */
export interface ApprovalDetail extends ApprovalItem {
  /** 各业务线快照 JSON 字符串（结构由各线清单登记，页面只格式化展示不解析语义） */
  bizPayload: string
  records: ApprovalRecord[]
}

/** P08 未读红点数 */
export interface UnreadCount {
  /** 发给我的邀请/通知：PENDING 且未读 */
  todoCount: number
  /** 我发起的已出结果且未读 */
  resultCount: number
}

/** P01 分页列表；学生传 TODO 由后端回 1003 */
export function apiApprovalList(query: {
  view: ApprovalView
  status?: string
  bizType?: string
  /** 标题模糊搜索（清单 §7 遗留项，2026-10-12 lzh 拍板本期补；TODO(联调)：待后端清单正式化） */
  keyword?: string
  page: number
  size: number
}): Promise<PageResult<ApprovalItem>> {
  return get('/api/approvals', query as Record<string, unknown>)
}

/** P09 详情（可见性：超管/发起人/目标人/审批组织子树内管理员，其余 6002） */
export function apiApprovalDetail(id: string): Promise<ApprovalDetail> {
  return get('/api/approvals/' + id)
}

/** P08 两类红点（未绑定人员档案的纯管理账号回两个 0） */
export function apiUnreadCount(): Promise<UnreadCount> {
  return get('/api/approvals/unread-count')
}

/** P07 已读标记：只置 asRole 对应身份位的已读 */
export function apiMarkRead(ids: string[], asRole: ApprovalRole): Promise<null> {
  return put('/api/approvals/read', { ids, asRole })
}

// ── P02–P06 五个写操作：均带幂等头与审计；body 只有 comment ──
// 幂等键随每次点击生成：同一确认框内连点由页面 submitting 置灰挡住，服务端重复键回 1005

/** P02 管理员批准（部门管理员须审批责任组织在其子树内，否则 6002） */
export function apiApprove(id: string, comment: string): Promise<null> {
  return postIdem('/api/approvals/' + id + '/approve', { comment }, crypto.randomUUID())
}

/** P03 管理员驳回（触发 onRejected 回调，各线通常无动作） */
export function apiReject(id: string, comment: string): Promise<null> {
  return postIdem('/api/approvals/' + id + '/reject', { comment }, crypto.randomUUID())
}

/** P04 发起人取消（仅申请人本人；取消不触发业务回调） */
export function apiCancel(id: string, comment: string): Promise<null> {
  return postIdem('/api/approvals/' + id + '/cancel', { comment }, crypto.randomUUID())
}

/** P05 目标人同意（转 APPROVED 并触发 onApproved） */
export function apiAgree(id: string, comment: string): Promise<null> {
  return postIdem('/api/approvals/' + id + '/agree', { comment }, crypto.randomUUID())
}

/** P06 目标人拒绝（转 REJECTED） */
export function apiRefuse(id: string, comment: string): Promise<null> {
  return postIdem('/api/approvals/' + id + '/refuse', { comment }, crypto.randomUUID())
}
