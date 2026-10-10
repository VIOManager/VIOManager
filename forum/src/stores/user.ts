/**
 * 论坛站用户状态
 * 对应：架构方案 §4——JWT 两服务共享，论坛只验签不查库
 * ⚠️ 安全：token 与主站共用 localStorage 键 vio_token（同一浏览器的同团队站点）；
 *    论坛不签发、不刷新 token，过期后引导回主站登录
 * TODO(联调)：realName/role 从 JWT 载荷解析（载荷含 userId role orgId realName），
 *    当前桩 token 非 JWT，解析失败走默认值
 */
import { defineStore } from 'pinia'

const TOKEN_KEY = 'vio_token'

interface ForumUserState {
  token: string
  realName: string
  initials: string
}

/** 尝试解析 JWT 载荷取 realName；桩 token 或解析失败返回 null */
function realNameFromJwt(token: string): string | null {
  const parts = token.split('.')
  if (parts.length !== 3) return null
  try {
    const payload = JSON.parse(decodeURIComponent(escape(atob(parts[1]))))
    return typeof payload.realName === 'string' ? payload.realName : null
  } catch {
    return null
  }
}

export const useUserStore = defineStore('forum-user', {
  state: (): ForumUserState => {
    const token = localStorage.getItem(TOKEN_KEY) ?? ''
    const name = realNameFromJwt(token) ?? '成员'
    return { token, realName: name, initials: name.slice(0, 1) }
  },
  getters: {
    loggedIn: (s) => s.token.length > 0
  },
  actions: {
    /** ⚠️ 开发调试件：写入 stub token 并同步 store（与主站登录桩同性质，联调后移除） */
    devStubLogin() {
      this.token = 'dev-stub-token'
      this.realName = '成员'
      this.initials = '成'
      localStorage.setItem(TOKEN_KEY, this.token)
    }
  }
})
