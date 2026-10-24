<script setup lang="ts">
/**
 * AI 消息气泡：用户（右侧黄块）/ 助手（左侧面板色）
 * 渲染口径：segments 结构化渲染（段落/数据小表/数据源标签），**禁 v-html**——
 *   真实模式下回答来自 GLM 生成，用户内容永不进 innerHTML（红线）
 * 流式态：末段落跟打字机光标 + 停止钮（store.stop）；中断态挂"回复可能不完整"警告条
 */
import { useAiStore } from '@/stores/ai'
import { AI_TOOL_LABELS } from '@/api/ai'
import type { AiMessage } from '@/api/ai'

const props = defineProps<{ msg: AiMessage; userName: string }>()
const ai = useAiStore()

/** 是否为该气泡最后一个文本段（光标只跟在它后面） */
function isLastP(i: number): boolean {
  const pIdx: number[] = []
  props.msg.segments.forEach((s, k) => {
    if (s.type === 'p') pIdx.push(k)
  })
  return pIdx.length > 0 && i === pIdx[pIdx.length - 1]
}
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
        <p v-if="seg.type === 'p'" class="bp">
          {{ seg.text }}<span v-if="msg.streaming && isLastP(i)" class="cursor"></span>
        </p>
        <table v-else-if="seg.type === 'table'" class="dtable">
          <tr>
            <th v-for="h in seg.head" :key="h">{{ h }}</th>
          </tr>
          <tr v-for="(row, r) in seg.rows" :key="r">
            <td v-for="(cell, c) in row" :key="c" :class="{ mono: c < seg.head.length - 1 }">{{ cell }}</td>
          </tr>
        </table>
        <div v-else-if="seg.type === 'src'" class="src">
          <span class="s">数据源：{{ seg.label }}</span>
          <router-link v-if="seg.link" :to="seg.link">{{ seg.linkText ?? '去核对' }} →</router-link>
        </div>
      </template>

      <!-- 流式中：生成指示 + 停止钮 -->
      <div v-if="msg.streaming" class="streaming-tag mono">
        <i></i>正在生成
        <button class="stopbtn" @click="ai.stop()">■ 停止</button>
      </div>

      <!-- 中断警告条（PRD：finishReason=interrupted / 断流） -->
      <div v-if="msg.interrupted && !msg.streaming" class="cutbar">⚠ 回复可能不完整<button class="cutbtn" @click="ai.regenerate()">重新生成</button></div>
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
