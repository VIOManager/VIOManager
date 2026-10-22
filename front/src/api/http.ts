/**
 * axios 封装（全站唯一请求出口）
 * 对应方案：《前端骨架-开发方案》Step 1；《前端ABC模块页面开发-优化方案》FS1
 * 拦截规则（规范 §2.2 + 骨架接口清单）：
 *   code=0    放行，返回 data
 *   1002      清 token 踢回登录页（登录过期/改密后旧 token/账号停用）
 *   1005      提示"请勿重复提交"（幂等命中）
 *   其余业务码 ElMessage 弹 message，reject 交页面兜底
 *   HTTP 层异常（断网/5xx 无响应体）统一兜底提示；带统一响应体的 4xx 按业务码分流弹真实 message
 * ⚠️ 安全：token 注入 Authorization 头只允许在本文件做，页面禁止手工拼（规范 §5.3）
 * ⚠️ 安全：本文件只做展示层分流，不构成任何权限依据
 */
import axios from 'axios'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { Result } from './types'
import { mockAdapter } from './mock'

// token 存 sessionStorage：按标签页隔离——多窗口多账号测试互不串号（2026-10-14 lzh 测试需求）；
// 刷新保留、关标签页即登出；2 小时时效短 token 与此存储策略匹配
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
  const token = sessionStorage.getItem(TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (resp: AxiosResponse<Result>) => {
    // 文件流响应（导出/打包下载）没有统一响应体外壳，直接放行给 download() 处理
    if (resp.config.responseType === 'blob') return resp as never
    const body = resp.data
    if (body.code === 0) return body.data as never
    if (body.code === 1002) {
      // 登录失效：清 token 整页跳登录（整页刷新顺带清空内存态，最朴素也最稳）
      sessionStorage.removeItem(TOKEN_KEY)
      ElMessage.error(body.message || '登录已失效，请重新登录')
      window.location.assign('/login')
      return Promise.reject(new BizError(body.code, body.message))
    }
    // 静默请求（silent 标记）不弹统一提示，交调用方自行兜底
    const silent = (resp.config as unknown as { silent?: boolean }).silent === true
    if (body.code === 1005) {
      if (!silent) ElMessage.warning('请勿重复提交')
      return Promise.reject(new BizError(body.code, body.message))
    }
    if (!silent) ElMessage.error(body.message || '操作失败')
    return Promise.reject(new BizError(body.code, body.message))
  },
  (err) => {
    const silent = ((err.config ?? {}) as unknown as { silent?: boolean }).silent === true
    // HTTP 层 401：后端 JWT 过滤器直接回 401 状态码 + code 1002 响应体（后端重启/令牌过期走这里，
    // 不经过上方 2xx 分支的 1002 处理）——同样清 token 踢回登录，
    // 否则用户只会看到误导性的"网络异常"满屏刷（2026-10-17 组织管理排查定位）
    if (err.response?.status === 401) {
      const body = err.response.data as Result
      sessionStorage.removeItem(TOKEN_KEY)
      if (!silent) ElMessage.error(body?.message || '登录已失效，请重新登录')
      window.location.assign('/login')
      return Promise.reject(new BizError(1002, body?.message ?? '未登录或令牌已过期'))
    }
    // HTTP 4xx/5xx 但带统一响应体：后端部分业务拒绝直接回 HTTP 403/400 + code/message 响应体
    // （如审批中心 P01 视角对无档案账号回 1003"当前账号未绑定人员档案"），与 2xx 分支同口径
    // 弹真实 message——否则原因被"网络异常"吞掉（2026-10-19 审批中心超管视角排查定位）
    const errBody = err.response?.data as Result | undefined
    if (errBody && typeof errBody.code === 'number') {
      if (errBody.code === 1005) {
        if (!silent) ElMessage.warning('请勿重复提交')
        return Promise.reject(new BizError(errBody.code, errBody.message))
      }
      if (!silent) ElMessage.error(errBody.message || '操作失败')
      return Promise.reject(new BizError(errBody.code, errBody.message))
    }
    if (!silent) ElMessage.error('网络异常，请稍后重试')
    return Promise.reject(err)
  }
)

