/**
 * 论坛站用户状态
 * 对应：架构方案 §4——JWT 两服务共享，论坛只验签不查库
 * ⚠️ 安全：token 存 sessionStorage（每标签页独立身份，与主站同策略）；开发调试键同存
 *    sessionStorage，多窗口演示不同角色互不串号
 * TODO(联调)：realName/role 从 JWT 载荷解析（载荷含 userId role orgId realName），
 *    当前桩 token 非 JWT，解析失败走默认值
 */
import { defineStore } from 'pinia'
import { avatarOf as genAvatar } from '@/config/avatars'

const TOKEN_KEY = 'vio_token'
// 开发调试件键：mock 层据此判定当前登录人（联调后移除，改从 JWT 解析）
const DEV_NAME_KEY = 'forum_dev_name'
const DEV_ROLE_KEY = 'forum_dev_role'
// 自定义头像（演示期存 localStorage 模拟服务端档案；联调后改走文件服务 member.photo_file_id）
const AVATAR_KEY = 'forum_avatar'

interface ForumUserState {
  token: string
  realName: string
  initials: string
  role: string
  /** 自定义头像 dataURL；空串 = 未设，走 pravatar 映射（config/avatars） */
  avatar: string
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
    return { token, realName: name, initials: name.slice(0, 1), role, avatar: localStorage.getItem(AVATAR_KEY) ?? '' }
  },
  getters: {
    loggedIn: (s) => s.token.length > 0,
    /** 管理操作（置顶/录入/撤回）显隐用，口径同技术交流清单 9002：仅超管 */
    isAdmin: (s) => s.role === 'SUPER_ADMIN',
    /**
     * 按名字取头像（全站渲染统一走这里，勿直连 config/avatars）：
     * 是本人且已设自定义头像 → 自定义；其余成员 → pravatar 固定映射。
     * 返回函数的形式让模板可传参（name, size），闭包内读 state 保证换头像后触发重渲染
     */
    avatarByName: (s) => (name: string, size: number) =>
      s.avatar && name === s.realName ? s.avatar : genAvatar(name, size)
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
    /** 换头像：空串 = 恢复默认（pravatar 映射）；存 localStorage 模拟服务端档案 */
    setAvatar(dataUrl: string) {
      this.avatar = dataUrl
      if (dataUrl) localStorage.setItem(AVATAR_KEY, dataUrl)
      else localStorage.removeItem(AVATAR_KEY)
    }
  }
})
