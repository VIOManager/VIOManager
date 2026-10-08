<script setup lang="ts">
/**
 * 通用占位页：大框阶段承载所有未开发模块路由
 * 对应方案：Step 2；各业务线按接口清单陆续替换本页（A/B/C 线及后 9 模块）
 * TODO(各业务线)：替换为真实模块页面；路由表已就位（src/router/index.ts 由菜单配置派生）
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => (route.meta.title as string) ?? '模块')
const group = computed(() => (route.meta.group as string) ?? '')
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>{{ title }}</h3>
      <div class="dossier-meta mono"><span>{{ group }}</span></div>
    </div>
    <div class="empty-state">
      <div class="es-compass" aria-hidden="true"></div>
      <b>{{ title }} · 待开发</b>
      <p>该模块页面由对应线路按接口清单开发；本骨架提供布局、菜单、路由与请求底座。</p>
      <p class="annot">※ 接口先行：先在 Apifox / 接口清单确认定义，再写实现（协作铁律 2）。</p>
    </div>
  </div>
</template>

<style scoped>
.dossier-head { display: flex; justify-content: space-between; align-items: flex-end; padding-bottom: 14px; border-bottom: 2px solid var(--vio-ink); }
.dossier-head h3 { font-family: var(--vio-serif); font-size: 26px; letter-spacing: 0.1em; font-weight: 700; }
.dossier-meta { font-size: 11.5px; color: var(--vio-ink-3); }
.empty-state { margin-top: 40px; border: 1.5px dashed var(--vio-rule); background: var(--vio-sheet); padding: 48px 30px; text-align: center; }
.es-compass { width: 84px; height: 84px; margin: 0 auto 18px; border: 1px dashed var(--vio-ink-3); border-radius: 50%; position: relative; }
.es-compass::before { content: "+"; position: absolute; inset: 0; display: grid; place-items: center; font-family: var(--vio-mono); color: var(--vio-ink-3); font-size: 18px; }
.empty-state b { display: block; font-family: var(--vio-serif); font-size: 18px; letter-spacing: 0.1em; margin-bottom: 10px; }
.empty-state p { font-size: 13px; color: var(--vio-ink-2); }
.annot { margin-top: 10px; font-family: var(--vio-fangsong); font-size: 12px; color: var(--vio-ink-3); }
</style>
