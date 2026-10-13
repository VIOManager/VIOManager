/**
 * 审计查询接口（AQ01–AQ03）
 * 契约源：docs/api/审计查询-接口清单.md（2026-10-12 入库 main；录入 Apifox 后以 Apifox 为准）
 * 纯只读模块（无专属错误码）：写入侧由后端 @AuditLog AOP 全组共用
 * 权限：仅超管与部门管理员（学生 1003）；部门管理员按操作人所属组织子树过滤
 * bizType 枚举走 AQ02 动态返回（后端加类型前端零改动），不进 labels.ts
 */
import { get } from './http'
import type { PageResult } from './types'

export interface AuditLogItem {
  id: string
  realName: string
  loginName: string
  role: string
  /** 业务类型中文名（后端随 bizType 带上，前端不翻译） */
  bizType: string
  bizTypeLabel: string
  summary: string
  ip: string
  success: number
  errorMessage: string
  costMillis: number
  createdAt: string
}

export interface AuditLogDetail extends AuditLogItem {
  httpMethod: string
  uri: string
  userAgent: string
  requestParams: string
}

export interface AuditBizType {
  bizType: string
  label: string
}

export function apiAuditLogs(q: {
  keyword?: string
  bizType?: string
  success?: number
  startTime?: string
  endTime?: string
  page: number
  size: number
}): Promise<PageResult<AuditLogItem>> {
  return get('/api/audit-logs', q as Record<string, unknown>)
}

/** AQ02 业务类型枚举（筛选下拉用，动态） */
export function apiAuditBizTypes(): Promise<AuditBizType[]> {
  return get('/api/audit-logs/biz-types')
}

export function apiAuditLogDetail(id: string): Promise<AuditLogDetail> {
  return get('/api/audit-logs/' + id)
}
