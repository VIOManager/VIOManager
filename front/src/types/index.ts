/**
 * 全局类型定义
 * 对应方案：《前端骨架-开发方案》Step 2（三端角色驱动菜单与路由）
 */
/** 三端角色：超管 / 部门管理员 / 学生（PRD §2.1） */
export type Role = 'SUPER_ADMIN' | 'DEPT_ADMIN' | 'STUDENT'

/** 菜单项：group 为侧边栏分组名，roles 控制三端显隐（PRD §4） */
export interface MenuEntry {
  path: string
  title: string
  group: string
  roles: Role[]
  /** 红点未读数占位（审批中心等），0 为不显示 */
  badge?: number
}
