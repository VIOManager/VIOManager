/**
 * AI 助手会话 store：多会话管理 + SSE 流式接收状态机
 * 会话口径（接口清单待确认①的落地默认）：首版不落库（AI03-07 缓行），
 *   会话与消息存 localStorage（仅本浏览器可见，清缓存即没）——后端就绪后切接口
 * 消息拼装：delta 文本按 \n\n 切段落；table/src 富片段只有 mock 会给（见 api/ai.ts 注释）
 */
import { defineStore } from 'pinia'
import { apiAiChat, apiAiStatus } from '@/api/ai'
import type { AiConversation, AiMessage, AiSegment, AiStatus } from '@/api/ai'

const CONV_KEY = 'vio_ai_convs'
/** 本地会话上限：超出裁最旧的（防 localStorage 无限膨胀） */
const MAX_CONVS = 20

/** 时间戳展示口径：MM-DD HH:mm */
function stamp(): string {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' + p(d.getHours()) + ':' + p(d.getMinutes())
}

function loadConvs(): AiConversation[] {
  try {
    const raw = localStorage.getItem(CONV_KEY)
    if (raw) return JSON.parse(raw) as AiConversation[]
  } catch {
    // 坏数据当空会话处理
  }
  return []
}

function saveConvs(convs: AiConversation[]): void {
  while (convs.length > MAX_CONVS) convs.shift()
  localStorage.setItem(CONV_KEY, JSON.stringify(convs))
}

/** 新会话的开场白（本地直接落，不走流——省一次假请求） */
function greeting(name: string): AiMessage {
  return {
    id: 'local-' + Date.now(),
    role: 'assistant',
    createdAt: stamp(),
    segments: [
      {
        type: 'p',
        text:
          '你好' + (name ? '，' + name : '') +
          '，我是 VIO 智能助手。比赛报名、排班值班、材料进度这些可以直接问——' +
          '比如「我还有什么材料没交？」'
      }
    ]
  }
}

interface AiState {
  /** AI02 探针结果：loading 探测中 / ok 可用 / off 未启用 / error 后端未部署或网络异常 */
  statusState: 'loading' | 'ok' | 'off' | 'error'
  status: AiStatus | null
  convs: AiConversation[]
  activeId: string
  streaming: boolean
  abort: AbortController | null
}

