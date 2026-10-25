/**
 * AI 助手 mock：AI02 探针 + AI01 SSE 流式模拟
 * 契约：docs/api/AI助手-接口清单.md §5（meta → tool → delta… → done 事件序列）
 * 演示口径（清单 §10）：
 *   - delta 按 3-4 字一片、约 55ms 一发，模拟 GLM 打字机；工具调用 start/done 各停半秒
 *   - 权限裁剪：空课类问题仅管理角色可问，学生问 → 拒答文案（PRD「空课查询仅管理角色」）
 *   - 中断：AbortSignal 停止即停（store 侧标 interrupted，气泡挂"回复可能不完整"）
 * TODO(联调)：后端 AI 模块落地后删除本文件
 */
import { findMockUserByToken } from './auth'
import { ok, registerMock } from './registry'
import type { AiChatHandlers } from '@/api/ai'

/** 模拟服务端从 Authorization 头收到的 token 解析当前用户（token 键与 http.ts/stores 一致） */
function currentUserRole(): 'SUPER_ADMIN' | 'DEPT_ADMIN' | 'STUDENT' {
  const token = sessionStorage.getItem('vio_token') ?? ''
  return findMockUserByToken(token)?.role ?? 'STUDENT'
}

/** mock 会话必须先登录（与真实后端 JWT 校验同口径） */
function currentUserName(): string {
  const token = sessionStorage.getItem('vio_token') ?? ''
  return findMockUserByToken(token)?.realName ?? ''
}

registerMock({
  method: 'get',
  path: '/api/ai/status',
  reply: () => ok({ enabled: true, model: 'GLM-4-Flash', quotaLeft: 5 })
})

/** 单条回答脚本：文本分片流 + 可选工具调用/富片段（store 拼装进气泡） */
interface AnswerScript {
  tool?: string
  paras: string[]
  table?: { head: string[]; rows: string[][] }
  src?: { label: string; link?: string; linkText?: string }
}

