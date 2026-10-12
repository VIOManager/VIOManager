<script setup lang="ts">
/**
 * 根组件：路由出口（全站页面均懒加载，首屏只加载当前路由 —— PRD §5 性能）
 * FS2：挂载时凭已有 token 恢复身份（刷新不丢角色/姓名）；
 *    token 已失效时 /api/auth/me 返回 1002，由 http 拦截器统一清 token 踢回登录页
 */
import { onMounted } from 'vue'
import { useUserStore } from '@/stores/user'

const user = useUserStore()
onMounted(() => {
  user.restore()
})
</script>

<template>
  <router-view />
</template>
