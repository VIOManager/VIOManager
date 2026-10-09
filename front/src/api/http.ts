/**
 * axios 封装（全站唯一请求出口）
 * 对应方案：《前端骨架-开发方案》Step 1；《前端ABC模块页面开发-优化方案》FS1
 * 拦截规则（规范 §2.2 + 骨架接口清单）：
 *   code=0    放行，返回 data
 *   1002      清 token 踢回登录页（登录过期/改密后旧 token/账号停用）
 *   1005      提示"请勿重复提交"（幂等命中）
 *   其余业务码 ElMessage 弹 message，reject 交页面兜底
 *   HTTP 层异常（断网/5xx）统一兜底提示
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
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8088',
  timeout: 15000,
  // mock 模式接管适配器；真实模式走默认 XHR
  adapter: useMock ? mockAdapter : undefined
})

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (resp: AxiosResponse<Result>) => {
    const body = resp.data
    if (body.code === 0) return body.data as never
    if (body.code === 1002) {
      // 登录失效：清 token 整页跳登录（整页刷新顺带清空内存态，最朴素也最稳）
      localStorage.removeItem(TOKEN_KEY)
      ElMessage.error(body.message || '登录已失效，请重新登录')
      window.location.assign('/login')
      return Promise.reject(new BizError(body.code, body.message))
    }
    if (body.code === 1005) {
      ElMessage.warning('请勿重复提交')
      return Promise.reject(new BizError(body.code, body.message))
    }
    ElMessage.error(body.message || '操作失败')
    return Promise.reject(new BizError(body.code, body.message))
  },
  (err) => {
    ElMessage.error('网络异常，请稍后重试')
    return Promise.reject(err)
  }
)

// 页面侧一律用这四个薄封装（直接返回 data 类型，不写 resp.data.data 链）
export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return (await http.get(url, { params })) as unknown as T
}
export async function post<T>(url: string, data?: unknown): Promise<T> {
  return (await http.post(url, data)) as unknown as T
}
export async function put<T>(url: string, data?: unknown): Promise<T> {
  return (await http.put(url, data)) as unknown as T
}
export async function del<T>(url: string): Promise<T> {
  return (await http.delete(url)) as unknown as T
}

// ⚠️ 开发自验件：浏览器控制台 __http.get('/api/_probe/1002') 可验证三种拦截分支；
// import.meta.env.DEV 在构建期为 false，会被整段摇掉，不进生产包
if (import.meta.env.DEV) {
  ;(window as unknown as Record<string, unknown>).__http = { get, post, put, del }
}
