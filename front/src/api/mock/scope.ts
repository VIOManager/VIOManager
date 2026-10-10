/**
 * 数据范围助手（模拟后端 DataScope 拦截器，规范 §5.3：真实权限由后端判定，本文件仅 mock 演示）
 * 规则：超级管理员 = 全量；部门管理员 = 本部门组织子树（人员/组织树/赛队/材料概览/成果均按此裁剪）
 * ⚠️ 赛队归属判定暂定：队长所在组织在本部门子树内 → 该队伍对本部门管理员可见
 * TODO(联调)：删除本文件，由后端 DataScope 统一实现
 */
import { findMockUserByToken } from './auth'
import { members } from './member-data'
import { findOrgNodeById, tree } from './org-data'
import type { OrgNode } from '@/api/org'

/** 部门管理员的组织范围根节点（其成员账号所在组织）；超管/学生返回 null */
export function deptScopeRoot(token: string): OrgNode | null {
  const u = findMockUserByToken(token)
  if (!u || u.role !== 'DEPT_ADMIN') return null
  const me = members.find((m) => m.loginName === u.loginName)
  if (!me) return null
  return findOrgNodeById(me.orgId)
}

/** 部门管理员的组织 id 集（含子孙）；null=不裁剪（超管/学生） */
export function deptScopeOrgIds(token: string): string[] | null {
  const root = deptScopeRoot(token)
  if (!root) return null
  const ids: string[] = []
  const walk = (n: OrgNode) => {
    ids.push(n.id)
    for (const c of n.children) walk(c)
  }
  walk(root)
  return ids
}

/** 组织是否在范围内（scope=null 全量放行） */
export function orgInScope(orgId: string, scope: string[] | null): boolean {
  return scope === null || scope.indexOf(orgId) >= 0
}

/** 成员 id → 所在组织 id（赛队归属判定用） */
export function orgIdOfMember(memberId: string): string {
  const m = members.find((x) => x.id === memberId)
  return m ? m.orgId : ''
}

/**
 * 队伍对本登录人是否可见：超管全量；部门管理员按队长组织归属裁剪
 * （leaderId 为赛队队长成员 id）
 */
export function teamVisible(leaderId: string, token: string): boolean {
  const scope = deptScopeOrgIds(token)
  if (scope === null) return true
  return orgInScope(orgIdOfMember(leaderId), scope)
}

/** 全量组织树（org-data 引用透出，避免 scope 使用方直接依赖 org mock） */
export { tree as orgTree }
