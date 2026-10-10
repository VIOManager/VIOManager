/**
 * 审批中心接口（暂定：骨架清单未出，字段以 mock 为准，录入 Apifox 后改造）
 * 当前用途：顶栏"待办"红点（P08 未读数语义的占位实现）
 */
import { get, post } from './http'

export interface ApprovalItem {
  id: string
  bizType: string
  bizId: string
  title: string
  detail: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
  applicantName: string
  createdAt: string
  unread: boolean
  targetMemberId?: string
}

export function apiApprovals(): Promise<ApprovalItem[]> {
  return get('/api/approvals')
}

export function apiPendingCount(): Promise<{ count: number }> {
  return get('/api/approvals/pending-count')
}

export function apiMarkRead(id: string): Promise<null> {
  return post('/api/approvals/' + id + '/read')
}
