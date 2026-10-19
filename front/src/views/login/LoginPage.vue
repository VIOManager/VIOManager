<script setup lang="ts">
/**
 * 登录页（S2 粗野重涂版 · 2026-10-15 方案 D）
 * 对应方案：《前端骨架-开发方案》Step 3 + 《前端ABC模块页面开发-优化方案》FS2 + 主站视觉重设计方案 S2
 * FS2：登录闭环（/api/auth/login）；defaultPassword=1 时改密引导由壳内 TopBar 自动弹出
 * 账号提示按 mock/真实模式切换（VITE_USE_MOCK），两套种子账号不同
 * 登录页在壳外，自备主题切换钮（stores/theme）
 */
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'

const router = useRouter()
const user = useUserStore()
const theme = useThemeStore()
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
    <div class="marquee" aria-hidden="true">
      <div class="mq-track">
        <span>VIOMANAGER ✦ 科创协作管理平台 ✦ SEASON 2026F ✦ 比赛管理 ✦ 赛队协作 ✦ 材料归档 ✦ 成果沉淀 ✦ 审批中心 ✦ </span>
        <span>VIOMANAGER ✦ 科创协作管理平台 ✦ SEASON 2026F ✦ 比赛管理 ✦ 赛队协作 ✦ 材料归档 ✦ 成果沉淀 ✦ 审批中心 ✦ </span>
      </div>
    </div>
    <button
      class="theme-btn mono"
      :title="theme.current === 'light' ? '当前：图纸白，点击切深空黑' : '当前：深空黑，点击切图纸白'"
      @click="theme.toggle()"
    >{{ theme.current === 'light' ? '☾ 深色' : '☀ 浅色' }}</button>
    <div class="stage">
      <div class="left">
        <span class="sticker mono">2026 秋季学期 · 第六教学周</span>
        <h1>把<span class="blk">科创协作</span><br />钉在<span class="blk p">一块黑板</span>上。</h1>
        <p class="desc">赛事报名、组队材料、成果归档、排班与知识沉淀——以学期为周期，为 <b>186 名成员</b> 与 <b>47 支赛队</b> 提供三端协同管理。</p>
        <div class="stats">
          <div class="stat"><b class="mono">186</b><span class="mono">在册成员</span></div>
          <div class="stat y"><b class="mono">12</b><span class="mono">进行中比赛</span></div>
          <div class="stat l"><b class="mono">47</b><span class="mono">活跃赛队</span></div>
          <div class="stat g"><b class="mono">09</b><span class="mono">待办审批</span></div>
        </div>
        <div class="stripes" aria-hidden="true"></div>
      </div>
      <form class="panel" @submit.prevent="onSubmit">
        <span class="tag mono">LOGIN · 登录口</span>
        <span class="flag mono">ACCESS / 登记入口</span>
        <h2>欢迎回来</h2>
        <label class="field">
          <span class="mono">登录名 / 学工号</span>
          <input v-model="form.loginName" type="text" placeholder="如 2023319024" autocomplete="username" />
        </label>
        <label class="field">
          <span class="mono">密码 / PASSWORD</span>
          <span class="pw-row">
            <input v-model="form.password" :type="showPwd ? 'text' : 'password'" placeholder="请输入密码" autocomplete="current-password" />
            <button type="button" class="pw-toggle mono" @click="showPwd = !showPwd">{{ showPwd ? '隐藏' : '明文' }}</button>
          </span>
        </label>
        <button type="submit" class="btn-go" :disabled="loading">{{ loading ? '登记中…' : '进入平台 →' }}</button>
        <p class="note mono">
          <template v-if="useMock">mock 演示账号：admin（超管）/ dept01（部门管理员）/ 2023319024（学生·在队）/ 2023319005（学生·无队），密码均为 abc123</template>
          <template v-else>联调种子账号：admin（超管）/ dept_chen（部门管理员）/ stu_wang（学生），初始密码为角色名加 @123456（如 Admin@123456）；忘记密码请联系超级管理员重置</template>
        </p>
      </form>
    </div>
    <footer class="foot mono">黑龙江科技大学 · 科创团队 — VOL.2026F</footer>
  </div>
</template>

