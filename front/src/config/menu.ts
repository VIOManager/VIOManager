/**
 * 三端菜单配置表（PRD §4 菜单可见性的唯一数据源）
 * 对应方案：《前端骨架-开发方案》Step 2
 * ⚠️ 安全：本配置只决定"菜单显隐"这一展示层行为，接口与数据权限全部由后端判定，
 *    禁止把本配置当作任何安全依据（规范 §5.3）
 * ⚠️ 边界（2026-10-08 组长拍板）：团队论坛拆分为独立网站（微服务论坛服务配套前端），
 *    主站不再含论坛入口；技术交流与知识库仍属管理系统
 * TODO(后续步骤)：badge 改由 P08 未读数接口驱动，当前为静态演示值
 */
import type { MenuEntry, Role } from '@/types'

/** 管理端分组（超管/部门管理员共用目录结构） */
const G = {
  OVERVIEW: '壹 · 总览',
  ORG: '贰 · 人员与组织',
  BIZ: '叁 · 赛事业务',
  COOP: '肆 · 协同',
  COMMUNITY: '伍 · 社区',
  SYSTEM: '陆 · 系统'
} as const

const ADMIN: Role[] = ['SUPER_ADMIN', 'DEPT_ADMIN']
const SUPER: Role[] = ['SUPER_ADMIN']
const ALL: Role[] = ['SUPER_ADMIN', 'DEPT_ADMIN', 'STUDENT']

/** 管理端菜单（部门管理员隐藏：学期管理 / 系统配置 / 权限调整 —— PRD §4） */
export const ADMIN_MENU: MenuEntry[] = [
  { path: '/overview', title: '概览统计', group: G.OVERVIEW, roles: ADMIN },
  { path: '/personnel', title: '人员档案', group: G.ORG, roles: ADMIN },
  { path: '/org', title: '组织管理', group: G.ORG, roles: ADMIN },
  { path: '/semester', title: '学期管理', group: G.ORG, roles: SUPER },
  { path: '/competition', title: '比赛管理', group: G.BIZ, roles: ADMIN },
  { path: '/team', title: '赛队管理', group: G.BIZ, roles: ADMIN },
  { path: '/material', title: '材料管理', group: G.BIZ, roles: ADMIN },
  { path: '/achievement', title: '成果管理', group: G.BIZ, roles: ADMIN },
  { path: '/approval', title: '审批中心', group: G.COOP, roles: ALL, badge: 2 },
  { path: '/project', title: '项目管理', group: G.COOP, roles: ADMIN },
  { path: '/schedule', title: '排班管理', group: G.COOP, roles: ADMIN },
  { path: '/performance', title: '绩效统计', group: G.COOP, roles: ADMIN },
  { path: '/tech', title: '技术交流', group: G.COMMUNITY, roles: ALL },
  { path: '/kb', title: '知识库', group: G.COMMUNITY, roles: ALL },
  { path: '/audit', title: '审计日志', group: G.SYSTEM, roles: ADMIN },
  { path: '/system', title: '系统配置', group: G.SYSTEM, roles: SUPER }
]

/** 学生端菜单（PRD §4：显示"我的排班"，隐藏全部管理模块） */
export const STUDENT_MENU: MenuEntry[] = [
  { path: '/my/overview', title: '我的概览', group: '我的', roles: ['STUDENT'] },
  { path: '/my/team', title: '我的赛队', group: '我的', roles: ['STUDENT'] },
  { path: '/my/material', title: '我的材料', group: '我的', roles: ['STUDENT'] },
  { path: '/my/achievement', title: '我的成果', group: '我的', roles: ['STUDENT'] },
  { path: '/my/schedule', title: '我的排班', group: '我的', roles: ['STUDENT'] },
  { path: '/my/performance', title: '我的绩效', group: '我的', roles: ['STUDENT'] },
  { path: '/approval', title: '审批中心', group: '协同', roles: ALL, badge: 2 },
  { path: '/tech', title: '技术交流', group: '社区', roles: ALL },
  { path: '/kb', title: '知识库', group: '社区', roles: ALL }
]

/** 按角色取菜单：过滤 + 保持分组顺序 */
export function menuOf(role: Role): MenuEntry[] {
  const source = role === 'STUDENT' ? STUDENT_MENU : ADMIN_MENU
  return source.filter((m) => m.roles.includes(role))
}
