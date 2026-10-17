/**
 * 论坛站 axios 封装（论坛全站唯一请求出口）
 * 对应：主站 front/src/api/http.ts 同构复刻；《微服务拆分与论坛-架构调整方案》§4
 * 拦截规则（规范 §2.2）：
 *   code=0    放行，返回 data
 *   1002      清 token 引导回主站登录页（JWT 由主站签发，论坛不设登录页）
 *   1005      提示"请勿重复提交"（幂等键命中——防重复提交的契约层防线）
 *   其余业务码 ElMessage 弹 message，reject 交页面兜底
 *   HTTP 层异常（断网/超时/5xx）统一兜底提示，本地数据不动，可安全重试
 * ⚠️ 安全：token 注入 Authorization 头只允许在本文件做，页面禁止手工拼（规范 §5.3）
 * ⚠️ 安全：本文件只做展示层分流，不构成任何权限依据
 */
import axios from 'axios'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { Result } from './types'
import { mockAdapter } from './mock'

const TOKEN_KEY = 'vio_token'
const useMock = import.meta.env.VITE_USE_MOCK === 'true'

/** 业务错误：code + message 随 reject 传出，页面可按 code 做差异化提示 */
export class BizError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.code = code
  }
}

const http = axios.create({
  // 留空 = 相对路径：开发走 vite 代理（vite.config /api → 论坛服务 8089）、生产同域 Nginx 分流，
  // 两态都无需完整地址；指向独立部署的论坛服务时才在 env 里填 VITE_API_BASE_URL
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 15000,
  // mock 模式接管适配器；真实模式走默认 XHR（联调时 .env 里关 VITE_USE_MOCK 即可）
  adapter: useMock ? mockAdapter : undefined
})

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = sessionStorage.getItem(TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (resp: AxiosResponse<Result>) => {
    const body = resp.data
    if (body.code === 0) return body.data as never
    if (body.code === 1002) {
      // 登录失效：清 token 整页跳主站登录（整页刷新顺带清空内存态，最朴素也最稳）
      sessionStorage.removeItem(TOKEN_KEY)
      ElMessage.error(body.message || '登录已失效，请回主站重新登录')
      window.location.assign(import.meta.env.VITE_MAIN_SITE_URL ?? 'http://localhost:5174/login')
      return Promise.reject(new BizError(body.code, body.message))
    }
    if (body.code === 1005) {
      ElMessage.warning('请勿重复提交')
      return Promise.reject(new BizError(body.code, body.message))
    }
    ElMessage.error(body.message || '操作失败')
    return Promise.reject(new BizError(body.code, body.message))
  },
  () => {
    // 断网/超时/5xx 兜底：不吞错也不改本地状态，用户看到原列表，重试安全
    ElMessage.error('网络异常，请稍后重试')
    return Promise.reject(new Error('network-error'))
  }
)

// 页面侧一律用这几个薄封装（直接返回 data 类型，不写 resp.data.data 链）
export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return (await http.get(url, { params })) as unknown as T
}
export async function post<T>(url: string, data?: unknown): Promise<T> {
  return (await http.post(url, data)) as unknown as T
}
export async function put<T>(url: string, data?: unknown): Promise<T> {
  return (await http.put(url, data)) as unknown as T
}

/**
 * 带幂等键的 POST（规范 §6：发帖/回帖/录入/撤回要求 X-Idempotency-Key 头）
 * ⚠️ 键由调用方生成——防重复提交的约定是"一张表单一个键"：
 *    表单打开时生成，网络失败重试继续用同一个键（后端首次已收到则 1005 拦下），
 *    成功或关闭表单后才换新键。详见各弹窗组件内 formKey 注释
 */
export async function postIdem<T>(url: string, data: unknown, idempotencyKey: string): Promise<T> {
  return (await http.post(url, data, { headers: { 'X-Idempotency-Key': idempotencyKey } })) as unknown as T
}

// ⚠️ 开发自验件：浏览器控制台 __http.get('/api/forum/topics') 可直接打接口；
// 幂等演示：__http.postIdem('/api/forum/topics', {...}, 'k1') 连发两次同键，第二次 1005
// import.meta.env.DEV 在构建期为 false，会被整段摇掉，不进生产包
if (import.meta.env.DEV) {
  ;(window as unknown as Record<string, unknown>).__http = { get, post, put, postIdem }
}