/** 按关键词选题库（演示答案为静态脚本，不真调数据接口——工具调用本身也是演示） */
function pickScript(content: string, role: string, name: string): AnswerScript {
  const isAdmin = role === 'SUPER_ADMIN' || role === 'DEPT_ADMIN'
  if (/空课|有空|谁.*闲|空闲/.test(content)) {
    if (!isAdmin) {
      return {
        paras: [
          '空课查询是管理角色的能力，你当前是学生身份，查不了其他成员的空课表。',
          '可以换个问法试试：「我的排班」「我的材料状态」这些你权限内的都可以问。'
        ]
      }
    }
    return {
      tool: 'queryFreeSlots',
      paras: ['查到了，电控工作室今天有空窗的成员 2 人：李添（14:00 后）、周子墨（全天）。', '晚班设备值班建议排他们，排班确认后在「排班管理」里能看到生效结果。'],
      src: { label: '成员空课', link: '/schedule', linkText: '去排班页核对' }
    }
  }
  if (/排班|值班|班次|我的班/.test(content)) {
    if (isAdmin) {
      return {
        tool: 'queryScheduleOverview',
        paras: [
          '本周（第 8 周）值班已排 6 个班次，全部在数媒二楼值班室，无空岗：',
          '· 10-26 周三 18:00-21:00 王雨桐（设备值班）\n\n· 10-27 周四 14:00-18:00 李添（耗材盘点）\n\n· 10-28 周五 18:00-21:00 周子墨（设备值班）',
          '还有 3 个班次排在下周，明细在排班管理页。'
        ],
        src: { label: '值班总览', link: '/schedule', linkText: '去排班管理核对' }
      }
    }
    return {
      tool: 'queryMySchedule',
      paras: [
        name + '，你这个月（10 月）共有 3 个班次，都在数媒二楼值班室：',
        '最近一次是 10-26（本周三），记得提前 10 分钟到岗做设备交接。'
      ],
      table: {
        head: ['日期', '时段', '岗位'],
        rows: [
          ['10-26 周三', '18:00-21:00', '设备值班'],
          ['10-28 周五', '18:00-21:00', '设备值班'],
          ['10-31 周一', '14:00-18:00', '耗材盘点']
        ]
      },
      src: { label: '我的排班', link: isAdmin ? '/schedule' : '/my/schedule', linkText: '去排班页核对' }
    }
  }
  if (/材料|没交|待交|交什么/.test(content)) {
    if (isAdmin) {
      return {
        tool: 'queryMaterialProgress',
        paras: [
          '蓝桥杯（省赛）报名队伍 12 支，材料交齐 8 支（67%）：',
          '未交齐的 4 支：星辰算法队（缺报名表）、芯火电子队（缺团队合照）、极客小队（缺报名表）、灵犀设计队（缺指导教师确认书）。',
          '报名表截止 10-28，建议先催两支缺必交件的队伍。'
        ],
        src: { label: '材料进度', link: '/material', linkText: '去材料管理核对' }
      }
    }
    return {
      tool: 'queryMyMaterials',
      paras: [
        '查到了，你在代码先锋队还有 2 项材料待交：',
        '· 报名表（必交，PDF，截止 10-28）\n\n· 团队合照（选交，随时可补）',
        '建议先把报名表交了——过了截止日没交会记缺勤，影响队伍报名资格。'
      ],
      src: { label: '我的材料', link: '/my/material', linkText: '去材料页提交' }
    }
  }
  if (/队伍|赛队|我的队/.test(content)) {
    if (isAdmin) {
      return {
        tool: 'queryTeamStats',
        paras: [
          '本学期共 4 场比赛，2 场报名中：蓝桥杯（省赛）12 支队伍、电子设计竞赛 7 支。',
          '已截止的 2 场：智能制造挑战赛 5 支、大创项目 9 支。'
        ],
        table: {
          head: ['比赛', '状态', '队伍数'],
          rows: [
            ['蓝桥杯 省赛', '报名中', '12'],
            ['电子设计竞赛', '报名中', '7'],
            ['智能制造挑战赛', '已截止', '5'],
            ['大创项目', '已截止', '9']
          ]
        },
        src: { label: '队伍统计', link: '/team', linkText: '去赛队管理核对' }
      }
    }
    return {
      tool: 'queryMyTeam',
      paras: [
        '你在「代码先锋队」，队长是王雨桐，队里共 3 人：你（前端）、李添（后端）、周子墨（测试）。',
        '队伍当前状态是报名中，材料还差团队合照没交齐。'
      ],
      src: { label: '我的赛队', link: '/my/team', linkText: '去赛队页看看' }
    }
  }
  if (/绩效|周报|分数|得分/.test(content)) {
    if (isAdmin) {
      return {
        tool: 'queryReportStats',
        paras: [
          '本周应交周报 42 份，已交 35 份，提交率 83%。',
          '未交 7 人：李添、周子墨、王一诺、赵天宇、陈子昂、孙嘉仪、张志豪——其中 3 人已连续两周未交，按规则各扣 2 分。'
        ],
        src: { label: '周报统计', link: '/performance', linkText: '去绩效统计核对' }
      }
    }
    return {
      tool: 'queryMyPerformance',
      paras: [
        '本月你的绩效 86 分，团队平均 82 分，高出 4 分。',
        '加分项：技术分享 1 次（+8）、周报全勤（+6）；扣分项：例会迟到 1 次（-3）。'
      ],
      src: { label: '我的绩效', link: '/my/performance', linkText: '去绩效页核对' }
    }
  }
  if (/比赛|蓝桥杯|报名|竞赛/.test(content)) {
    return {
      tool: 'queryContestRule',
      paras: [
        '蓝桥杯（省赛）本学期报名 10-30 截止，校内选拔已结束。',
        '报名要交：队伍信息表 + 指导教师确认书，模板在比赛详情页下载。',
        '以 3 人队报名时，前 2 人自动带出成员信息，第 3 人需要单独填。'
      ],
      src: { label: '比赛管理', link: isAdmin ? '/competition' : '/my/team', linkText: '去比赛页看看' }
    }
  }
  if (/待办|审批|要做什么|还有什么/.test(content)) {
    if (isAdmin) {
      return {
        tool: 'queryApprovalTodos',
        paras: [
          '你的审批待办共 3 条：成果提交 2 条（星辰算法队·蓝桥杯、灵犀设计队·电赛）、项目查看 1 条（智慧校园项目）。',
          '加人、指导教师类申请由队长发起、本人确认即生效，不进你的待办。'
        ],
        src: { label: '审批待办', link: '/approval', linkText: '去审批中心处理' }
      }
    }
    return {
      tool: 'queryMyTodos',
      paras: ['你最近有 3 件待办：① 报名表（10-28 截止）② 周报（每周日 22:00 前）③ 值班（10-26 周三 18:00）。', '最紧的是报名表，还剩 4 天。']
    }
  }
  if (isAdmin) {
    return {
      paras: [
        '这个问题我暂时没有对接对应的数据接口。',
        '目前我能查：待审批事项、材料收取进度、队伍报名情况、值班安排、周报提交情况、比赛规则、成员空课。',
        '换个问法试试，比如「材料收取进度怎么样？」'
      ]
    }
  }
  return {
    paras: [
      '这个问题我暂时没有对接对应的数据接口。',
      '目前我能查：我的排班、我的材料、我的队伍、我的绩效、待办事项、比赛报名规则。',
      '换个问法试试，比如「我还有什么材料没交？」'
    ]
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms))
}

/** AI01 mock 主入口：按脚本吐 SSE 事件序列（真实分支在 api/ai.ts 走 postSse） */
export async function mockAiChat(
  body: { conversationId: string | null; content: string },
  handlers: AiChatHandlers,
  signal?: AbortSignal
): Promise<void> {
  const script = pickScript(body.content, currentUserRole(), currentUserName())
  await sleep(400) // 模拟首帧延迟（鉴权 + 限流 + 模型排队）
  if (signal?.aborted) return
  handlers.onMeta?.({ messageId: 'mock-msg-' + Date.now(), conversationId: null })
  if (script.tool) {
    handlers.onTool?.({ name: script.tool, phase: 'start' })
    await sleep(500)
    if (signal?.aborted) return
    handlers.onTool?.({ name: script.tool, phase: 'done' })
    await sleep(300)
  }
  for (const para of script.paras) {
    // 按语义段切分（\n\n 分段），每段内再按 3-4 字一片打字机推送
    const chunks = para.match(/[\s\S]{1,4}/g) ?? []
    for (const c of chunks) {
      if (signal?.aborted) return
      handlers.onDelta?.({ text: c })
      await sleep(55)
    }
    handlers.onDelta?.({ text: '\n\n' })
    await sleep(180)
  }
  if (signal?.aborted) return
  if (script.table) handlers.onDemoTable?.(script.table)
  if (script.src) handlers.onDemoSrc?.(script.src)
  await sleep(200)
  handlers.onDone?.({ finishReason: 'stop', usage: { prompt: 128 + body.content.length, completion: 256 } })
}
