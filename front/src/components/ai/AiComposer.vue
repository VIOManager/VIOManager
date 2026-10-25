<script setup lang="ts">
/**
 * AI 输入栏：快捷问题芯片 + 多行输入 + 发送
 * 交互口径：ENTER 发送 / SHIFT+ENTER 换行；上限 500 字（契约 AI01 body.content 1-500）
 * 不可用态：服务未启用/未部署（禁输入并提示）或生成中（等流收完再问）
 */
import { computed, ref } from 'vue'
import { AI_QUICK_QUESTIONS } from '@/api/ai'
import { useAiStore } from '@/stores/ai'
import { useUserStore } from '@/stores/user'

const ai = useAiStore()
const user = useUserStore()
const text = ref('')

/** 快捷问题按角色出题：学生自查类，管理员管理类（role 异步恢复，必须 computed 防冻结默认值） */
const quickQuestions = computed(() =>
  user.role === 'STUDENT' ? AI_QUICK_QUESTIONS.STUDENT : AI_QUICK_QUESTIONS.ADMIN
)

function send(): void {
  if (text.value.trim() === '' || !ai.canSend) return
  ai.send(text.value)
  text.value = ''
}

/** ENTER 发送、SHIFT+ENTER 换行（输入法组合中不拦截） */
function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    send()
  }
}
</script>

<template>
  <div>
    <!-- 快捷提问：点即发送（按角色出题——学生自查类 / 管理员管理类） -->
    <div class="chips">
      <button v-for="q in quickQuestions" :key="q" class="qchip" :disabled="!ai.canSend" @click="ai.send(q)">{{ q }}</button>
    </div>

    <div class="inputbar">
      <textarea
        v-model="text"
        :placeholder="ai.statusState === 'ok' ? '问点什么… 例如：我蓝桥杯的队伍还缺谁没交材料？' : 'AI 服务不可用'"
        :disabled="ai.statusState !== 'ok'"
        maxlength="500"
        @keydown="onKeydown"
      ></textarea>
      <button class="send" :disabled="!ai.canSend || text.trim() === ''" @click="send">
        {{ ai.streaming ? '生成中…' : '发送 ➤' }}
      </button>
    </div>
    <div class="hint mono">
      ENTER 发送 · SHIFT+ENTER 换行 · 回答由 GLM 生成，数据经后端接口按你的权限裁剪
      <span v-if="text.length > 400" class="cnt">{{ text.length }}/500</span>
    </div>
  </div>
</template>

<style scoped>
.chips { display: flex; flex-wrap: wrap; gap: 9px; padding: 10px 20px 4px; border-top: 2px solid var(--vio2-row-line); }
.qchip {
  font-size: 12px; font-weight: 800; color: var(--vio2-ink-2); background: var(--vio2-panel);
  border: 2px solid var(--vio2-bd); padding: 4px 13px; cursor: pointer; box-shadow: 2px 2px 0 var(--vio2-shadow);
}
.qchip:hover:not(:disabled) { transform: translate(1px, 1px); box-shadow: 1px 1px 0 var(--vio2-shadow); background: var(--vio2-lilac); color: #101010; }
.qchip:disabled { opacity: 0.45; cursor: not-allowed; }
.inputbar { display: flex; gap: 12px; align-items: stretch; padding: 12px 20px 8px; }
.inputbar textarea {
  flex: 1; min-height: 46px; max-height: 120px; resize: vertical; font-family: inherit; font-size: 13.5px;
  border: 2.5px solid var(--vio2-bd); background: var(--vio2-panel); color: var(--vio2-ink);
  padding: 10px 13px; box-shadow: inset 3px 3px 0 rgba(16, 16, 16, 0.06);
}
.inputbar textarea:focus { outline: none; border-color: var(--vio2-blue); }
.inputbar textarea:disabled { background: var(--vio2-panel-2); color: var(--vio2-ink-3); cursor: not-allowed; }
.send {
  flex: none; background: var(--vio2-yellow); color: #101010; border: 2.5px solid var(--vio2-bd);
  box-shadow: 3px 3px 0 var(--vio2-shadow); font: inherit; font-weight: 900; font-size: 13.5px;
  padding: 10px 22px; cursor: pointer; min-width: 96px;
}
.send:hover:not(:disabled) { transform: translate(1px, 1px); box-shadow: 2px 2px 0 var(--vio2-shadow); }
.send:disabled { opacity: 0.5; cursor: not-allowed; }
.hint { padding: 0 20px 14px; font-size: 11px; color: var(--vio2-ink-3); letter-spacing: 0.04em; }
.cnt { margin-left: 10px; color: var(--vio2-pink); font-weight: 700; }
</style>