export const useAiStore = defineStore('ai', {
  state: (): AiState => ({
    statusState: 'loading',
    status: null,
    convs: loadConvs(),
    activeId: '',
    streaming: false,
    abort: null
  }),
  getters: {
    active(state): AiConversation | null {
      return state.convs.find((c) => c.id === state.activeId) ?? null
    },
    canSend(state): boolean {
      return state.statusState === 'ok' && !state.streaming
    }
  },
  actions: {
    /** AI02 探针：未装配/未部署都灰化入口不报错（契约 AI02 说明） */
    async loadStatus(): Promise<void> {
      this.statusState = 'loading'
      try {
        this.status = await apiAiStatus()
        this.statusState = this.status.enabled ? 'ok' : 'off'
      } catch {
        this.statusState = 'error'
      }
    },
    /** 首次进页兜底：无会话自动开一个（带开场白） */
    ensureConversation(name: string): void {
      if (this.convs.length === 0) this.newConversation(name)
      else if (!this.active) this.activeId = this.convs[0].id
    },
    newConversation(name: string): void {
      const conv: AiConversation = {
        id: 'c' + Date.now(),
        title: '新会话',
        updatedAt: stamp(),
        messages: [greeting(name)]
      }
      this.convs.unshift(conv)
      this.activeId = conv.id
      saveConvs(this.convs)
    },
    switchConversation(id: string): void {
      if (!this.streaming) this.activeId = id
    },
    removeConversation(id: string): void {
      const i = this.convs.findIndex((c) => c.id === id)
      if (i < 0) return
      this.convs.splice(i, 1)
      if (this.activeId === id) this.activeId = this.convs.length > 0 ? this.convs[0].id : ''
      saveConvs(this.convs)
    },
    /** 停止生成：中断当前流（postSse 静默返回，done 不会再来） */
    stop(): void {
      this.abort?.abort()
    },
    /** 发消息：落用户消息 → 建助手占位 → 流式接收拼装 */
    send(content: string): void {
      const conv = this.active
      if (!conv || !this.canSend || content.trim() === '') return
      conv.messages.push({ id: 'u' + Date.now(), role: 'user', createdAt: stamp(), segments: [{ type: 'p', text: content.trim() }] })
      // 标题取首问前 20 字（对齐契约 AI03 语义，本地版）
      if (conv.messages.filter((m) => m.role === 'user').length === 1) conv.title = content.trim().slice(0, 20)
      void this.stream(conv, content.trim())
    },
    /** 重新生成：裁掉最后一条用户消息之后的助手回复，重发同一问 */
    regenerate(): void {
      const conv = this.active
      if (!conv || this.streaming) return
      let lastUser = -1
      for (let i = conv.messages.length - 1; i >= 0; i--) {
        if (conv.messages[i].role === 'user') {
          lastUser = i
          break
        }
      }
      if (lastUser < 0) return
      const content = (conv.messages[lastUser].segments[0] as { text: string }).text
      conv.messages.splice(lastUser + 1)
      void this.stream(conv, content)
    },
    /** 流式接收核心：send 与 regenerate 共用 */
    async stream(conv: AiConversation, content: string): Promise<void> {
      conv.messages.push({ id: '', role: 'assistant', createdAt: stamp(), streaming: true, tools: [], segments: [] })
      // ⚠️ Vue3 响应式坑：push 进响应式数组后再改字段，必须通过"读回来的代理引用"操作——
      //    直接持有 push 前的原始对象改（本函数闭包里）不会触发视图更新，页面会永远停在"正在生成"
      const ai = conv.messages[conv.messages.length - 1]
      this.streaming = true
      this.abort = new AbortController()
      let finished = false
      let streamError = ''
      const appendText = (text: string): void => {
        // delta 按 \n\n 分段：首段并入当前段落，其后各起新段
        const parts = text.split('\n\n')
        for (let i = 0; i < parts.length; i++) {
          if (parts[i] === '') continue
          const lastSeg = ai.segments.length > 0 ? ai.segments[ai.segments.length - 1] : null
          if (i === 0 && lastSeg && lastSeg.type === 'p') lastSeg.text += parts[i]
          else ai.segments.push({ type: 'p', text: parts[i] } as AiSegment)
        }
      }
      try {
        await apiAiChat(
          { conversationId: null, content },
          {
            onMeta: (e) => {
              if (e.messageId) ai.id = e.messageId
            },
            onTool: (e) => {
              if (!ai.tools) ai.tools = []
              const found = ai.tools.find((t) => t.name === e.name)
              if (found) found.phase = e.phase
              else ai.tools.push({ name: e.name, phase: e.phase })
            },
            onDelta: (e) => appendText(e.text),
            onDemoTable: (e) => ai.segments.push({ type: 'table', head: e.head, rows: e.rows }),
            onDemoSrc: (e) => ai.segments.push({ type: 'src', label: e.label, link: e.link, linkText: e.linkText }),
            onDone: (e) => {
              finished = true
              if (e.finishReason === 'interrupted') ai.interrupted = true
            },
            onError: (e) => {
              // 流中途失败（9104）：保留已生成内容 + 挂中断条
              finished = true
              ai.interrupted = true
              streamError = e.message
            }
          },
          this.abort.signal
        )
      } catch {
        // HTTP 层错误已在 http.ts 弹过统一提示；占位消息按中断口径收场
        finished = true
        ai.interrupted = true
      } finally {
        // done 没来过 = 被停/断流 → interrupted（PRD「回复可能不完整」语义）
        if (!finished && ai.segments.length > 0) ai.interrupted = true
        if (!finished && ai.segments.length === 0) {
          // 一个字没来就断：撤掉空占位，别留空气泡
          const i = conv.messages.indexOf(ai)
          if (i >= 0) conv.messages.splice(i, 1)
        }
        ai.streaming = false
        this.streaming = false
        this.abort = null
        conv.updatedAt = stamp()
        if (streamError !== '') ai.segments.push({ type: 'p', text: '（生成中断：' + streamError + '）' })
        saveConvs(this.convs)
      }
    }
  }
})
