<script setup lang="ts">
/**
 * AI 会话列表（左栏）：新建 / 切换 / 删除
 * ⚠️ 口径（接口清单待确认①落地默认）：会话存 localStorage 本浏览器，不落库——
 *    AI03-07 后端就绪后此组件切接口数据源，交互不变
 */
import { useUserStore } from '@/stores/user'
import { useAiStore } from '@/stores/ai'

const ai = useAiStore()
const user = useUserStore()
</script>

<template>
  <div class="box">
    <div class="box-h"><b>会话</b><span class="mono">CHATS</span></div>
    <button class="conv-new" :disabled="ai.streaming" @click="ai.newConversation(user.realName || '')">＋ 新建会话</button>
    <div class="conv-scroll">
      <div
        v-for="c in ai.convs"
        :key="c.id"
        class="conv"
        :class="{ on: c.id === ai.activeId }"
        @click="ai.switchConversation(c.id)"
      >
        <span class="ct">{{ c.title }}</span>
        <span class="t mono">{{ c.updatedAt }}</span>
        <button
          class="del mono"
          title="删除会话"
          :disabled="ai.streaming"
          @click.stop="ai.removeConversation(c.id)"
        >×</button>
      </div>
      <div class="conv-tip">本地会话：仅保存在本浏览器，清缓存即没；会话落库方案待拍板（接口清单待确认①）。</div>
    </div>
  </div>
</template>

<style scoped>
.box { border: 3px solid var(--vio2-bd); background: var(--vio2-panel); box-shadow: 6px 6px 0 var(--vio2-shadow); }
.box-h {
  display: flex; justify-content: space-between; align-items: center; padding: 10px 14px;
  border-bottom: 3px solid var(--vio2-bd); background: var(--vio2-head); color: #fff;
}
.box-h b { font-size: 13px; letter-spacing: 0.1em; }
.box-h span { font-size: 10px; color: #c9c4b4; letter-spacing: 0.1em; }
.conv-new {
  display: block; width: calc(100% - 24px); margin: 12px; text-align: center;
  background: var(--vio2-yellow); color: #101010; border: 2px solid var(--vio2-bd);
  box-shadow: 3px 3px 0 var(--vio2-shadow); font-weight: 900; font-size: 13px; padding: 7px 0; cursor: pointer;
}
.conv-new:hover:not(:disabled) { transform: translate(1px, 1px); box-shadow: 2px 2px 0 var(--vio2-shadow); }
.conv-new:disabled { opacity: 0.5; cursor: not-allowed; }
.conv-scroll { max-height: 420px; overflow-y: auto; }
.conv {
  position: relative; padding: 9px 34px 9px 12px; border-bottom: 2px solid var(--vio2-row-line);
  font-size: 12.5px; color: var(--vio2-ink-2); cursor: pointer;
}
.conv:hover { background: var(--vio2-panel-2); }
.conv.on { background: var(--vio2-yellow); color: #101010; font-weight: 800; border: 2px solid var(--vio2-bd); margin: 6px 8px; padding: 7px 32px 7px 10px; }
.conv .ct { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conv .t { display: block; font-size: 10px; opacity: 0.65; margin-top: 2px; }
.del {
  position: absolute; right: 8px; top: 8px; border: 2px solid var(--vio2-bd); background: var(--vio2-panel);
  color: var(--vio2-ink-3); font-size: 12px; line-height: 1; padding: 1px 5px; cursor: pointer; font-weight: 700;
}
.conv.on .del { background: var(--vio2-panel); color: var(--vio2-ink-2); }
.del:hover:not(:disabled) { background: var(--vio2-pink); color: #101010; }
.del:disabled { opacity: 0.4; cursor: not-allowed; }
.conv-tip { padding: 10px 12px; font-size: 11px; color: var(--vio2-ink-3); line-height: 1.6; border-top: 2px solid var(--vio2-row-line); }
</style>
