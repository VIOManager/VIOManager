/**
 * 论坛站用户状态
 * 对应：架构方案 §4——JWT 两服务共享，论坛只验签不查库
 * ⚠️ 安全：token 存 sessionStorage（每标签页独立身份，与主站同策略）；开发调试键同存
 *    sessionStorage，多窗口演示不同角色互不串号
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

/** base64url 安全解码：JWT 载荷是 url 变体（-_ 字符、无补位），需转标准 base64 再 atob */
function b64urlDecode(s: string): string {
  let b = s.replace(/-/g, "+").replace(/_/g, "/")
  while (b.length % 4 !== 0) b += "="
  return atob(b)
}

function claimsFromJwt(token: string): { realName: string | null; role: string | null } {
  const parts = token.split('.')
  if (parts.length !== 3) return { realName: null, role: null }
  try {
    const payload = JSON.parse(decodeURIComponent(escape(b64urlDecode(parts[1]))))
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
    const token = sessionStorage.getItem(TOKEN_KEY) ?? ''
    const claims = claimsFromJwt(token)
    const name = claims.realName ?? sessionStorage.getItem(DEV_NAME_KEY) ?? '演示成员'
    const role = claims.role ?? sessionStorage.getItem(DEV_ROLE_KEY) ?? 'SUPER_ADMIN'
    return { token, realName: name, initials: name.slice(0, 1), role }
  },
  getters: {
    loggedIn: (s) => s.token.length > 0,
    /** 管理档（F11-F14 管理操作、含隐藏开关）：超管+部门管理员两档同权（2026-10-17 P1 拍板，对齐后端 @RequireRole） */
    isAdmin: (s) => s.role === 'SUPER_ADMIN' || s.role === 'DEPT_ADMIN',
    /** 置顶专属：后端发帖时仅超管置顶生效（两档之外唯一的口径差异点，勾选框显隐用） */
    isSuperAdmin: (s) => s.role === 'SUPER_ADMIN'
  },
  actions: {
    /**
     * ⚠️ 开发调试件：写入 stub token 并同步 store 与调试键（与主站登录桩同性质，联调后移除）
     * name/role 落 sessionStorage 供 mock 层读取（见 api/mock/registry.ts，按标签页隔离）
     */
    devStubLogin(name?: string, role?: string) {
      const n = name ?? '演示成员'
      const r = role ?? 'SUPER_ADMIN'
      this.token = 'dev-stub-token'
      this.realName = n
      this.initials = n.slice(0, 1)
      this.role = r
      sessionStorage.setItem(TOKEN_KEY, this.token)
      sessionStorage.setItem(DEV_NAME_KEY, n)
      sessionStorage.setItem(DEV_ROLE_KEY, r)
    },
    /**
     * dev token 交接（方案 §7.1）：主站入口以 ?token= 带来真 JWT，落库并解析载荷；
     * 门页调试登录换到的真 token 同走这里。载荷解析失败（桩 token）时保留现有展示身份
     */
    acceptToken(token: string) {
      this.token = token
      const claims = claimsFromJwt(token)
      if (claims.realName !== null) {
        this.realName = claims.realName
        this.initials = claims.realName.slice(0, 1)
      }
      if (claims.role !== null) this.role = claims.role
      sessionStorage.setItem(TOKEN_KEY, token)
    }
  }
})
