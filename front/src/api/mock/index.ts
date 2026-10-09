/**
 * 本地假数据层（VITE_USE_MOCK=true 时由 http.ts 装配，接管全部请求）
 * 依据：《前端ABC模块页面开发-优化方案》§3——Apifox 项目未建、清单待录入，本地 mock 先行；
 *    接口路径与字段严格照 docs/api/ 三份清单，后端就绪后 VITE_USE_MOCK=false 即切真
 * ⚠️ 冲突：mock 里的分页/筛选只是演示实现，真实行为以后端为准；写操作只改内存数组，刷新即还原
 * TODO(联调)：各模块 mock 文件随联调逐个删除，本注册表最后退役
 */
import type { AxiosRequestConfig, AxiosResponse } from 'axios'
import type { Result } from '@/api/types'

/** mock 处理器入参：路径参数 / query / 请求体 */
export interface MockCtx {
  params: Record<string, string>
  query: Record<string, unknown>
  body: Record<string, unknown>
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

/** 路径模式转正则；同时收集参数名供回填 */
function compile(path: string): { re: RegExp; names: string[] } {
  const names: string[] = []
  const p = path.replace(/:[^/]+/g, (seg) => {
    names.push(seg.slice(1))
    return '([^/]+)'
  })
  return { re: new RegExp('^' + p + '$'), names }
}

function match(method: string, url: string): { h: MockHandler; params: Record<string, string> } | null {
  for (const h of handlers) {
    if (h.method !== method) continue
    const { re, names } = compile(h.path)
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

/** axios 自定义适配器：mock 模式入口（http.ts 装配） */
export async function mockAdapter(config: AxiosRequestConfig): Promise<AxiosResponse> {
  await new Promise((r) => setTimeout(r, 150)) // 模拟网络延迟，loading 态可见
  const url = config.url ?? ''
  const method = (config.method ?? 'get').toLowerCase() as MockHandler['method']
  const hit = match(method, url)
  let bodyData: unknown = config.data
  if (typeof bodyData === 'string' && bodyData.length > 0) {
    try {
      bodyData = JSON.parse(bodyData)
    } catch {
      bodyData = {}
    }
  }
  const body: Result = hit
    ? hit.h.reply({ params: hit.params, query: (config.params as Record<string, unknown>) ?? {}, body: (bodyData as Record<string, unknown>) ?? {} })
    : fail(1404, `mock 未注册：${method} ${url}`)
  return { data: body, status: 200, statusText: 'OK', headers: {}, config: config as never }
}

// ── dev 探针：验证 http 拦截器三种分流（1002 踢登录 / 1005 重复提交 / 业务码弹 message）──
// TODO(联调)：探针随 mock 层一并删除
registerMock({ method: 'get', path: '/api/_probe/1002', reply: () => fail(1002, '登录已过期') })
registerMock({ method: 'get', path: '/api/_probe/1005', reply: () => fail(1005, '请勿重复提交') })
registerMock({ method: 'get', path: '/api/_probe/4002', reply: () => fail(4002, '队名已存在') })
