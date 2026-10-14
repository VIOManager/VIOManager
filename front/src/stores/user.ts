/**
 * 用户状态（token / 角色 / 姓名 / 默认密码标记）
 * 对应方案：《前端ABC模块页面开发-优化方案》FS2（登录改密闭环）
 * ⚠️ 安全：role 仅用于菜单显隐等展示层逻辑，接口数据权限一律由后端判定（规范 §5.3）
 * ⚠️ 安全：token 存 sessionStorage（每标签页一把独立身份，多窗口测试互不串号）；
 *    token 注入请求头只允许在 axios 实例统一做（http.ts），页面禁止手工拼
 */
import { defineStore } from 'pinia'
import { apiChangePassword, apiLogin, apiMe } from '@/api/auth'
import type { LoginResp } from '@/api/auth'
import type { Role } from '@/types'

const TOKEN_KEY = 'vio_token'

interface UserState {
  token: string
  role: Role
  realName: string
  /** 1=仍用默认密码（TopBar 据此自动弹改密引导，PRD §3.1） */
  defaultPassword: 0 | 1
}

/** 解析 JWT 载荷（token 本身自带 role/realName，恢复身份不必调接口——骨架暂无 /api/auth/me） */

/** base64url 安全解码：JWT 载荷是 url 变体（-_ 字符、无补位），需转标准 base64 再 atob */
function b64urlDecode(s: string): string {
  let b = s.replace(/-/g, "+").replace(/_/g, "/")
  while (b.length % 4 !== 0) b += "="
  return atob(b)
}

function claimsFromJwt(token: string): { role: Role | null; realName: string | null } {
  const parts = token.split('.')
  if (parts.length !== 3) return { role: null, realName: null }
  try {
    const payload = JSON.parse(decodeURIComponent(escape(b64urlDecode(parts[1]))))
    return {
      role: typeof payload.role === 'string' ? (payload.role as Role) : null,
      realName: typeof payload.realName === 'string' ? payload.realName : null
    }
  } catch {
    return { role: null, realName: null }
  }
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: sessionStorage.getItem(TOKEN_KEY) ?? '',
    role: 'SUPER_ADMIN',
    realName: '',
    defaultPassword: 0
  }),
  actions: {
    /** 登录：落 token 与身份；页面不需要返回值（引导弹窗由 TopBar 监听 defaultPassword） */
    async login(loginName: string, password: string): Promise<LoginResp> {
      const resp = await apiLogin({ loginName, password })
      this.token = resp.token
      this.role = resp.role
      this.realName = resp.realName
      this.defaultPassword = resp.defaultPassword
      sessionStorage.setItem(TOKEN_KEY, resp.token)
      // 迁移清理：旧方案 token 存 localStorage，新代码不再读它，清掉防困惑
      localStorage.removeItem(TOKEN_KEY)
      return resp
    },
    /** 刷新后凭 token 恢复身份：真 JWT 直接解析载荷（零请求）；mock 桩 token 走 /api/auth/me */
    async restore(): Promise<void> {
      if (!this.token) return
      const claims = claimsFromJwt(this.token)
      if (claims.role || claims.realName) {
        if (claims.role) this.role = claims.role
        if (claims.realName) this.realName = claims.realName
        // JWT 载荷无 defaultPassword 标记；改密引导待骨架补 /api/auth/me 后恢复
        this.defaultPassword = 0
        return
      }
      const resp = await apiMe()
      this.role = resp.role
      this.realName = resp.realName
      this.defaultPassword = resp.defaultPassword
    },
    /** 改密：成功即登出（PRD：改后强制重新登录；旧 token 已被服务端作废） */
    async changePassword(oldPassword: string, newPassword: string): Promise<void> {
      await apiChangePassword({ oldPassword, newPassword })
      this.logout()
    },
    logout() {
      this.token = ''
      this.role = 'SUPER_ADMIN'
      this.realName = ''
      this.defaultPassword = 0
      sessionStorage.removeItem(TOKEN_KEY)
    },
    /** ⚠️ 开发调试件：顶栏角色切换器用，验证三端菜单显隐，联调后移除（角色实际来自登录响应） */
    setRole(role: Role) {
      this.role = role
    }
  }
})
