/**
 * AI 助手接口层 —— 契约：docs/api/AI助手-接口清单.md（错误码 9100-9199）
 * 联调口径：mock 先行（VITE_USE_MOCK=true 时聊天走 mock/ai.ts 模拟流），后端 SSE 落地后自动切真
 * 会话口径（2026-10-25 起切真）：每会话首发先 AI03 建会话拿 conversationId，
 *   之后每条消息都带上——后端每轮带回最近 20 条历史，"他/刚才那个"这类指代才有着落
 *   （此前恒传 null 失忆裸聊，组长实测追问"他参加什么比赛了"模型不知道"他"是谁）
 * TODO(联调)：后端 AI 模块（SSE + 令牌桶 + GLM）落地后删除 mock 分支与 mock/ai.ts
 */
import { get, mockMode, postIdem, postSse } from './http'
import { mockAiChat } from './mock/ai'

/** AI02 探针：enabled=false 时页面灰化入口（后端未装配也回 code 0，不报错刷屏） */
export interface AiStatus {
  enabled: boolean
  model: string
  /** 当前窗口剩余次数（令牌桶余量展示） */
  quotaLeft: number
}

// ── SSE 事件载荷（契约 §5）──
export interface AiMetaEvent {
  messageId: string
  conversationId: string | null
}
export interface AiToolEvent {
  name: string
  phase: 'start' | 'done'
}
export interface AiDeltaEvent {
  text: string
}
export interface AiDoneEvent {
  finishReason: 'stop' | 'interrupted'
  usage: { prompt: number; completion: number }
}

// ── 页面侧消息模型（气泡渲染用；结构化 segments，禁 v-html）──
/** 段落 / 数据小表 / 数据源标签 */
export type AiSegment =
  | { type: 'p'; text: string }
  | { type: 'table'; head: string[]; rows: string[][] }
  | { type: 'src'; label: string; link?: string; linkText?: string }

export interface AiToolBadge {
  name: string
  phase: 'start' | 'done'
}

export interface AiMessage {
  id: string
  role: 'user' | 'assistant'
  createdAt: string
  /** 流式接收中（打字机光标 + 停止钮显隐） */
  streaming?: boolean
  /** finishReason=interrupted 或断流 → 气泡挂"回复可能不完整"警告条（PRD 语义） */
  interrupted?: boolean
  tools?: AiToolBadge[]
  segments: AiSegment[]
}

export interface AiConversation {
  id: string
  title: string
  updatedAt: string
  messages: AiMessage[]
  /** 后端会话 ID（AI03 建会话返回；首发时 stores/ai.ts 落上，localStorage 持久化） */
  backendId?: string | null
}

/**
 * 工具名翻译（契约工具白名单 → 展示名，清单 §2 定名 2026-10-25 终态 15 个）
 * ⚠️ 就近放行原因：config/labels.ts 已 325 行超单文件红线，AI 契约枚举随契约文件走
 */
export const AI_TOOL_LABELS: Record<string, string> = {
  queryMySchedule: '查我的排班',
  queryMyAchievements: '查我的贡献',
  queryMyWeeklyPerformance: '查我的周绩效',
  queryMembers: '查人员档案',
  queryTeam: '查赛队',
  queryTeamsMissingMaterials: '查未交材料赛队',
  queryTeamAchievements: '查赛队成果',
  queryFreeSlots: '查成员空课',
  queryMemberTeams: '查成员参赛',
  queryContests: '查比赛列表',
  queryProjects: '查项目列表',
  queryOrgStructure: '查组织架构',
  queryMyApprovals: '查我的审批',
  querySemesterInfo: '查学期信息',
  queryScheduleTable: '查排班总表',
  // mock 演示专用名（仅 VITE_USE_MOCK 模式的假工具；真契约已落地同名概念，mock 下线时一并删）
  queryMyMaterials: '查我的材料',
  queryMyTeam: '查我的队伍',
  queryMyPerformance: '查我的绩效',
  queryMyTodos: '查我的待办',
  queryContestRule: '查比赛规则',
  queryScheduleOverview: '查值班总览',
  queryMaterialProgress: '查材料进度',
  queryTeamStats: '查队伍统计',
  queryApprovalTodos: '查审批待办',
  queryReportStats: '查周报统计'
}

/** AI01 回调集：流式事件逐个回调，页面侧只管拼装 */
export interface AiChatHandlers {
  onMeta?: (e: AiMetaEvent) => void
  onTool?: (e: AiToolEvent) => void
  onDelta?: (e: AiDeltaEvent) => void
  onDone?: (e: AiDoneEvent) => void
  /** SSE error 事件（流中途失败，9104）：流到此为止 */
  onError?: (e: { code: number; message: string }) => void
  /** mock 演示专用：结构化富片段（数据小表 / 数据源标签）。真实后端首版只发纯文本 delta，
   *  后端要支持富结构时契约先行再对齐——前端渲染层已就位 */
  onDemoTable?: (e: { head: string[]; rows: string[][] }) => void
  onDemoSrc?: (e: { label: string; link?: string; linkText?: string }) => void
}

/** AI02：探针（silent——未部署/未装配不该刷"网络异常"toast，页面自行兜底灰化） */
export function apiAiStatus(): Promise<AiStatus> {
  return get('/api/ai/status', undefined, { silent: true })
}

/** AI03：新建会话。body 可整个省略（title 走后端默认"新对话"，首问落库时自动改为首问前 20 字） */
export function apiAiCreateConversation(): Promise<{ id: string }> {
  return postIdem('/api/ai/conversations', {}, 'ai-conv-' + crypto.randomUUID())
}

/** AI01：发消息（SSE 流式）。conversationId 带 backendId = 会话模式（后端带最近 20 条历史上下文） */
export async function apiAiChat(
  body: { conversationId: string | null; content: string },
  handlers: AiChatHandlers,
  signal?: AbortSignal
): Promise<void> {
  if (mockMode) {
    return mockAiChat(body, handlers, signal)
  }
  return postSse(
    '/api/ai/chat',
    body,
    (ev) => {
      if (ev.event === 'meta') handlers.onMeta?.(ev.data as unknown as AiMetaEvent)
      else if (ev.event === 'tool') handlers.onTool?.(ev.data as unknown as AiToolEvent)
      else if (ev.event === 'delta') handlers.onDelta?.(ev.data as unknown as AiDeltaEvent)
      else if (ev.event === 'done') handlers.onDone?.(ev.data as unknown as AiDoneEvent)
      else if (ev.event === 'error') handlers.onError?.(ev.data as unknown as { code: number; message: string })
    },
    signal
  )
}

/** 快捷提问（页面问题芯片）：按角色出题——学生自查视角，管理员管理视角（lzh 2026-10-24 拍板） */
export const AI_QUICK_QUESTIONS: Record<'STUDENT' | 'ADMIN', string[]> = {
  STUDENT: ['我的待办', '未交材料', '我的排班', '我的队伍', '本周绩效', '比赛报名规则'],
  ADMIN: ['待审批事项', '材料收取进度', '队伍报名情况', '本周值班安排', '周报提交情况', '比赛报名规则']
}
