/**
 * 论坛站全局类型
 * 对应：《微服务拆分与论坛-架构调整方案》§5.1 论坛功能范围
 */
/** 版块：管理员维护（当前内置四个，可扩展） */
export type BoardCode = 'GENERAL' | 'EE' | 'MODELING' | 'AI'

/** 帖子/推送通用列表项（骨架阶段静态演示用，字段以接口清单为准） */
export interface TopicItem {
  id: string
  board: BoardCode
  title: string
  author: string
  authorGroup: string
  tags: string[]
  replyCount: number
  viewCount: number
  likeCount: number
  lastActiveAt: string
  pinned: boolean
  hot: boolean
}
