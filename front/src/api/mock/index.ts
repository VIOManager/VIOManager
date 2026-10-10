/**
 * 本地假数据层入口（VITE_USE_MOCK=true 时由 http.ts 装配，接管全部请求）
 * 依据：《前端ABC模块页面开发-优化方案》§3——Apifox 项目未建、清单待录入，本地 mock 先行；
 *    接口路径与字段严格照 docs/api/ 三份清单，后端就绪后 VITE_USE_MOCK=false 即切真
 * ⚠️ 冲突：mock 里的分页/筛选只是演示实现，真实行为以后端为准；写操作只改内存数组，刷新即还原
 * TODO(联调)：各模块 mock 文件随联调逐个删除，本入口最后退役
 * 注册表在 ./registry（叶子模块，拆出原因见其文件头注释）
 */
import type { AxiosRequestConfig, AxiosResponse } from 'axios'
import type { Result } from '@/api/types'
import { checkToken, fail, matchHandler } from './registry'

function readToken(config: AxiosRequestConfig): string {
  const h = config.headers as Record<string, string> | undefined
  const auth = h?.Authorization ?? h?.authorization ?? ''
  return auth.startsWith('Bearer ') ? auth.slice(7) : ''
}

function readIdemKey(config: AxiosRequestConfig): string {
  const h = config.headers as Record<string, string> | undefined
  return h?.['X-Idempotency-Key'] ?? h?.['x-idempotency-key'] ?? ''
}

/** axios 自定义适配器：mock 模式入口（http.ts 装配） */
export async function mockAdapter(config: AxiosRequestConfig): Promise<AxiosResponse> {
  await new Promise((r) => setTimeout(r, 150)) // 模拟网络延迟，loading 态可见
  const url = config.url ?? ''
  const method = (config.method ?? 'get').toLowerCase() as 'get' | 'post' | 'put' | 'delete'
  const token = readToken(config)
  // 登录以外的请求先过 token 校验（模拟真实后端的 1002 踢出）
  if (url !== '/api/auth/login' && !checkToken(token)) {
    return { data: fail(1002, '登录已过期'), status: 200, statusText: 'OK', headers: {}, config: config as never }
  }
  const hit = matchHandler(method, url)
  let bodyData: unknown = config.data
  if (typeof bodyData === 'string' && bodyData.length > 0) {
    try {
      bodyData = JSON.parse(bodyData)
    } catch {
      bodyData = {}
    }
  }
  // 注意：文件上传的 FormData 原样透传给处理器（files mock 里自行 .get('file')）
  const body: Result = hit
    ? hit.h.reply({ params: hit.params, query: (config.params as Record<string, unknown>) ?? {}, body: (bodyData as Record<string, unknown>) ?? {}, token, idemKey: readIdemKey(config) })
    : fail(1404, `mock 未注册：${method} ${url}`)
  return { data: body, status: 200, statusText: 'OK', headers: {}, config: config as never }
}

// ── dev 探针：验证 http 拦截器三种分流（1002 踢登录 / 1005 重复提交 / 业务码弹 message）──
// TODO(联调)：探针随 mock 层一并删除
import { registerMock } from './registry'
registerMock({ method: 'get', path: '/api/_probe/1002', reply: () => fail(1002, '登录已过期') })
registerMock({ method: 'get', path: '/api/_probe/1005', reply: () => fail(1005, '请勿重复提交') })
registerMock({ method: 'get', path: '/api/_probe/4002', reply: () => fail(4002, '队名已存在') })

// ── 模块 mock 登记（新增模块在此追加一行）──
import './auth'
import './dict'
import './files'
import './org'
import './member'
import './semester'
import './contest'
import './contest-templates'
import './approval'
import './team'
import './material'
import './material-submit'
import './achievement'
