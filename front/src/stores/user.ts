/**
 * 用户状态（token / 角色 / 姓名）
 * 对应方案：《前端骨架-开发方案》Step 2（Pinia user store）
 * ⚠️ 安全：role 仅用于菜单显隐等展示层逻辑，接口数据权限一律由后端判定（规范 §5.3）
 * ⚠️ 安全：token 存 localStorage 是方案 §7 待确认项的当前实现（刷新保活），
 *    XSS 权衡已在方案中标注；token 注入请求头只允许在 axios 实例统一做（Step 1）
 * TODO(Step3)：mockLogin 替换为 A01 真实登录（Apifox Mock → 后端联调），
 *    defaultPassword=1 时弹改密引导（A02）
 */
import { defineStore } from 'pinia'
import type { Role } from '@/types'

const TOKEN_KEY = 'vio_token'

interface UserState {
  token: string
  role: Role
  realName: string
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: localStorage.getItem(TOKEN_KEY) ?? '',
    role: 'SUPER_ADMIN',
    realName: '李明'
  }),
  actions: {
    /** 大框阶段登录桩：任意输入即入库 token，进入布局壳（Step 3 接真实接口） */
    mockLogin(role: Role = 'SUPER_ADMIN') {
      this.token = 'dev-stub-token'
      this.role = role
      localStorage.setItem(TOKEN_KEY, this.token)
    },
    logout() {
      this.token = ''
      this.role = 'SUPER_ADMIN'
      localStorage.removeItem(TOKEN_KEY)
    },
    /** ⚠️ 开发调试件：顶栏角色切换器用，联调后移除（Step 3 起角色来自登录响应） */
    setRole(role: Role) {
      this.role = role
    }
  }
})
