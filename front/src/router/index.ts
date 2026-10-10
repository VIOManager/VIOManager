/**
 * 路由与全局守卫
 * 对应方案：《前端骨架-开发方案》Step 2（路由懒加载 + 无 token 一律跳登录）
 * ⚠️ 安全：守卫只做"登录态跳转"，不构成任何权限放行依据（规范 §5.3）
 * TODO(Step3)：登录态过期由 axios 拦截 1002 统一踢回（Step 1 拦截器落地后移除本注释）
 */
import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { ADMIN_MENU, STUDENT_MENU } from '@/config/menu'
import { useUserStore } from '@/stores/user'

const AdminLayout = () => import('@/layout/AdminLayout.vue')
const LoginPage = () => import('@/views/login/LoginPage.vue')
const PlaceholderPage = () => import('@/views/common/PlaceholderPage.vue')

/** 已落地的真实页面：菜单路径 → 懒加载组件（未列出的路径仍挂占位页，随开发逐步替换） */
const REAL_PAGES: Record<string, () => Promise<unknown>> = {
  '/overview': () => import('@/views/overview/OverviewPage.vue'),
  '/org': () => import('@/views/org/OrgPage.vue')
}

/** 由菜单配置派生子路由：真实页用真实组件，其余先挂占位页 */
function menuChildren(): RouteRecordRaw[] {
  const all = [...ADMIN_MENU, ...STUDENT_MENU]
  const seen = new Set<string>()
  const children: RouteRecordRaw[] = []
  for (const m of all) {
    if (seen.has(m.path)) continue
    seen.add(m.path)
    children.push({
      path: m.path.replace(/^\//, ''),
      component: REAL_PAGES[m.path] ?? PlaceholderPage,
      meta: { title: m.title, group: m.group }
    })
  }
  return children
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginPage, meta: { title: '登录' } },
    {
      path: '/',
      component: AdminLayout,
      redirect: '/overview',
      children: menuChildren()
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

router.beforeEach((to) => {
  const user = useUserStore()
  if (to.path !== '/login' && !user.token) return '/login'
  if (to.path === '/login' && user.token) return '/'
  return true
})

export default router
