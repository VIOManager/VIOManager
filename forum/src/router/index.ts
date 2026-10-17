/**
 * 论坛站路由与守卫
 * ⚠️ 安全：守卫只做"未登录引导"，不构成权限放行依据（规范 §5.3）
 * 与主站差异：论坛无登录页，未登录一律进 /gate 引导回主站登录（JWT 共享）
 */
import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const ForumLayout = () => import('@/layout/ForumLayout.vue')
const HomePage = () => import('@/views/home/HomePage.vue')
const TopicPage = () => import('@/views/topic/TopicPage.vue')
const GatePage = () => import('@/views/gate/GatePage.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/gate', component: GatePage, meta: { title: '请先登录' } },
    {
      path: '/',
      component: ForumLayout,
      children: [
        { path: '', component: HomePage, meta: { title: '首页' } },
        { path: 'topic/:id', component: TopicPage, meta: { title: '帖子详情' } }
        // 技术交流 2026-10-15 并入论坛（后端 V2 退役 tech 域，前端 feed 页同批移除）
        // 知识库 2026-10-13 迁回主站（/kb 由主站承载）
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

router.beforeEach((to) => {
  const user = useUserStore()
  // dev token 交接：主站跳转带 ?token=<jwt>，落库后重定向去掉 query（防地址栏/历史记录残留）
  if (import.meta.env.DEV && typeof to.query.token === 'string' && to.query.token.length > 0) {
    user.acceptToken(to.query.token)
    const query = { ...to.query }
    delete query.token
    return { path: to.path, query, replace: true }
  }
  if (to.path !== '/gate' && !user.loggedIn) return '/gate'
  return true
})

export default router
