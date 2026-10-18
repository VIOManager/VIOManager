<script setup lang="ts">
/**
 * 滚动加载器：放在列表底部当哨兵，滚进视口自动 emit reach 让页面追加下一批
 * 对应方案：《列表分页-优化方案》ST1（2026-10-16 lzh 拍板滚动分页，每批 20 条）
 * 机制：IntersectionObserver 提前 120px 预载；数据变化后若哨兵仍在视口内（列表不满一屏）
 *       observer 不会重复回调，组件手动复核可见性续触发，保证首屏一定被填满
 * ⚠️ 页面侧职责：首次加载/筛选变化时清空列表回第 1 批；loading 与 noMore 由页面状态驱动
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{
  loading: boolean
  noMore: boolean
  loaded: number
  total: number
}>()

const emit = defineEmits<{ (e: 'reach'): void }>()

const root = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null

/** 条件齐了才通知页面：加载中或已到底都不再触发 */
function tryReach() {
  if (props.loading || props.noMore) return
  emit('reach')
}

/** 手动复核哨兵是否在视口附近（observer 对持续可见不重复回调） */
function recheck() {
  if (!root.value) return
  const rect = root.value.getBoundingClientRect()
  if (rect.top < window.innerHeight + 120) tryReach()
}

onMounted(() => {
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting) tryReach()
    },
    { rootMargin: '120px' }
  )
  if (root.value) observer.observe(root.value)
})
onBeforeUnmount(() => observer?.disconnect())

// loading 翻转（服务端模式）或 loaded 变化（本地切片模式）后补一次复核
watch(
  () => [props.loading, props.loaded] as const,
  () => requestAnimationFrame(recheck)
)
</script>

<template>
  <!-- 空列表（total=0）整条隐藏，空态提示由各页面自己画 -->
  <div ref="root" v-show="total > 0" class="scroll-loader mono" aria-live="polite">
    <template v-if="loading">
      <span class="stripe-bar" aria-hidden="true"></span>
      <span class="txt">加载中</span>
    </template>
    <template v-else-if="noMore">
      <span class="done-sticker">已全部加载</span>
      <span class="txt">共 {{ total }} 条</span>
    </template>
    <span v-else class="txt">已加载 {{ loaded }} / {{ total }} 条 · 下拉加载更多</span>
  </div>
</template>

<style scoped>
.scroll-loader {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 16px 0 4px;
  color: var(--vio2-ink-3);
  letter-spacing: 0.08em;
}
.stripe-bar {
  width: 120px;
  height: 12px;
  border: 2px solid var(--vio2-bd);
  background: repeating-linear-gradient(-45deg, var(--vio2-yellow) 0 8px, var(--vio2-panel-2) 8px 16px);
  /* 沿 45 度纹理周期平移（16px * 根号 2）视觉上无缝滚动 */
  animation: stripe-slide 0.7s linear infinite;
}
@keyframes stripe-slide {
  from { background-position: 0 0; }
  to { background-position: 22.63px 0; }
}
.done-sticker {
  background: var(--vio2-green);
  color: #101010;
  border: 2px solid var(--vio2-bd);
  padding: 1px 9px 2px;
  font-size: 11px;
  font-weight: 700;
  transform: rotate(-1deg);
}
.txt { font-size: 10.5px; }
</style>
