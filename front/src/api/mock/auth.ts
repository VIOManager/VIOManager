/**
 * 认证模块 mock：登录 / 改密 / 会话恢复
 * 对应方案：《前端ABC模块页面开发-优化方案》FS2
 * ⚠️ 演示数据：admin 账号刻意保持 defaultPassword=1，用于验证"登录即弹改密引导"；
 *    改密后 validTokens 全清，模拟真实后端"改密作废全部旧会话"（PRD §3.1）
 * 会话持久化：已签发 token 存 sessionStorage（刷新存活、关标签页即清），
 *    模拟真实后端"token 在服务端跨刷新有效"；用户表等其余写操作仍刷新还原
 * TODO(联调)：后端骨架认证接口就绪后删除本文件
 */
import { fail, ok, registerMock, setTokenChecker } from './registry'
import type { Role } from '@/types'

interface MockUser {
  id: string
  loginName: string
  password: string
  realName: string
  role: Role
  /** 1=默认密码未改 */
  defaultPassword: 0 | 1
}

const users: MockUser[] = [
  { id: '1', loginName: 'admin', password: 'abc123', realName: '李明', role: 'SUPER_ADMIN', defaultPassword: 1 },
  { id: '2', loginName: 'dept01', password: 'abc123', realName: '陈志远', role: 'DEPT_ADMIN', defaultPassword: 0 },
  { id: '3', loginName: '2023319024', password: 'abc123', realName: '王雨桐', role: 'STUDENT', defaultPassword: 0 }
]

/** sessionStorage 键：已签发 token 列表（跨刷新存活的"服务端会话"） */
const TOKEN_STORE_KEY = 'mock_valid_tokens'

function loadTokens(): Set<string> {
  try {
    const raw = sessionStorage.getItem(TOKEN_STORE_KEY)
    if (raw) return new Set(JSON.parse(raw) as string[])
  } catch {
    // 坏数据当作空会话处理
  }
  return new Set()
}

const validTokens = loadTokens()

function saveTokens(): void {
  sessionStorage.setItem(TOKEN_STORE_KEY, JSON.stringify(Array.from(validTokens)))
}

/** token 形如 mock-token-{用户id}-{序号}；me 等接口据此反查用户 */
function findUserByToken(token: string): MockUser | null {
  const m = /^mock-token-(\d+)-\d+$/.exec(token)
  if (!m) return null
  for (const u of users) {
    if (u.id === m[1]) return u
  }
  return null
}

setTokenChecker((token) => validTokens.has(token))

registerMock({
  method: 'post',
  path: '/api/auth/login',
  reply: (ctx) => {
    const loginName = String(ctx.body.loginName ?? '')
    const password = String(ctx.body.password ?? '')
    let found: MockUser | null = null
    for (const u of users) {
      if (u.loginName === loginName && u.password === password) {
        found = u
        break
      }
    }
    if (!found) return fail(2001, '账号或密码错误')
    const token = 'mock-token-' + found.id + '-' + Date.now()
    validTokens.add(token)
    saveTokens()
    return ok({ token, realName: found.realName, role: found.role, defaultPassword: found.defaultPassword })
  }
})

registerMock({
  method: 'get',
  path: '/api/auth/me',
  reply: (ctx) => {
    const found = findUserByToken(ctx.token)
    if (!found) return fail(1002, '登录已过期')
    return ok({ realName: found.realName, role: found.role, defaultPassword: found.defaultPassword })
  }
})

registerMock({
  method: 'put',
  path: '/api/auth/password',
  reply: (ctx) => {
    const found = findUserByToken(ctx.token)
    if (!found) return fail(1002, '登录已过期')
    const oldPassword = String(ctx.body.oldPassword ?? '')
    const newPassword = String(ctx.body.newPassword ?? '')
    if (oldPassword !== found.password) return fail(2002, '原密码不正确')
    if (newPassword === oldPassword) return fail(2003, '新密码不能与原密码相同')
    found.password = newPassword
    found.defaultPassword = 0
    // 改密作废全部旧会话：旧 token 再请求即 1002 被踢回登录页
    validTokens.clear()
    saveTokens()
    return ok(null)
  }
})
