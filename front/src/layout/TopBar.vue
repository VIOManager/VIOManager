<script setup lang="ts">
/**
 * 顶栏：面包屑（取路由 meta）+ 待办 + 用户区
 * 对应方案：Step 2；改密/退出入口在右上角（PRD §3.1）
 * ⚠️ 角色切换器为开发调试件：用于大框阶段验证三端菜单显隐（Step 2 验证项），
 *    联调后必须移除，正式角色来自 A01 登录响应
 * TODO(Step3)：退出登录接真实登出并清 token；改密弹窗（A02）
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import type { Role } from '@/types'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

const crumb = computed(() => ({
  group: (route.meta.group as string) ?? '',
  title: (route.meta.title as string) ?? ''
}))

const ROLE_LABEL: Record<Role, string> = {
  SUPER_ADMIN: '超级管理员',
  DEPT_ADMIN: '部门管理员',
  STUDENT: '学生用户'
}

function onLogout() {
  user.logout()
  router.push('/login')
}
</script>

<template>
  <header class="topbar">
    <span class="crumb">
      <template v-if="crumb.group">{{ crumb.group }} / </template><b>{{ crumb.title }}</b>
    </span>
    <div class="topbar-right">
      <span class="chip mono" title="待办（Step 6 审批中心落地后接未读数）">待办 2</span>
      <!-- ⚠️ 开发调试件：三端菜单显隐验证用，联调后移除 -->
      <select
        class="role-switch mono"
        :value="user.role"
        @change="user.setRole(($event.target as HTMLSelectElement).value as Role)"
        title="调试用：切换角色验证三端菜单"
      >
        <option value="SUPER_ADMIN">超管</option>
        <option value="DEPT_ADMIN">部门管理员</option>
        <option value="STUDENT">学生</option>
      </select>
      <span class="user"><i class="ava">{{ user.realName.slice(0, 1) }}</i>{{ user.realName }} · {{ ROLE_LABEL[user.role] }}</span>
      <button class="btn-plain" @click="onLogout">退出</button>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 32px;
  border-bottom: 1px solid var(--vio-rule);
  background: var(--vio-sheet);
}
.crumb { font-size: 12.5px; color: var(--vio-ink-3); }
.crumb b { color: var(--vio-ink); font-weight: 600; }
.topbar-right { display: flex; align-items: center; gap: 14px; }
.chip {
  font-size: 11px;
  color: var(--vio-seal);
  border: 1px solid var(--vio-seal);
  padding: 2px 9px;
  letter-spacing: 0.08em;
  cursor: pointer;
}
.role-switch {
  border: 1px solid var(--vio-rule);
  background: var(--vio-paper);
  font-size: 11px;
  color: var(--vio-ink-3);
  padding: 4px 6px;
}
.user { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--vio-ink-2); }
.ava {
  display: inline-grid;
  place-items: center;
  width: 27px;
  height: 27px;
  border-radius: 50%;
  background: var(--vio-wash);
  color: var(--vio-blue-ink);
  font-size: 12px;
  font-weight: 700;
  font-style: normal;
  font-family: var(--vio-serif);
}
.btn-plain {
  padding: 5px 14px;
  background: none;
  border: 1px solid var(--vio-rule);
  color: var(--vio-ink-2);
  font-size: 12.5px;
  border-radius: 0;
  cursor: pointer;
  transition: all 0.25s var(--vio-ease);
}
.btn-plain:hover { border-color: var(--vio-ink); color: var(--vio-ink); }
</style>
