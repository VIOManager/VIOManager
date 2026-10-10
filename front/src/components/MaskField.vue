<script setup lang="ts">
/**
 * 敏感字段脱敏展示组件（身份证 / 手机号）
 * 对应方案：《前端ABC模块页面开发-优化方案》FS3
 * ⚠️ 安全：本组件只是展示层裁剪，不构成任何权限依据——后端已按角色裁剪返回值，
 *    列表只给 idCardLast4，完整身份证/证件照须走 M10/M11 审计接口（规范 §5.3）
 * ⚠️ allowRoles 仅用于"无权限角色显示占位"的展示优化，同样不是权限判定
 */
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import type { Role } from '@/types'

const props = withDefaults(
  defineProps<{
    text: string
    /** 脱敏类型：idcard 身份证 / phone 手机号 / plain 原样 */
    kind?: 'idcard' | 'phone' | 'plain'
    /** 允许看明文的角色；不在列表内显示"无权查看"占位（缺省不限制） */
    allowRoles?: Role[]
  }>(),
  { kind: 'plain' }
)

const user = useUserStore()

const allowed = computed(() => !props.allowRoles || props.allowRoles.includes(user.role))

const display = computed(() => {
  if (!props.text) return '—'
  if (props.kind === 'idcard') {
    const last4 = props.text.slice(-4)
    // 列表场景后端只给后四位，统一按 18 位宽度补星，视觉上一眼认出是身份证
    const stars = '*'.repeat(Math.max(props.text.length - 4, 14))
    return stars + last4
  }
  if (props.kind === 'phone') {
    if (props.text.length === 11) return props.text.slice(0, 3) + '****' + props.text.slice(7)
    return '*'.repeat(Math.max(props.text.length - 4, 0)) + props.text.slice(-4)
  }
  return props.text
})
</script>

<template>
  <span v-if="allowed" class="mono">{{ display }}</span>
  <span v-else class="mask-denied" title="当前角色无权查看（展示层裁剪，权限以后端为准）">无权查看</span>
</template>

<style scoped>
.mask-denied {
  color: var(--vio-ink-3);
  font-family: var(--vio-fangsong);
  font-size: 12px;
}
</style>
