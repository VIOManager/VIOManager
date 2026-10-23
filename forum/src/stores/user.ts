/**
 * 论坛站用户状态
 * 对应：架构方案 §4——JWT 两服务共享，论坛只验签不查库
 * ⚠️ 安全：token 存 sessionStorage（每标签页独立身份，与主站同策略）
 * 身份一律从 JWT 载荷解析（载荷含 userId role orgId realName）；未登录为空身份，
 * 由路由守卫引导回主站登录页
 */
import { defineStore } from 'pinia'
import { avatarOf as genAvatar } from '@/config/avatars'
import { useThemeStore } from '@/stores/theme'

const TOKEN_KEY = 'vio_token'
// 自定义头像（演示期存 localStorage 模拟服务端档案；联调后改走文件服务 member.photo_file_id）
const AVATAR_KEY = 'forum_avatar'

interface ForumUserState {
  token: string
  realName: string
  initials: string
  role: string
  /** 自定义头像 dataURL；空串 = 未设，走默认首字块（config/avatars） */
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
    // 未登录/载荷解析失败 → 空身份 + 最低权限档：守卫会拦去 /gate，展示层永不吃到假身份
    const name = claims.realName ?? ''
    const role = claims.role ?? 'STUDENT'
    return { token, realName: name, initials: name.slice(0, 1), role, avatar: localStorage.getItem(AVATAR_KEY) ?? '' }
  },
  getters: {
    loggedIn: (s) => s.token.length > 0,
    /** 管理档（F11-F14 管理操作、含隐藏开关）：超管+部门管理员两档同权（2026-10-17 P1 拍板，对齐后端 @RequireRole） */
    isAdmin: (s) => s.role === 'SUPER_ADMIN' || s.role === 'DEPT_ADMIN',
    /** 置顶专属：后端发帖时仅超管置顶生效（两档之外唯一的口径差异点，勾选框显隐用） */
    isSuperAdmin: (s) => s.role === 'SUPER_ADMIN',
    /**
     * 按名字取头像（全站渲染统一走这里，勿直连 config/avatars）：
     * 是本人且已设自定义头像 → 自定义；其余成员 → 主站同款首字块。
     * 闭包内读 theme store 的响应式值：切主题时依赖变化触发全站重渲染，
     * 首字块按新主题档的绿令牌重画（config/avatars 缓存键含主题，不重复画）
     */
    avatarByName: (s) => (name: string, size: number) =>
      s.avatar && name === s.realName
        ? s.avatar
        : genAvatar(name, size, useThemeStore().current)
  },
  actions: {
    /**
     * dev token 交接（方案 §7.1）：主站入口以 ?token= 带来真 JWT，落库并解析载荷。
     * 载荷解析失败（非 JWT 串）时保留现有展示身份
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
    },
    /** 换头像：空串 = 恢复默认首字块；存 localStorage 模拟服务端档案 */
    setAvatar(dataUrl: string) {
      this.avatar = dataUrl
      if (dataUrl) localStorage.setItem(AVATAR_KEY, dataUrl)
      else localStorage.removeItem(AVATAR_KEY)
    }
  }
})
