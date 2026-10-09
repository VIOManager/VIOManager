/**
 * 认证接口：登录 / 改密 / 会话恢复
 * 对应方案：《前端ABC模块页面开发-优化方案》FS2（登录改密闭环）
 * ⚠️ 清单：组长骨架接口清单尚未录入 Apifox，本文件路径与字段为暂定稿，
 *    录入后以 Apifox 为准并同步修改（TODO 联调）
 * 错误码（2xxx 认证段暂定）：2001 账号或密码错误；2002 原密码不正确；2003 新密码与原密码相同
 */
import { get, post, put } from './http'
import type { Role } from '@/types'

export interface LoginResp {
  token: string
  realName: string
  role: Role
  /** 1=仍用默认密码 abc123（PRD §3.1：登录后弹安全提示引导改密） */
  defaultPassword: 0 | 1
}

export interface MeResp {
  realName: string
  role: Role
  defaultPassword: 0 | 1
}

export function apiLogin(data: { loginName: string; password: string }): Promise<LoginResp> {
  return post('/api/auth/login', data)
}

/** 改密成功后服务端作废该用户全部旧 token（PRD：改后强制重新登录） */
export function apiChangePassword(data: { oldPassword: string; newPassword: string }): Promise<null> {
  return put('/api/auth/password', data)
}

/** 刷新后凭 token 恢复角色与姓名（避免刷新后身份丢回默认值） */
export function apiMe(): Promise<MeResp> {
  return get('/api/auth/me')
}
