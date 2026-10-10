/**
 * 请求/响应契约类型（对应《全局接口与开发规范》§2.2 统一响应体、§2.3 分页）
 * ⚠️ 安全：ID 一律字符串传输（雪花 19 位超 JS 安全整数上限，规范 §2.1），禁止转 number
 */

/** 统一响应体 */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

/** 分页请求参数（默认 size=20，上限 100） */
export interface PageQuery {
  page: number
  size: number
  keyword?: string
}

/** 分页响应 */
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}
