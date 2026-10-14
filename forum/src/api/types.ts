/**
 * 论坛站接口基础类型（对齐《全局接口与开发规范》§2 统一响应体与 §2.3 分页）
 * 与主站 front/src/api/types.ts 同构——论坛是独立 Vite 工程，类型各持一份
 */

/** 统一响应体外壳（拦截器剥壳后页面拿到的已是 data） */
export interface Result {
  code: number
  message: string
  data: unknown
}

/** 统一分页外壳（规范 §2.3：list + total + page + size） */
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}
