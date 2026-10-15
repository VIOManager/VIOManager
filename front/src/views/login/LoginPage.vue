<script setup lang="ts">
/**
 * 登录页（方向一「图纸档案」定调版）
 * 对应方案：《前端骨架-开发方案》Step 3 + 《前端ABC模块页面开发-优化方案》FS2
 * FS2：登录闭环（/api/auth/login）；defaultPassword=1 时改密引导由壳内 TopBar 自动弹出
 * 账号提示按 mock/真实模式切换（VITE_USE_MOCK），两套种子账号不同
 */
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const user = useUserStore()
const useMock = import.meta.env.VITE_USE_MOCK === 'true'
const form = reactive({ loginName: '', password: '' })
const loading = ref(false)
/** 密码明文切换（改密弹窗走 el-input show-password 自带小眼睛，登录页原生 input 需自备） */
const showPwd = ref(false)

async function onSubmit() {
  if (!form.loginName || !form.password || loading.value) return
  loading.value = true
  try {
    await user.login(form.loginName, form.password)
    router.push('/')
  } catch {
    // 失败提示（2001 账号或密码错误）已由 http 拦截器统一弹出
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <div class="grid-bg" aria-hidden="true"></div>
    <div class="vertical-note" aria-hidden="true">二〇二六秋季学期 · 第六教学周</div>
    <div class="cover">
      <header class="cover-head">
        <span class="org">黑龙江科技大学 · 科创团队</span>
        <span class="docno mono">编号 VIO-DOC-2026-001</span>
      </header>
      <div class="cover-main">
        <div class="cover-title">
          <p class="en mono">VIOMANAGER · SEASON-DRIVEN COLLABORATION SYSTEM</p>
          <h1>科创协作<br />管理平台</h1>
          <p class="desc">以学期为周期，统一管理赛事、赛队、材料、成果、排班与团队知识。覆盖超级管理员、部门管理员、学生用户三端。</p>
          <dl class="cover-ledger">
            <div><dt>在册成员</dt><dd class="mono">186 人</dd></div>
            <div><dt>进行中比赛</dt><dd class="mono">12 项</dd></div>
            <div><dt>活跃赛队</dt><dd class="mono">47 支</dd></div>
            <div><dt>待办审批</dt><dd class="mono">9 件</dd></div>
          </dl>
        </div>
        <form class="ledger-form" @submit.prevent="onSubmit">
          <h2>登录登记</h2>
          <label class="line-field">
            <span>登录名（学工号）</span>
            <input v-model="form.loginName" type="text" placeholder="如 2023319024" autocomplete="username" />
          </label>
          <label class="line-field">
            <span>密码</span>
            <span class="pw-row">
              <input v-model="form.password" :type="showPwd ? 'text' : 'password'" placeholder="请输入密码" autocomplete="current-password" />
              <button type="button" class="pw-toggle" @click="showPwd = !showPwd">{{ showPwd ? '隐藏' : '明文' }}</button>
            </span>
          </label>
          <button type="submit" class="btn-stamp" :disabled="loading">{{ loading ? '登记中…' : '登记登录' }}</button>
          <p class="note">
            <template v-if="useMock">mock 演示账号：admin（超管）/ dept01（部门管理员）/ 2023319024（学生·在队）/ 2023319005（学生·无队），密码均为 abc123</template>
            <template v-else>联调种子账号：admin（超管）/ dept_chen（部门管理员）/ stu_wang（学生），初始密码为角色名加 @123456（如 Admin@123456）；忘记密码请联系超级管理员重置</template>
          </p>
          <div class="seal" aria-hidden="true"><i>黑龙江科技大学<br />科创团队</i></div>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background: var(--vio-paper);
}
.grid-bg {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(29, 58, 143, 0.05) 1px, transparent 1px),
    linear-gradient(180deg, rgba(29, 58, 143, 0.05) 1px, transparent 1px);
  background-size: 24px 24px;
  mask-image: linear-gradient(120deg, rgba(0, 0, 0, 0.9), rgba(0, 0, 0, 0.25) 70%);
}
.vertical-note {
  position: absolute;
  left: 34px;
  top: 50%;
  transform: translateY(-50%);
  writing-mode: vertical-rl;
  letter-spacing: 0.42em;
  font-family: var(--vio-fangsong);
  font-size: 14px;
  color: var(--vio-ink-3);
}
.cover {
  position: relative;
  max-width: 1160px;
  margin: 0 auto;
  padding: 48px 56px 26px;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.cover-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding-bottom: 14px;
  border-bottom: 2px solid var(--vio-ink);
}
.cover-head .org { font-family: var(--vio-serif); font-weight: 700; font-size: 19px; letter-spacing: 0.14em; }
.cover-head .docno { font-size: 11.5px; color: var(--vio-ink-3); letter-spacing: 0.1em; }
.cover-main { flex: 1; display: grid; grid-template-columns: 1.15fr 1fr; gap: 72px; align-items: center; padding: 40px 0; }
.cover-title .en { font-size: 11px; letter-spacing: 0.26em; color: var(--vio-blue-ink); margin-bottom: 20px; }
.cover-title h1 { font-family: var(--vio-serif); font-weight: 700; font-size: 60px; line-height: 1.18; letter-spacing: 0.08em; }
.cover-title .desc { margin-top: 20px; max-width: 400px; font-size: 14.5px; color: var(--vio-ink-2); line-height: 2; }
.cover-ledger { margin-top: 36px; display: grid; grid-template-columns: repeat(4, auto); gap: 32px; width: max-content; }
.cover-ledger div { border-left: 1px solid var(--vio-rule); padding-left: 14px; }
.cover-ledger dt { font-family: var(--vio-fangsong); font-size: 12.5px; color: var(--vio-ink-3); letter-spacing: 0.1em; }
.cover-ledger dd { font-size: 20px; color: var(--vio-blue-ink); font-weight: 700; margin-top: 2px; }
.ledger-form {
  position: relative;
  background: var(--vio-sheet);
  border: 1px solid var(--vio-ink);
  padding: 32px 34px 26px;
  box-shadow: 5px 5px 0 rgba(29, 58, 143, 0.14);
}
.ledger-form h2 {
  font-family: var(--vio-serif);
  font-size: 21px;
  letter-spacing: 0.18em;
  padding: 6px 92px 12px 0;
  border-bottom: 1px solid var(--vio-ink);
  margin-bottom: 24px;
}
.line-field { display: block; margin-bottom: 22px; }
.line-field span {
  display: block;
  font-family: var(--vio-fangsong);
  font-size: 13px;
  color: var(--vio-ink-2);
  letter-spacing: 0.12em;
  margin-bottom: 4px;
}
.line-field input {
  width: 100%;
  border: none;
  border-bottom: 1px solid var(--vio-ink-3);
  background: transparent;
  padding: 8px 2px;
  font-size: 15px;
  color: var(--vio-ink);
  border-radius: 0;
  transition: border-color 0.3s var(--vio-ease);
}
.line-field input::placeholder { color: #B9C0CB; }
.line-field input:focus { outline: none; border-bottom: 2px solid var(--vio-blue-ink); margin-bottom: -1px; }
.pw-row { display: flex; align-items: flex-end; gap: 8px; }
.pw-row input { flex: 1; }
.pw-toggle {
  flex-shrink: 0;
  background: none;
  border: 1px solid var(--vio-rule);
  color: var(--vio-ink-3);
  font-family: var(--vio-fangsong);
  font-size: 11px;
  padding: 2px 9px;
  letter-spacing: 0.08em;
  cursor: pointer;
  border-radius: 0;
  transition: all 0.2s var(--vio-ease);
}
.pw-toggle:hover { border-color: var(--vio-blue-ink); color: var(--vio-blue-ink); }
.btn-stamp {
  width: 100%;
  padding: 13px;
  background: var(--vio-blue-ink);
  color: #F6F7F2;
  border: none;
  font-size: 15px;
  letter-spacing: 0.34em;
  text-indent: 0.34em;
  font-family: var(--vio-serif);
  font-weight: 700;
  border-radius: 0;
  cursor: pointer;
  transition: background 0.3s var(--vio-ease);
}
.btn-stamp:hover { background: var(--vio-blue-deep); }
.btn-stamp:disabled { opacity: 0.55; cursor: not-allowed; }
.note { margin-top: 16px; font-family: var(--vio-fangsong); font-size: 12px; color: var(--vio-ink-3); line-height: 1.9; }
.seal {
  position: absolute;
  right: 22px;
  top: -38px;
  width: 96px;
  height: 96px;
  border: 3px solid var(--vio-seal);
  border-radius: 50%;
  display: grid;
  place-items: center;
  transform: rotate(-9deg);
  opacity: 0.82;
  pointer-events: none;
  background: rgba(249, 250, 247, 0.55);
}
.seal::before { content: ""; position: absolute; inset: 5px; border: 1px solid var(--vio-seal); border-radius: 50%; }
.seal i {
  font-style: normal;
  font-family: var(--vio-serif);
  font-weight: 700;
  font-size: 12.5px;
  color: var(--vio-seal);
  text-align: center;
  line-height: 1.7;
  letter-spacing: 0.12em;
}
</style>
