/**
 * 知识库 mock 数据层（接口层在 ./kb.ts）
 * lzh 2026-10-14 拍板简化：条目 = 外部链接或上传文件二选一，分类与正文暂不做
 * fileId 种子用 f101 起（mock/files.ts 新上传从 f1 自增，不会撞号）
 * TODO(联调)：后端知识库清单就绪后删除本文件
 */

export interface MockKbItem {
  id: string
  title: string
  summary: string
  sourceType: 'LINK' | 'FILE'
  /** 外部链接（LINK 时有值） */
  linkUrl: string
  /** 文件服务 ID 与原始文件名（FILE 时有值） */
  fileId: string
  fileName: string
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
    id: 'kb1', title: 'STM32 HAL 库入门：从点灯到定时器中断', pinned: true,
    summary: '电控组新人入门系列录像，CubeMX 配置 + HAL 点灯 + 中断入门。',
    sourceType: 'FILE', linkUrl: '', fileId: 'f101', fileName: 'stm32-hal-入门系列.mp4',
    viewCount: 86, publisherName: '陈志远', createdAt: '2026-09-15 10:00:00'
  },
  {
    id: 'kb2', title: 'Python 爬虫合规与校内数据源清单', pinned: false,
    summary: '爬数据的边界在哪、校内哪些公开接口可用、robots 与频率控制。',
    sourceType: 'LINK', linkUrl: 'https://example.com/py-crawl-guide', fileId: '', fileName: '',
    viewCount: 152, publisherName: '李明', createdAt: '2026-09-20 14:30:00'
  },
  {
    id: 'kb3', title: '机械制图国标速查（GB/T 4458 摘要）', pinned: false,
    summary: '图纸幅面、比例、线型、尺寸标注的常用条款摘要。',
    sourceType: 'FILE', linkUrl: '', fileId: 'f102', fileName: '机械制图国标速查.pdf',
    viewCount: 64, publisherName: '赵天宇', createdAt: '2026-09-25 09:10:00'
  },
  {
    id: 'kb4', title: 'SolidWorks 装配体配合实战 10 例', pinned: false,
    summary: '同心/重合/距离/角度配合的典型用法与翻车点。',
    sourceType: 'LINK', linkUrl: 'https://example.com/sw-assembly-10', fileId: '', fileName: '',
    viewCount: 98, publisherName: '陈志远', createdAt: '2026-09-28 16:40:00'
  },
  {
    id: 'kb5', title: '赛题拆解提示词模板（数模/电赛通用）', pinned: true,
    summary: '让 AI 帮你把赛题拆成任务树的提示词模板，直接复制可用。',
    sourceType: 'FILE', linkUrl: '', fileId: 'f103', fileName: '赛题拆解提示词模板.pdf',
    viewCount: 210, publisherName: 'AI 助手', createdAt: '2026-10-05 11:20:00'
  },
  {
    id: 'kb6', title: '代码 Review 提示词（嵌入式 C）', pinned: false,
    summary: '嵌入式 C 代码走查提示词：内存、中断安全、风格三视角。',
    sourceType: 'FILE', linkUrl: '', fileId: 'f104', fileName: '嵌入式C走查提示词.pdf',
    viewCount: 77, publisherName: 'AI 助手', createdAt: '2026-10-08 10:00:00'
  },
  {
    id: 'kb7', title: '招新面试评分表（2026 秋修订版）', pinned: false,
    summary: '各组统一的面试评分维度与打分口径。',
    sourceType: 'FILE', linkUrl: '', fileId: 'f105', fileName: '招新面试评分表-2026秋.xlsx',
    viewCount: 45, publisherName: '李明', createdAt: '2026-10-10 09:00:00'
  }
]

export const kbComments: MockKbComment[] = [
  { id: 'kc1', itemId: 'kb1', authorName: '王雨桐', authorLoginName: '2023319024', content: '跟着看完录像了，中断那节再补个时间戳目录就更好了', createdAt: '2026-10-09 21:14:00' },
  { id: 'kc2', itemId: 'kb1', authorName: '赵天宇', authorLoginName: '2023319001', content: '已在工程目录补了章节索引', createdAt: '2026-10-10 09:02:00' },
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
