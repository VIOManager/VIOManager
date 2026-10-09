<script setup lang="ts">
/**
 * 改密弹窗（PRD §3.1：所有角色右上角可改密，改后强制重新登录）
 * 对应方案：《前端ABC模块页面开发-优化方案》FS2
 * 两类触发：默认密码登录引导（TopBar 监听 defaultPassword 自动弹）/ 顶栏"改密"入口（手动）
 * ⚠️ 安全：新密码只在表单内存流转，禁止落 localStorage / console；错误提示由 http 拦截器统一弹出
 */
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const visible = defineModel<boolean>({ required: true })
const router = useRouter()
const user = useUserStore()

/** 引导态（默认密码未改）：标题与文案区分；非引导态为顶栏手动改密 */
const isGuide = computed(() => user.defaultPassword === 1)

const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const submitting = ref(false)

/** 前置校验：返回错误文案，通过返回 null */
function validate(): string | null {
  if (!form.oldPassword || !form.newPassword || !form.confirmPassword) return '请填写完整'
  if (form.newPassword.length < 6) return '新密码至少 6 位'
  if (form.newPassword !== form.confirmPassword) return '两次输入的新密码不一致'
  if (form.newPassword === form.oldPassword) return '新密码不能与原密码相同'
  return null
}

async function onSubmit() {
  const err = validate()
  if (err) {
    ElMessage.warning(err)
    return
  }
  submitting.value = true
  try {
    await user.changePassword(form.oldPassword, form.newPassword)
    visible.value = false
    ElMessage.success('密码已修改，请用新密码重新登录')
    router.push('/login')
  } catch {
    // 业务码提示（2002 原密码不正确等）已由 http 拦截器弹出，这里只兜住避免未处理拒绝
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="isGuide ? '安全提示：请修改默认密码' : '修改密码'" width="430px">
    <p v-if="isGuide" class="guide-tip">当前账号仍使用默认密码，为保障账号安全请立即修改（本次可暂不修改，下次登录将再次提醒）。</p>
    <el-form label-width="86px" @submit.prevent>
      <el-form-item label="原密码">
        <el-input v-model="form.oldPassword" type="password" show-password autocomplete="current-password" />
      </el-form-item>
      <el-form-item label="新密码">
        <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" placeholder="至少 6 位" />
      </el-form-item>
      <el-form-item label="确认新密码">
        <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" @keyup.enter="onSubmit" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">暂不修改</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">确认修改</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.guide-tip {
  margin: 0 0 14px;
  padding: 8px 12px;
  font-size: 12.5px;
  color: var(--vio-seal);
  background: rgba(192, 57, 43, 0.06);
  border-left: 3px solid var(--vio-seal);
}
</style>
