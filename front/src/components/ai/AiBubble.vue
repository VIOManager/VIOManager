<script setup lang="ts">
/**
 * AI 消息气泡：用户（右侧黄块）/ 助手（左侧面板色）
 * 渲染口径：segments 结构化渲染（段落/数据小表/数据源标签），**禁 v-html**——
 *   真实模式下回答来自 GLM 生成，用户内容永不进 innerHTML（红线）
 * 流式态：末段落跟打字机光标 + 停止钮（store.stop）；中断态挂"回复可能不完整"警告条
 * 丝滑打字（lzh 2026-10-24 要求，像 Claude 那样逐字吐）：数据层照实收 delta，
 *   显示层用 reveal 缓冲按自己的节奏放字——积压多就吐快（大块约 0.5 秒追平），
 *   接近实时时 16ms 一字；流结束后缓冲里的余字继续吐完再收光标
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useAiStore } from '@/stores/ai'
import { AI_TOOL_LABELS } from '@/api/ai'
import type { AiMessage } from '@/api/ai'

const props = defineProps<{ msg: AiMessage; userName: string }>()
/** 每放出一批字通知页面贴底滚动（store 文本没变时 DOM 高度也在长，页面 watcher 看不见） */
const emit = defineEmits<{ grow: [] }>()
const ai = useAiStore()

/** 是否为该气泡最后一个文本段（等网络时光标挂在它尾部） */
function isLastP(i: number): boolean {
  const pIdx: number[] = []
  props.msg.segments.forEach((s, k) => {
    if (s.type === 'p') pIdx.push(k)
  })
  return pIdx.length > 0 && i === pIdx[pIdx.length - 1]
}

// ── 打字机渲染缓冲（仅显示层；store 与接口层零改动） ──
const reveal = ref(0)
let timer: number | null = null

/** 全部 p 段总字数（打字进度的分母） */
const totalP = computed(() => {
  let n = 0
  for (const s of props.msg.segments) {
    if (s.type === 'p') n += s.text.length
  }
  return n
})

/** 打字中 = 流还在收 或 缓冲没吐完 */
const typing = computed(() => props.msg.streaming || reveal.value < totalP.value)

/** 第 i 段之前所有 p 段的累计字数（判断打字进度落在哪段） */
function pBefore(i: number): number {
  let n = 0
  for (let k = 0; k < i; k++) {
    const s = props.msg.segments[k]
    if (s.type === 'p') n += s.text.length
  }
  return n
}

/** p 段展示文本：没打到的段返回空串，打到的段截断到 reveal */
function pShown(i: number): string {
  const seg = props.msg.segments[i]
  if (seg.type !== 'p') return ''
  if (!typing.value) return seg.text
  const start = pBefore(i)
  if (reveal.value <= start) return ''
  return seg.text.slice(0, reveal.value - start)
}

/** 富片段（表/数据源）在前面所有文字吐完后整体出现 */
function blockShown(i: number): boolean {
  return !typing.value || reveal.value >= pBefore(i)
}

/** 光标位置：进度落在本段内；或字全吐完但流还开着（等网络）挂最后一个 p 段尾 */
function cursorAt(i: number): boolean {
  if (!typing.value) return false
  const seg = props.msg.segments[i]
  if (!seg || seg.type !== 'p') return false
  const start = pBefore(i)
  if (reveal.value > start && reveal.value < start + seg.text.length) return true
  return reveal.value >= start + seg.text.length && isLastP(i)
}

function tick() {
  const backlog = totalP.value - reveal.value
  if (backlog <= 0) return
  // 自适应速率：积压 25 字内一字一跳最丝滑；大积压按 1/25 比例加速，半秒内顺滑追平
  const step = backlog > 25 ? Math.ceil(backlog / 25) : 1
  reveal.value = Math.min(totalP.value, reveal.value + step)
  emit('grow')
}

watch(
  typing,
  (on) => {
    if (on && timer === null) timer = window.setInterval(tick, 16)
    else if (!on && timer !== null) {
      window.clearInterval(timer)
      timer = null
    }
  },
  { immediate: true }
)

onMounted(() => {
  // 挂载即跳到当前全量：历史消息整条直出；流到一半组件被重建（空 id 换真 id 触发重挂）也不会从头重打
  reveal.value = totalP.value
})
watch(
  () => props.msg,
  () => {
    // 同一实例换绑了别的消息（极端复用场景）：跳到新消息全量，避免旧进度错切新文本
    reveal.value = totalP.value
  }
)
onUnmounted(() => {
  if (timer !== null) window.clearInterval(timer)
})
</script>

