/**
 * 论坛帖子接口契约（页面侧唯一入口）
 * 契约基准：backend/vio-forum 控制器与 VO（F01-F10 已实现；错误码 9100-9199，ForumErrorCode）
 *   F11-F14 管理端编辑/状态接口随两级评论落地后在此追加
 * 幂等口径（规范 §6）：发帖/回帖带 X-Idempotency-Key；点赞/收藏为状态型天然幂等不带
 */
import { get, post, postIdem, putIdem } from './http'
import type { PageResult } from './types'
import type { BoardCode } from '@/types'

/** 帖子列表项（HomePage 行式列表用，不含正文） */
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
  lastReplyBy: string
  pinned: boolean
  /** NORMAL / LOCKED / HIDDEN（管理员 includeHidden 列表用，常规列表恒前两态） */
  status: string
  hot: boolean
}

/** 帖子详情（TopicPage 主楼用） */
export interface TopicDetail {
  id: string
  board: BoardCode
  title: string
  author: string
  authorGroup: string
  tags: string[]
  replyCount: number
  viewCount: number
  likeCount: number
  liked: boolean
  collected: boolean
  pinned: boolean
  /** NORMAL / LOCKED / HIDDEN：LOCKED 时前端禁用回复框（后端 9101 兜底） */
  status: string
  publishAt: string
  body: string[]
  attach: { name: string; size: string; downloads: number }[]
  authorStats: { postCount: number; likeTotal: number; collectedCount: number }
}

/** 楼层项（引用信息由服务端拼好；children 为 V4 两级评论扩展） */
export interface ReplyItem {
  id: string
  floorNo: number
  author: string
  authorGroup: string
  publishAt: string
  text: string
  likeCount: number
  liked: boolean
  quote: { author: string; floorNo: number; text: string } | null
  /** 父评论 ID：一级为 null，二级指向一级（V4 两级封顶） */
  parentReplyId: string | null
  /** 二级评论（仅一级楼层携带，时间正序全量；二级自身恒为空数组） */
  children: ReplyItem[]
  /** NORMAL / HIDDEN（HIDDEN 学生侧占位文本由服务端下发） */
  status: string
  /** 管理员编辑标记（透明度） */
  edited: boolean
}

/** 今日热议条目 */
export interface HotTopicItem {
  id: string
  title: string
  author: string
}

/** F01 帖子列表（includeHidden：管理档专属，服务端强制鉴权——非两档角色传了也忽略） */
export function apiTopicList(params: {
  board?: string
  sort?: 'latest' | 'hot'
  keyword?: string
  page?: number
  size?: number
  includeHidden?: boolean
}): Promise<PageResult<TopicItem>> {
  return get('/api/forum/topics', params as Record<string, unknown>)
}

/** F02 今日热议 top5 */
export function apiHotTopics(): Promise<HotTopicItem[]> {
  return get('/api/forum/topics/hot')
}

/** F03 社区运行状况 */
export function apiForumStats(): Promise<{ memberCount: number; topicCount: number; replyCount: number }> {
  return get('/api/forum/topics/stats')
}

/** F04 帖子详情（服务端浏览计数 +1） */
export function apiTopicDetail(id: string): Promise<TopicDetail> {
  return get('/api/forum/topics/' + encodeURIComponent(id))
}

/** F05 楼层列表（一级分页、楼层号升序；二级挂 children 不进分页） */
export function apiReplyList(
  topicId: string,
  params?: { page?: number; size?: number }
): Promise<PageResult<ReplyItem>> {
  return get(
    '/api/forum/topics/' + encodeURIComponent(topicId) + '/replies',
    (params ?? {}) as Record<string, unknown>
  )
}

/** F06 发帖（幂等：一张表单一个键，失败重试复用同键） */
export function apiCreateTopic(
  data: { board: string; title: string; bodyText: string; tags: string[]; pinned: boolean },
  idempotencyKey: string
): Promise<{ id: string }> {
  return postIdem('/api/forum/topics', data, idempotencyKey)
}

/** F07 回帖（幂等；quoteReplyId 引用楼层 / parentReplyId 二级评论父级，均可空） */
export function apiCreateReply(
  topicId: string,
  data: { text: string; quoteReplyId: string | null; parentReplyId?: string | null },
  idempotencyKey: string
): Promise<{ id: string; floorNo: number }> {
  return postIdem('/api/forum/topics/' + encodeURIComponent(topicId) + '/replies', data, idempotencyKey)
}

/** F08 帖子点赞/取消（状态型，天然幂等；likeCount 服务端权威计数） */
export function apiLikeTopic(id: string, liked: boolean): Promise<{ liked: boolean; likeCount: number }> {
  return post('/api/forum/topics/' + encodeURIComponent(id) + '/like', { liked })
}

/** F09 帖子收藏/取消（状态型，天然幂等） */
export function apiCollectTopic(id: string, collected: boolean): Promise<{ collected: boolean }> {
  return post('/api/forum/topics/' + encodeURIComponent(id) + '/collect', { collected })
}

/** F10 楼层点赞/取消（状态型，天然幂等） */
export function apiLikeReply(replyId: string, liked: boolean): Promise<{ liked: boolean }> {
  return post('/api/forum/replies/' + encodeURIComponent(replyId) + '/like', { liked })
}

/* ── F11-F14 管理档（超管+部门管理员两档同权，学生 1003；编辑/状态均带幂等键） ── */

/** F11 编辑主题（标题/正文/标签；换版块不在常规编辑范围） */
export function apiAdminUpdateTopic(
  id: string,
  data: { title: string; bodyText: string; tags: string[] },
  idempotencyKey: string
): Promise<{ id: string; title: string }> {
  return putIdem('/api/forum/topics/' + encodeURIComponent(id), data, idempotencyKey)
}

/** F12 主题状态（删除=HIDDEN 软删可恢复 / 恢复=NORMAL / 锁定解锁=LOCKED⇄NORMAL；非法值 1001） */
export function apiAdminSetTopicStatus(
  id: string,
  to: 'NORMAL' | 'LOCKED' | 'HIDDEN',
  idempotencyKey: string
): Promise<{ id: string; status: string }> {
  return postIdem('/api/forum/topics/' + encodeURIComponent(id) + '/status', { to }, idempotencyKey)
}

/** F13 编辑评论（管理员覆盖原文，edited=1 透出） */
export function apiAdminUpdateReply(
  id: string,
  text: string,
  idempotencyKey: string
): Promise<{ id: string; edited: boolean }> {
  return putIdem('/api/forum/replies/' + encodeURIComponent(id), { text }, idempotencyKey)
}

/** F14 评论状态（删除=HIDDEN 学生占位/管理员原文；恢复=NORMAL；非法值 1001） */
export function apiAdminSetReplyStatus(
  id: string,
  to: 'NORMAL' | 'HIDDEN',
  idempotencyKey: string
): Promise<{ id: string; status: string }> {
  return postIdem('/api/forum/replies/' + encodeURIComponent(id) + '/status', { to }, idempotencyKey)
}
