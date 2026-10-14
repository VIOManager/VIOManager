/**
 * 知识库 mock 数据层（接口层在 ./kb.ts）
 * 依据 PRD §3.14：分类对齐 KB_CATEGORY 字典（Python 教程/机械设计规范/三维建模教程/AI 提示词库/其他）
 * 内容来源三类：管理员上传 / 外部链接收录 / AI 总结
 * TODO(联调)：后端知识库清单就绪后删除本文件
 */

export interface MockKbItem {
  id: string
  title: string
  category: string
  summary: string
  content: string
  sourceType: 'ADMIN_UPLOAD' | 'LINK' | 'AI_SUMMARY'
  linkUrl: string
  pinned: boolean
  viewCount: number
  publisherName: string
  createdAt: string
}

export interface MockKbComment {
  id: string
  itemId: string
  authorName: string
  authorLoginName: string
  content: string
  createdAt: string
}

/** 收藏关系：itemId → 登录名集合（按人记忆） */
export const kbFavorites: Record<string, string[]> = {}

export const kbItems: MockKbItem[] = [
  {
    id: 'kb1', title: 'STM32 HAL 库入门：从点灯到定时器中断', category: 'Python 教程', pinned: true,
    summary: '电控组新人入门系列第一篇，CubeMX 配置 + HAL 点灯 + 中断入门。',
    content: '本文覆盖：CubeMX 工程创建、GPIO 输出点灯、外部中断按键、定时器 PWM 输出。\n\n配套工程在团队服务器 /pub/stm32 目录。学习建议：先跑通点灯再碰中断，不要跳步。',
    sourceType: 'ADMIN_UPLOAD', linkUrl: '', viewCount: 86, publisherName: '陈志远', createdAt: '2026-09-15 10:00:00'
  },
  {
    id: 'kb2', title: 'Python 爬虫合规与校内数据源清单', category: 'Python 教程', pinned: false,
    summary: '爬数据的边界在哪、校内哪些公开接口可用、robots 与频率控制。',
    content: '合规三问：有没有授权？有没有超频率？有没有碰个人隐私？\n\n校内可用数据源：教务公告 RSS、图书馆检索接口（限速）、就业网公开榜单。',
    sourceType: 'LINK', linkUrl: 'https://example.com/py-crawl-guide', viewCount: 152, publisherName: '李明', createdAt: '2026-09-20 14:30:00'
  },
  {
    id: 'kb3', title: '机械制图国标速查（GB/T 4458 摘要）', category: '机械设计规范', pinned: false,
    summary: '图纸幅面、比例、线型、尺寸标注的常用条款摘要。',
    content: '常用条款：A4/A3 幅面尺寸；粗实线 0.5mm；尺寸线终端箭头画法；孔径标注规范。\n\n出图前对照本表自查一遍，能减少 80% 的返工。',
    sourceType: 'ADMIN_UPLOAD', linkUrl: '', viewCount: 64, publisherName: '赵天宇', createdAt: '2026-09-25 09:10:00'
  },
  {
    id: 'kb4', title: 'SolidWorks 装配体配合实战 10 例', category: '三维建模教程', pinned: false,
    summary: '同心/重合/距离/角度配合的典型用法与翻车点。',
    content: '10 个案例：轴承座装配、齿轮啮合、滑轨副、连杆机构……每个案例附练习模型下载地址。',
    sourceType: 'LINK', linkUrl: 'https://example.com/sw-assembly-10', viewCount: 98, publisherName: '陈志远', createdAt: '2026-09-28 16:40:00'
  },
  {
    id: 'kb5', title: '赛题拆解提示词模板（数模/电赛通用）', category: 'AI 提示词库', pinned: true,
    summary: '让 AI 帮你把赛题拆成任务树的提示词模板，直接复制可用。',
    content: '模板：\n你是一名竞赛教练。请把下面的赛题文本拆解为：1) 硬性指标清单 2) 评分点权重推测 3) 任务树（按周排布）4) 风险清单。\n赛题如下：\n【粘贴赛题】',
    sourceType: 'AI_SUMMARY', linkUrl: '', viewCount: 210, publisherName: 'AI 助手', createdAt: '2026-10-05 11:20:00'
  },
  {
    id: 'kb6', title: '代码 Review 提示词（嵌入式 C）', category: 'AI 提示词库', pinned: false,
    summary: '嵌入式 C 代码走查提示词：内存、中断安全、风格三视角。',
    content: '模板：\n请以嵌入式评审视角检查以下 C 代码：1) 内存泄漏与越界 2) 中断上下文安全 3) 可移植性。\n输出：问题清单 + 修改建议 + 严重度分级。',
    sourceType: 'AI_SUMMARY', linkUrl: '', viewCount: 77, publisherName: 'AI 助手', createdAt: '2026-10-08 10:00:00'
  },
  {
    id: 'kb7', title: '招新面试评分表（2026 秋修订版）', category: '其他', pinned: false,
    summary: '各组统一的面试评分维度与打分口径。',
    content: '评分维度：基础 40% / 项目 30% / 表达 20% / 态度 10%。每档打分锚点见正文附录。',
    sourceType: 'ADMIN_UPLOAD', linkUrl: '', viewCount: 45, publisherName: '李明', createdAt: '2026-10-10 09:00:00'
  }
]

export const kbComments: MockKbComment[] = [
  { id: 'kc1', itemId: 'kb1', authorName: '王雨桐', authorLoginName: '2023319024', content: '跟着做完点灯了，中断那节再补个截图就更好了', createdAt: '2026-10-09 21:14:00' },
  { id: 'kc2', itemId: 'kb1', authorName: '赵天宇', authorLoginName: '2023319001', content: '已补截图到工程目录 readme', createdAt: '2026-10-10 09:02:00' },
  { id: 'kc3', itemId: 'kb5', authorName: '李明轩', authorLoginName: '2023319003', content: '用这个模板拆了电赛题，任务树直接能用', createdAt: '2026-10-10 19:40:00' }
]

let kbSeq = 8
let commentSeq = 4

export function nextKbId(): string {
  return 'kb' + kbSeq++
}
export function nextCommentId(): string {
  return 'kc' + commentSeq++
}
