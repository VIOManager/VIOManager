<script setup lang="ts">
/**
 * 顶栏：面包屑（取路由 meta）+ 待办 + 用户区 + 主题切换
 * 对应方案：Step 2；FS2 补改密闭环（PRD §3.1：右上角改密，改后强制重新登录）
 * S2 粗野重涂（2026-10-15 方案）：深色头带（--vio2-head）双主题恒暗，文字用固定亮色
 * 待办数：审批中心 P08 未读数（todoCount 邀请/通知 + resultCount 申请结果），路由切换时刷新
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useApprovalStore } from '@/stores/approval'
import { useThemeStore } from '@/stores/theme'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import type { Role } from '@/types'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const approval = useApprovalStore()
const theme = useThemeStore()

const crumb = computed(() => ({
  group: (route.meta.group as string) ?? '',
  title: (route.meta.title as string) ?? ''
}))

const ROLE_LABEL: Record<Role, string> = {
  SUPER_ADMIN: '超级管理员',
  DEPT_ADMIN: '部门管理员',
  STUDENT: '学生用户'
}

// ── 待办红点：审批中心 P08 未读数（审批单变化集中在路由切换后，够用即可）──
async function loadPending() {
  if (!user.token) return
  await approval.refresh()
}
onMounted(loadPending)
watch(() => route.path, loadPending)

// ── 改密弹窗：顶栏手动入口 + 默认密码登录自动引导 ──
const pwdVisible = ref(false)
// PRD §3.1：每次用默认密码登录都弹安全提示；immediate 覆盖"刷新后恢复身份已是引导态"的时序
watch(
  () => user.defaultPassword,
  (v) => {
    if (v === 1) pwdVisible.value = true
  },
  { immediate: true }
)

function onLogout() {
  user.logout()
  router.push('/login')
}
</script>

<template>
  <header class="topbar">
    <span class="crumb mono">
      <template v-if="crumb.group">{{ crumb.group }} / </template><b>{{ crumb.title }}</b>
    </span>
    <div class="topbar-right">
      <span
        class="chip mono"
        :title="'审批中心未读：邀请/通知 ' + approval.todoCount + ' · 申请结果 ' + approval.resultCount"
        @click="router.push('/approval')"
      >待办 {{ approval.total }}</span>
      <span class="user"><i class="ava">{{ user.realName.slice(0, 1) }}</i>{{ user.realName }} · {{ ROLE_LABEL[user.role] }}</span>
      <button class="btn-plain" :title="theme.current === 'light' ? '当前：图纸白，点击切深空黑' : '当前：深空黑，点击切图纸白'" @click="theme.toggle()">{{ theme.current === 'light' ? '☾ 深色' : '☀ 浅色' }}</button>
      <button class="btn-plain" @click="pwdVisible = true">改密</button>
      <button class="btn-plain" @click="onLogout">退出</button>
    </div>
    <ChangePasswordDialog v-model="pwdVisible" />
  </header>
</template>

<style scoped>
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 32px;
  border-bottom: 3px solid var(--vio2-bd);
  background: var(--vio2-head);
  position: relative;
  z-index: 2; /* 压过壳层网格底纹 */
}
/* 头带双主题恒暗：文字/描边用固定色值，不走主题令牌 */
.crumb { font-size: 12px; color: #98A2B8; letter-spacing: 0.06em; }
.crumb b { color: #FFFFFF; font-weight: 700; }
.topbar-right { display: flex; align-items: center; gap: 12px; }
.chip {
  font-size: 11px;
  font-weight: 700;
  color: #101010;
  background: var(--vio2-pink);
  border: 2px solid #101010;
  padding: 1px 9px 2px;
  letter-spacing: 0.08em;
  cursor: pointer;
}
.chip:hover { background: var(--vio2-yellow); }
.user { display: flex; align-items: center; gap: 9px; font-size: 12.5px; color: #E9EDF5; }
.ava {
  display: inline-grid;
  place-items: center;
  width: 26px;
  height: 26px;
  background: var(--vio2-green);
  color: #101010;
  border: 2px solid #101010;
  font-size: 12px;
  font-weight: 900;
  font-style: normal;
  font-family: var(--vio2-din);
}
.btn-plain {
  padding: 4px 13px;
  background: none;
  border: 2px solid #3A4360;
  color: #98A2B8;
  font-size: 12px;
  font-weight: 700;
  border-radius: 0;
  cursor: pointer;
  transition: border-color 0.2s var(--vio-ease), color 0.2s var(--vio-ease), background 0.2s var(--vio-ease);
}
.btn-plain:hover { border-color: #E9EDF5; color: #FFFFFF; }
</style>
