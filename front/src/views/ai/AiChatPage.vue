<script setup lang="ts">
/**
 * AI 智能助手页（PRD §3.15；契约 docs/api/AI助手-接口清单.md）
 * 双入口之一（侧栏菜单）；悬浮球见 components/ai/AiFab.vue
 * 状态机（AI02 探针驱动）：ok 可聊 / off 后端未启用（灰化） / error 后端未部署或网络异常
 * 自动滚动：仅当用户本就在底部附近才跟随（阅读历史时被打断很难受）
 */
import { nextTick, onMounted, ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { useAiStore } from '@/stores/ai'
import AiBubble from '@/components/ai/AiBubble.vue'
import AiComposer from '@/components/ai/AiComposer.vue'
import AiConvList from '@/components/ai/AiConvList.vue'

const user = useUserStore()
const ai = useAiStore()
const msgsRef = ref<HTMLElement>()

onMounted(() => {
  void ai.loadStatus()
  ai.ensureConversation(user.realName || '')
})

/** 贴底跟随：仅当用户本就在底部附近才滚（阅读历史时被打断很难受） */
async function followBottom(): Promise<void> {
  await nextTick()
  const el = msgsRef.value
  if (!el) return
  if (el.scrollHeight - el.scrollTop - el.clientHeight < 140) el.scrollTop = el.scrollHeight
}

/** store 侧变化（新消息/新段落）驱动一次跟随 */
watch(
  () => {
    const conv = ai.active
    if (!conv) return 0
    let n = conv.messages.length * 10000
    const last = conv.messages[conv.messages.length - 1]
    if (last) {
      for (const s of last.segments) {
        if (s.type === 'p') n += s.text.length
      }
    }
    return n
  },
  () => {
    void followBottom()
  }
)
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>AI 智能助手<span class="sub mono">AI ASSISTANT · 自然语言查一切</span></h3>
      <div class="dossier-meta">
        <span>数据范围：仅本人</span>
        <span>仅查询 · 不做写操作</span>
        <span class="sticker">GLM 驱动</span>
      </div>
    </div>

    <!-- 服务态横幅：off/error 才出现（ok 不占位） -->
    <div v-if="ai.statusState === 'off'" class="statebar warn">
      AI 服务未启用——GLM key 未配置时后端零装配，系统其余功能不受影响；启用后此处自动恢复。
      <button class="retry" @click="ai.loadStatus()">重新探测</button>
    </div>
    <div v-else-if="ai.statusState === 'error'" class="statebar bad">
      AI 服务暂不可用（后端未部署或网络异常）——mock 模式可完整演示本页全部交互。
      <button class="retry" @click="ai.loadStatus()">重试</button>
    </div>

    <div class="chat-grid">
      <AiConvList />

      <div class="chatbox">
        <div class="box-h">
          <b>{{ user.realName || '我' }} 的会话</b>
          <span class="mono">{{ ai.status?.model ?? 'SSE' }} · STREAMING</span>
        </div>

        <div ref="msgsRef" class="msgs">
          <!-- @grow：气泡打字机每放出一批字通知贴底跟随（store 文本没变时 DOM 也在长） -->
          <AiBubble v-for="(m, i) in ai.active?.messages ?? []" :key="m.id || i" :msg="m" :user-name="user.realName || '我'" @grow="followBottom" />
        </div>

        <AiComposer />
      </div>
    </div>
  </div>
</template>

<style scoped>
.dossier-head h3 .sub {
  display: block; font-size: 11px; font-weight: 400; letter-spacing: 0.24em;
  color: var(--vio2-ink-3); margin-bottom: 5px; margin-top: 2px;
}
.dossier-meta .sticker {
  background: var(--vio2-lilac); color: #101010; border: 2px solid var(--vio2-bd);
  box-shadow: 3px 3px 0 var(--vio2-shadow); font-weight: 900; transform: rotate(2deg);
}
/* 服务态横幅 */
.statebar {
  display: flex; align-items: center; gap: 12px; margin-bottom: 14px; padding: 9px 14px;
  font-size: 12.5px; font-weight: 700; border: 2.5px solid var(--vio2-bd); box-shadow: 4px 4px 0 var(--vio2-shadow);
}
.statebar.warn { background: var(--vio2-yellow); color: #101010; }
.statebar.bad { background: var(--vio2-pink); color: #101010; }
.retry {
  margin-left: auto; flex: none; font-size: 11.5px; font-weight: 800; padding: 3px 12px;
  background: var(--vio2-panel); color: var(--vio2-ink); border: 2px solid var(--vio2-bd); cursor: pointer;
}
.retry:hover { background: var(--vio2-panel-2); }
/* 聊天双列：会话栏 + 对话区 */
.chat-grid { display: grid; grid-template-columns: 208px 1fr; gap: 18px; align-items: start; }
.chatbox {
  display: flex; flex-direction: column; min-width: 0;
  border: 3px solid var(--vio2-bd); background: var(--vio2-panel); box-shadow: 6px 6px 0 var(--vio2-shadow);
  height: calc(100vh - 300px); min-height: 420px;
}
.box-h {
  display: flex; justify-content: space-between; align-items: center; padding: 10px 14px;
  border-bottom: 3px solid var(--vio2-bd); background: var(--vio2-head); color: #fff;
}
.box-h b { font-size: 13px; letter-spacing: 0.1em; }
.box-h span { font-size: 10px; color: #c9c4b4; letter-spacing: 0.1em; }
.msgs { flex: 1; overflow-y: auto; padding: 18px 20px 8px; }
/* 窄视口（IAB 面板/小窗）：会话栏让位给对话 */
@media (max-width: 1020px) {
  .chat-grid { grid-template-columns: 1fr; }
  .chat-grid > :first-child { display: none; }
  .chatbox { height: calc(100vh - 250px); min-height: 360px; }
}
</style>
