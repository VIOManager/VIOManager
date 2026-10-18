/**
 * 论坛 mock 注册表：把 axios 请求拦到本地内存数据上
 * 对应：主站 front/src/api/mock/registry.ts 的论坛简化版（论坛无数据范围/审批回调）
 * 分层：api/*.ts 页面契约函数 → http.ts（唯一出口）→ 本适配器 → mock/*.ts 处理器
 * ⚠️ 注册顺序敏感：固定路径（/hot、/stats）必须先于占位路径（/:id）注册，否则会被吃掉
 */
import type { AxiosAdapter, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import type { Result } from '../types'

/** 处理器拿到的上下文：路径参数、查询串、请求体、幂等键、当前登录人 */
export interface MockCtx {
  params: Record<string, string>
  query: Record<string, string>
  body: Record<string, unknown>
  /** X-Idempotency-Key 请求头的值（没传为空串） */
  idemKey: string
  /** 当前登录人登录名（开发调试件写入 localStorage，见 stores/user.ts） */
  loginName: string
  /** 当前登录人角色（SUPER_ADMIN / DEPT_ADMIN / STUDENT，开发调试件） */
  role: string
}

/** 处理器内抛出 = 业务错误分支：拦截器按 code 弹提示并 reject */
export class MockBizError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.code = code
  }
}

interface MockHandler {
  method: string
  /** 路径模板，冒号开头段为参数占位（如 /api/forum/topics/:id） */
  path: string
  reply: (ctx: MockCtx) => unknown
}

/** 全部处理器按注册顺序排队的登记簿 */
const handlers: MockHandler[] = []

/** 各业务 mock 文件调它登记接口（顺序即匹配顺序，先注册先匹配） */
export function registerMock(method: string, path: string, reply: (ctx: MockCtx) => unknown): void {
  handlers.push({ method: method.toLowerCase(), path, reply })
}

/** 路径模板匹配：段数相同且非占位段全等才算命中，占位段收进 params */
function matchPath(pattern: string, actual: string): Record<string, string> | null {
  const pat = pattern.split('/').filter(Boolean)
  const act = actual.split('/').filter(Boolean)
  if (pat.length !== act.length) return null
  const params: Record<string, string> = {}
  for (let i = 0; i < pat.length; i++) {
    if (pat[i].startsWith(':')) {
      params[pat[i].slice(1)] = decodeURIComponent(act[i])
    } else if (pat[i] !== act[i]) {
      return null
    }
  }
  return params
}

/**
 * ⚠️ 防重复提交核心（规范 §6 幂等）：用过的幂等键登记在册，
 * 同键再次到达 = 重复提交，业务处理器调 markIdem 拿到 false 后抛 1005
 * 语义：markIdem(key) 首次见到该键返回 true 并记下；之后再见到返回 false
 */
const usedIdemKeys = new Set<string>()
export function markIdem(key: string): boolean {
  if (usedIdemKeys.has(key)) return false
  usedIdemKeys.add(key)
  return true
}

/** 当前登录人登录名/角色（开发调试件，sessionStorage 按标签页隔离）：真实联调后从 JWT 解析 */
const DEV_NAME_KEY = 'forum_dev_name'
const DEV_ROLE_KEY = 'forum_dev_role'
export function currentLoginName(): string {
  return sessionStorage.getItem(DEV_NAME_KEY) ?? '演示成员'
}
export function currentRole(): string {
  return sessionStorage.getItem(DEV_ROLE_KEY) ?? 'SUPER_ADMIN'
}
/** 管理操作权限口径：两档同权（超管+部门管理员，2026-10-17 P1 拍板；通用 1003，ForumErrorCode 无专用码） */
export function requireAdmin(): void {
  if (currentRole() !== 'SUPER_ADMIN' && currentRole() !== 'DEPT_ADMIN') {
    throw new MockBizError(1003, '无权执行管理操作（仅管理员）')
  }
}

/** axios 适配器：mock 模式下替代真实 XHR（http.ts 里挂进来） */
export const mockAdapter: AxiosAdapter = async (config: InternalAxiosRequestConfig) => {
  const method = (config.method ?? 'get').toLowerCase()
  const url = config.url ?? ''
  const ctx = buildCtx(config)
  for (let i = 0; i < handlers.length; i++) {
    const h = handlers[i]
    if (h.method !== method) continue
    const params = matchPath(h.path, url)
    if (params === null) continue
    ctx.params = params
    // 处理器抛 MockBizError = 业务错误（如 9002 无权限），转统一响应体走拦截器错误分支
    try {
      return wrapResult(config, h.reply(ctx))
    } catch (e) {
      if (e instanceof MockBizError) return wrapError(config, e.code, e.message)
      throw e
    }
  }
  return wrapError(config, 500, 'mock 未登记该接口: ' + method.toUpperCase() + ' ' + url)
}

/** 组装上下文：查询串、请求体、幂等键、登录人 */
function buildCtx(config: AxiosRequestConfig): MockCtx {
  const query: Record<string, string> = {}
  const params = config.params as Record<string, unknown> | undefined
  if (params) {
    for (const k of Object.keys(params)) {
      if (params[k] !== undefined && params[k] !== null && params[k] !== '') {
        query[k] = String(params[k])
      }
    }
  }
  let body: Record<string, unknown> = {}
  if (typeof config.data === 'string' && config.data.length > 0) {
    try {
      body = JSON.parse(config.data) as Record<string, unknown>
    } catch {
      body = {}
    }
  } else if (config.data && typeof config.data === 'object') {
    body = config.data as Record<string, unknown>
  }
  const headers = config.headers as Record<string, unknown> | undefined
  const idemKey = String(headers?.['X-Idempotency-Key'] ?? '')
  return { params: {}, query, body, idemKey, loginName: currentLoginName(), role: currentRole() }
}

/** 成功分支：code=0 包壳（拦截器只认 code=0 放行） */
function wrapResult(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  const body: Result = { code: 0, message: 'ok', data }
  return { data: body, status: 200, statusText: 'OK', headers: {}, config }
}

/** 业务错误分支：处理器的 MockBizError 或未登记兜底，都走统一响应体 */
function wrapError(config: InternalAxiosRequestConfig, code: number, message: string): AxiosResponse {
  const body: Result = { code, message, data: null }
  return { data: body, status: 200, statusText: 'OK', headers: {}, config }
}