<template>
  <div class="mrow" :class="{ me: msg.role === 'user' }">
    <i class="bava" :class="msg.role === 'user' ? 'user' : 'ai'">{{ msg.role === 'user' ? userName.slice(0, 1) : '✦' }}</i>
    <div class="bubble">
      <div class="who mono">{{ msg.role === 'user' ? 'ME · ' + msg.createdAt : 'AI ASSISTANT' }}</div>

      <!-- 工具调用徽章（start=蓝 进行中 / done=绿 完成） -->
      <div v-if="msg.tools && msg.tools.length > 0" class="tools">
        <span v-for="t in msg.tools" :key="t.name" class="stamp" :class="t.phase === 'start' ? 'blue' : 'ok'">
          {{ AI_TOOL_LABELS[t.name] ?? t.name }} {{ t.phase === 'start' ? '…' : '✓' }}
        </span>
      </div>

      <template v-for="(seg, i) in msg.segments" :key="i">
        <p v-if="seg.type === 'p' && pShown(i) !== ''" class="bp">
          {{ pShown(i) }}<span v-if="cursorAt(i)" class="cursor"></span>
        </p>
        <table v-else-if="seg.type === 'table' && blockShown(i)" class="dtable">
          <tr>
            <th v-for="h in seg.head" :key="h">{{ h }}</th>
          </tr>
          <tr v-for="(row, r) in seg.rows" :key="r">
            <td v-for="(cell, c) in row" :key="c" :class="{ mono: c < seg.head.length - 1 }">{{ cell }}</td>
          </tr>
        </table>
        <div v-else-if="seg.type === 'src' && blockShown(i)" class="src">
          <span class="s">数据源：{{ seg.label }}</span>
          <router-link v-if="seg.link" :to="seg.link">{{ seg.linkText ?? '去核对' }} →</router-link>
        </div>
      </template>

      <!-- 流式中：生成指示 + 停止钮 -->
      <div v-if="msg.streaming" class="streaming-tag mono">
        <i></i>正在生成
        <button class="stopbtn" @click="ai.stop()">■ 停止</button>
      </div>

      <!-- 中断警告条（PRD：finishReason=interrupted / 断流；等余字吐完再出现） -->
      <div v-if="msg.interrupted && !msg.streaming && !typing" class="cutbar">⚠ 回复可能不完整<button class="cutbtn" @click="ai.regenerate()">重新生成</button></div>
    </div>
  </div>
</template>

<style scoped>
.mrow { display: flex; gap: 12px; margin-bottom: 18px; }
.mrow.me { flex-direction: row-reverse; }
.bava {
  width: 40px; height: 40px; flex: none; display: inline-grid; place-items: center;
  border: 2.5px solid var(--vio2-shadow); font-weight: 900; font-size: 15px; font-style: normal;
}
.bava.ai { background: var(--vio2-lilac); color: #101010; }
.bava.user { background: var(--vio2-green); color: #101010; }
.bubble {
  max-width: 76%; border: 2.5px solid var(--vio2-bd); background: var(--vio2-panel);
  box-shadow: 4px 4px 0 var(--vio2-shadow); padding: 11px 15px; min-width: 0;
}
.mrow.me .bubble { background: var(--vio2-yellow); color: #101010; }
.who { font-size: 10px; letter-spacing: 0.16em; color: var(--vio2-ink-3); margin-bottom: 6px; }
.mrow.me .who { color: rgba(16, 16, 16, 0.6); }
.bp { margin-bottom: 8px; font-size: 13.5px; white-space: pre-wrap; word-break: break-word; }
.bp:last-child { margin-bottom: 0; }
.tools { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 8px; }
/* 数据小表（mock 演示富片段；类名沿用设计样张） */
.dtable { width: 100%; border-collapse: collapse; margin: 8px 0; font-size: 12.5px; }
.dtable th { background: var(--vio2-panel-2); border: 2px solid var(--vio2-row-line); padding: 5px 9px; text-align: left; font-size: 11px; letter-spacing: 0.06em; color: var(--vio2-ink-2); }
.dtable td { border: 2px solid var(--vio2-row-line); padding: 5px 9px; color: var(--vio2-ink-2); }
.src { display: inline-flex; gap: 8px; align-items: center; margin-top: 8px; font-size: 11px; color: var(--vio2-ink-3); }
.src .s { background: var(--vio2-panel-2); border: 1.5px solid var(--vio2-bd); padding: 1px 8px; font-weight: 700; color: var(--vio2-ink-2); }
.src a { color: var(--vio2-blue); font-weight: 700; text-decoration: none; }
.src a:hover { text-decoration: underline; }
/* 打字机光标 */
.cursor { display: inline-block; width: 9px; height: 16px; background: var(--vio2-ink); vertical-align: -2px; margin-left: 2px; animation: ai-blink 0.9s steps(1) infinite; }
@keyframes ai-blink { 50% { opacity: 0; } }
.streaming-tag { display: inline-flex; align-items: center; gap: 7px; margin-top: 8px; font-size: 11px; color: var(--vio2-ink-2); font-weight: 700; }
.streaming-tag i { width: 10px; height: 10px; background: var(--vio2-blue); animation: ai-blink 0.9s steps(1) infinite; }
.stopbtn { font-size: 11px; color: var(--vio2-ink-2); background: var(--vio2-panel); border: 2px solid var(--vio2-bd); padding: 2px 10px; cursor: pointer; font-weight: 700; box-shadow: 2px 2px 0 var(--vio2-shadow); }
.stopbtn:hover { background: var(--vio2-pink); color: #101010; }
/* 中断警告条 */
.cutbar {
  display: flex; align-items: center; gap: 9px; margin-top: 8px; padding: 6px 11px;
  background: var(--vio2-pink); color: #101010; border: 2px solid var(--vio2-bd);
  font-size: 12px; font-weight: 800; width: fit-content;
}
.cutbtn { font-size: 11px; font-weight: 800; background: var(--vio2-panel); border: 2px solid var(--vio2-bd); padding: 1px 8px; cursor: pointer; color: var(--vio2-ink); }
.cutbtn:hover { background: var(--vio2-yellow); }
</style>
