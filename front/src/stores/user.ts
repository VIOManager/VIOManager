/**
 * 用户状态（token / 角色 / 姓名 / 默认密码标记）
 * 对应方案：《前端ABC模块页面开发-优化方案》FS2（登录改密闭环）
 * ⚠️ 安全：role 仅用于菜单显隐等展示层逻辑，接口数据权限一律由后端判定（规范 §5.3）
 * ⚠️ 安全：token 存 localStorage 是骨架方案待确认项的当前实现（刷新保活）；
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

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: localStorage.getItem(TOKEN_KEY) ?? '',
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
      localStorage.setItem(TOKEN_KEY, resp.token)
      return resp
    },
    /** 刷新后凭 token 恢复身份；token 已失效时由 http 拦截器统一踢回登录页 */
    async restore(): Promise<void> {
      if (!this.token) return
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
      localStorage.removeItem(TOKEN_KEY)
    },
    /** ⚠️ 开发调试件：顶栏角色切换器用，验证三端菜单显隐，联调后移除（角色实际来自登录响应） */
    setRole(role: Role) {
      this.role = role
    }
  }
})
