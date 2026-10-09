/**
 * mock 注册表（叶子模块：不引用任何运行时模块，供各模块 mock 文件安全导入）
 * 拆分原因：auth 等模块在 index.ts 尾部被 import 时会先于 index 主体执行（ESM 循环提升），
 *    若注册表状态写在本文件会撞暂时性死区——故独立成叶子模块
 */
import type { Result } from '@/api/types'

/** mock 处理器入参：路径参数 / query / 请求体 / 当前 token（从 Authorization 头解析） */
export interface MockCtx {
  params: Record<string, string>
  query: Record<string, unknown>
  body: Record<string, unknown>
  token: string
}

export interface MockHandler {
  method: 'get' | 'post' | 'put' | 'delete'
  /** 路径模式，:xx 匹配单层路径段（如 /api/members/:id） */
  path: string
  reply: (ctx: MockCtx) => Result
}

const handlers: MockHandler[] = []

/** 各模块 mock 文件统一经此注册 */
export function registerMock(h: MockHandler): void {
  handlers.push(h)
}

export function ok<T>(data: T): Result<T> {
  return { code: 0, message: 'ok', data }
}

export function fail(code: number, message: string): Result {
  return { code, message, data: null }
}

/**
 * token 校验钩子（由 mock/auth.ts 登记）：模拟"改密/停用后旧 token 失效"
 * 仅登录接口豁免校验；其余请求 token 无效一律 1002
 */
let tokenChecker: ((token: string) => boolean) | null = null
export function setTokenChecker(fn: (token: string) => boolean): void {
  tokenChecker = fn
}
export function checkToken(token: string): boolean {
  return tokenChecker ? tokenChecker(token) : true
}

/** 路径匹配：:xx 段回填参数名 */
export function matchHandler(method: string, url: string): { h: MockHandler; params: Record<string, string> } | null {
  for (const h of handlers) {
    if (h.method !== method) continue
    const names: string[] = []
    const re = new RegExp(
      '^' + h.path.replace(/:[^/]+/g, (seg) => {
        names.push(seg.slice(1))
        return '([^/]+)'
      }) + '$'
    )
    const m = re.exec(url)
    if (!m) continue
    const params: Record<string, string> = {}
    names.forEach((n, i) => {
      params[n] = m[i + 1]
    })
    return { h, params }
  }
  return null
}