<style scoped>
.login {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background: var(--vio2-bg);
  display: flex;
  flex-direction: column;
}
/* 顶部跑马灯：双主题恒为深底黄字（仅登录页保留，方案拍板） */
.marquee {
  background: var(--vio2-head);
  border-bottom: 3px solid var(--vio2-head);
  overflow: hidden;
  white-space: nowrap;
}
.mq-track { display: inline-flex; animation: mq 26s linear infinite; }
.mq-track span {
  font-family: var(--vio2-din);
  font-weight: 700;
  font-size: 11px;
  letter-spacing: 0.24em;
  color: var(--vio2-yellow);
  padding: 9px 0;
}
@keyframes mq {
  from { transform: translateX(0); }
  to { transform: translateX(-50%); }
}
.theme-btn {
  position: absolute;
  top: 40px;
  right: 34px;
  z-index: 3;
  background: var(--vio2-panel);
  border: 2px solid var(--vio2-bd);
  box-shadow: 3px 3px 0 var(--vio2-shadow);
  color: var(--vio2-ink-2);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  padding: 5px 12px;
  cursor: pointer;
}
.theme-btn:hover { color: var(--vio2-ink); transform: translate(-1px, -1px); }
.stage {
  flex: 1;
  max-width: 1140px;
  width: 100%;
  margin: 0 auto;
  padding: 56px 48px 32px;
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  gap: 64px;
  align-items: center;
}
/* 学期贴纸：粉底黑字，微旋转（双主题恒色） */
.sticker {
  display: inline-block;
  background: var(--vio2-pink);
  color: #101010;
  border: 2px solid var(--vio2-bd);
  box-shadow: 4px 4px 0 var(--vio2-shadow);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  padding: 3px 12px 4px;
  margin-bottom: 22px;
  transform: rotate(-1.5deg);
}
.left h1 {
  font-family: var(--vio2-din);
  font-weight: 900;
  font-size: 56px;
  line-height: 1.3;
  letter-spacing: 0.02em;
  color: var(--vio2-ink);
}
/* 标题彩色块：黄底主词 / 丁香紫底次词，贴纸感（双主题恒色，字恒黑） */
.left h1 .blk {
  display: inline-block;
  background: var(--vio2-yellow);
  color: #101010;
  border: 3px solid var(--vio2-bd);
  box-shadow: 5px 5px 0 var(--vio2-shadow);
  padding: 0 14px;
  transform: rotate(-1deg);
}
.left h1 .blk.p { background: var(--vio2-lilac); }
.desc { margin-top: 20px; max-width: 400px; font-size: 14px; color: var(--vio2-ink-2); line-height: 2; }
.desc b { font-weight: 700; color: var(--vio2-ink); }
.stats { margin-top: 34px; display: flex; gap: 14px; flex-wrap: wrap; }
.stat {
  background: var(--vio2-panel);
  border: 2px solid var(--vio2-bd);
  box-shadow: 4px 4px 0 var(--vio2-shadow);
  padding: 10px 16px 9px;
  min-width: 92px;
}
.stat b { display: block; font-size: 24px; font-weight: 900; color: var(--vio2-ink); }
.stat span { font-size: 10px; letter-spacing: 0.14em; color: var(--vio2-ink-3); }
/* 数据块四色：白 / 黄 / 丁香紫 / 绿，彩底恒黑字（双主题恒色） */
.stat.y { background: var(--vio2-yellow); }
.stat.l { background: var(--vio2-lilac); }
.stat.g { background: var(--vio2-green); }
.stat.y b, .stat.y span, .stat.l b, .stat.l span, .stat.g b, .stat.g span { color: #101010; }
.stripes {
  margin-top: 34px;
  width: 240px;
  height: 14px;
  background: repeating-linear-gradient(-45deg, var(--vio2-yellow) 0 10px, transparent 10px 20px);
  border: 2px solid var(--vio2-bd);
}
/* 登录表单：粗野面板 */
.panel {
  position: relative;
  background: var(--vio2-panel);
  border: 3px solid var(--vio2-bd);
  box-shadow: 8px 8px 0 var(--vio2-shadow);
  padding: 34px 32px 24px;
}
.tag {
  position: absolute;
  top: -14px;
  left: 22px;
  background: var(--vio2-lilac);
  color: #101010;
  border: 2px solid var(--vio2-bd);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  padding: 2px 10px 3px;
  transform: rotate(-2deg);
}
/* 表单卡右上小旗：黑底黄字（双主题恒色） */
.flag {
  position: absolute;
  top: -14px;
  right: 22px;
  background: var(--vio2-head);
  color: var(--vio2-yellow);
  border: 2px solid var(--vio2-bd);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.16em;
  padding: 3px 10px 4px;
  transform: rotate(2deg);
}
.panel h2 {
  font-family: var(--vio2-din);
  font-weight: 900;
  font-size: 26px;
  color: var(--vio2-ink);
  margin-bottom: 24px;
}
.field { display: block; margin-bottom: 20px; }
.field > span {
  display: block;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.18em;
  color: var(--vio2-ink-3);
  margin-bottom: 6px;
}
.field input {
  width: 100%;
  border: 2px solid var(--vio2-bd);
  background: var(--vio2-panel-2);
  padding: 10px 12px;
  font-size: 15px;
  color: var(--vio2-ink);
  border-radius: 0;
  transition: box-shadow 0.2s var(--vio-ease);
}
.field input::placeholder { color: var(--vio2-ink-3); }
.field input:focus { outline: none; box-shadow: 3px 3px 0 var(--vio2-yellow); }
.pw-row { display: flex; gap: 8px; }
.pw-row input { flex: 1; }
.pw-toggle {
  flex-shrink: 0;
  background: var(--vio2-panel);
  border: 2px solid var(--vio2-bd);
  color: var(--vio2-ink-2);
  font-size: 11px;
  font-weight: 700;
  padding: 0 12px;
  cursor: pointer;
  border-radius: 0;
}
.pw-toggle:hover { color: var(--vio2-ink); background: var(--vio2-yellow); }
.btn-go {
  width: 100%;
  margin-top: 4px;
  padding: 13px;
  background: var(--vio2-yellow);
  color: #101010;
  border: 3px solid var(--vio2-bd);
  box-shadow: 5px 5px 0 var(--vio2-shadow);
  font-family: var(--vio2-din);
  font-size: 16px;
  font-weight: 900;
  letter-spacing: 0.14em;
  border-radius: 0;
  cursor: pointer;
  transition: transform 0.15s var(--vio-ease), box-shadow 0.15s var(--vio-ease);
}
.btn-go:hover:not(:disabled) { transform: translate(-2px, -2px); box-shadow: 7px 7px 0 var(--vio2-shadow); }
.btn-go:active:not(:disabled) { transform: translate(2px, 2px); box-shadow: 2px 2px 0 var(--vio2-shadow); }
.btn-go:disabled { opacity: 0.55; cursor: not-allowed; }
.note { margin-top: 16px; font-size: 11px; color: var(--vio2-ink-3); line-height: 1.9; }
.foot {
  padding: 14px 34px;
  border-top: 3px solid var(--vio2-bd);
  font-size: 10px;
  letter-spacing: 0.2em;
  color: var(--vio2-ink-3);
  display: flex;
  justify-content: space-between;
}
</style>
