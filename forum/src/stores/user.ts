/**
 * 论坛站用户状态
 * 对应：架构方案 §4——JWT 两服务共享，论坛只验签不查库
 * ⚠️ 安全：token 与主站共用 localStorage 键 vio_token（部署后同域共享；开发期两端口
 *    localStorage 隔离，用 devStubLogin 写论坛自己的键）
 * TODO(联调)：realName/role 从 JWT 载荷解析（载荷含 userId role orgId realName），
 *    当前桩 token 非 JWT，解析失败走默认值
 */
import { defineStore } from 'pinia'

const TOKEN_KEY = 'vio_token'
// 开发调试件键：mock 层据此判定当前登录人（联调后移除，改从 JWT 解析）
const DEV_NAME_KEY = 'forum_dev_name'
const DEV_ROLE_KEY = 'forum_dev_role'

interface ForumUserState {
  token: string
  realName: string
  initials: string
  role: string
}

/** 尝试解析 JWT 载荷取 realName/role；桩 token 或解析失败返回 null */
function claimsFromJwt(token: string): { realName: string | null; role: string | null } {
  const parts = token.split('.')
  if (parts.length !== 3) return { realName: null, role: null }
  try {
    const payload = JSON.parse(decodeURIComponent(escape(atob(parts[1]))))
    return {
      realName: typeof payload.realName === 'string' ? payload.realName : null,
      role: typeof payload.role === 'string' ? payload.role : null
    }
  } catch {
    return { realName: null, role: null }
  }
}

export const useUserStore = defineStore('forum-user', {
  state: (): ForumUserState => {
    const token = localStorage.getItem(TOKEN_KEY) ?? ''
    const claims = claimsFromJwt(token)
    const name = claims.realName ?? localStorage.getItem(DEV_NAME_KEY) ?? '演示成员'
    const role = claims.role ?? localStorage.getItem(DEV_ROLE_KEY) ?? 'SUPER_ADMIN'
    return { token, realName: name, initials: name.slice(0, 1), role }
  },
  getters: {
    loggedIn: (s) => s.token.length > 0,
    /** 管理操作（置顶/录入/撤回）显隐用，口径同技术交流清单 9002：仅超管 */
    isAdmin: (s) => s.role === 'SUPER_ADMIN'
  },
  actions: {
    /**
     * ⚠️ 开发调试件：写入 stub token 并同步 store 与调试键（与主站登录桩同性质，联调后移除）
     * name/role 落 localStorage 供 mock 层读取（见 api/mock/registry.ts）
     */
    devStubLogin(name?: string, role?: string) {
      const n = name ?? '演示成员'
      const r = role ?? 'SUPER_ADMIN'
      this.token = 'dev-stub-token'
      this.realName = n
      this.initials = n.slice(0, 1)
      this.role = r
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(DEV_NAME_KEY, n)
      localStorage.setItem(DEV_ROLE_KEY, r)
    },
    /** 切换演示角色（顶栏调试下拉用）：只换身份键 */
    devSwitchRole(name: string, role: string) {
      this.devStubLogin(name, role)
    }
  }
})