/**
 * 页面侧一律用这几个薄封装（直接返回 data 类型，不写 resp.data.data 链）
 * opts.silent=true：失败不弹统一错误提示（联调期"接口后端未实现"的降级场景专用，
 * 调用方自行 catch 兜底——如学期接口未实现时展示兜底数据）
 */
export async function get<T>(url: string, params?: Record<string, unknown>, opts?: { silent?: boolean }): Promise<T> {
  const cfg = { params } as Record<string, unknown>
  if (opts?.silent) cfg.silent = true
  return (await http.get(url, cfg as never)) as unknown as T
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

/**
 * 带幂等键的 POST（规范 §6：写操作要求 X-Idempotency-Key 头，键由调用方生成）
 * ⚠️ 键的语义是"一张表单一个键"（失败重试复用同键防断网重复提交）；
 *    api 层内部生成键的用法（B/C 线联调临时口径）只保证请求能过幂等拦截，
 *    防断网重复创建要靠页面级 formKey——各弹窗组件标注 TODO(联调) 逐步补
 */
export async function postIdem<T>(url: string, data: unknown, idempotencyKey: string): Promise<T> {
  return (await http.post(url, data, { headers: { 'X-Idempotency-Key': idempotencyKey } })) as unknown as T
}

/** 带幂等键的 PUT / DELETE（后端 @Idempotent 对编辑删除同样要求请求头） */
export async function putIdem<T>(url: string, data: unknown, idempotencyKey: string): Promise<T> {
  return (await http.put(url, data, { headers: { 'X-Idempotency-Key': idempotencyKey } })) as unknown as T
}
export async function delIdem<T>(url: string, idempotencyKey: string): Promise<T> {
  return (await http.delete(url, { headers: { 'X-Idempotency-Key': idempotencyKey } })) as unknown as T
}

/** 文件流下载（导出/打包）：blob 拉取后触发浏览器保存，文件名优先取响应头 */
export async function download(url: string, fallbackName: string): Promise<void> {
  const resp = (await http.get(url, { responseType: 'blob' })) as unknown as AxiosResponse<Blob>
  const cd = String(resp.headers?.['content-disposition'] ?? '')
  const m = /filename\*?=(?:UTF-8''|")?([^";]+)/i.exec(cd)
  const name = m ? decodeURIComponent(m[1].replace(/"/g, '')) : fallbackName
  const href = URL.createObjectURL(resp.data)
  const a = document.createElement('a')
  a.href = href
  a.download = name
  a.click()
  URL.revokeObjectURL(href)
}

/** 文件上传专用：multipart 表单 + X-Idempotency-Key 幂等键（规范 §6，键由调用方生成）
 *  fields 为随表单一起提交的附加字段（如文件服务必填的 bizType 业务来源）
 *  timeoutMs 可选：同步解析大文件（如全校课表导入）时放宽，缺省走实例默认 15s */
export async function upload<T>(url: string, file: File, idempotencyKey: string, fields?: Record<string, string>, timeoutMs?: number): Promise<T> {
  const fd = new FormData()
  fd.append('file', file)
  if (fields) {
    for (const k of Object.keys(fields)) fd.append(k, fields[k])
  }
  return (await http.post(url, fd, { headers: { 'X-Idempotency-Key': idempotencyKey }, timeout: timeoutMs })) as unknown as T
}

// ⚠️ 开发自验件：浏览器控制台 __http.get('/api/_probe/1002') 可验证三种拦截分支；
// import.meta.env.DEV 在构建期为 false，会被整段摇掉，不进生产包
if (import.meta.env.DEV) {
  ;(window as unknown as Record<string, unknown>).__http = { get, post, put, del, upload, postIdem, putIdem, delIdem }
}
