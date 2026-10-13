/**
 * 论坛帖子接口契约（页面侧唯一入口）
 * TODO(联调-论坛清单)：论坛服务接口清单尚未出稿，本文件路径/字段为前端预设，
 *    清单定稿录入 Apifox 后以 Apifox 为准（错误码段也待登记）
 * 幂等口径（规范 §6）：发帖/回帖带 X-Idempotency-Key；点赞/收藏为状态型天然幂等不带
 */
import { get, post, postIdem } from './http'
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
  publishAt: string
  body: string[]
  attach: { name: string; size: string; downloads: number }[]
  authorStats: { postCount: number; likeTotal: number; archivedCount: number }
}

/** 楼层项（引用信息由服务端拼好） */
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
}

/** 今日热议条目 */
export interface HotTopicItem {
  id: string
  title: string
  author: string
}

/** F01 帖子列表 */
export function apiTopicList(params: {
  board?: string
  sort?: 'latest' | 'hot'
  keyword?: string
  page?: number
  size?: number
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

/** F05 楼层列表 */
export function apiReplyList(topicId: string): Promise<PageResult<ReplyItem>> {
  return get('/api/forum/topics/' + encodeURIComponent(topicId) + '/replies')
}

/** F06 发帖（幂等：一张表单一个键，失败重试复用同键） */
export function apiCreateTopic(
  data: { board: string; title: string; bodyText: string; tags: string[]; pinned: boolean },
  idempotencyKey: string
): Promise<{ id: string }> {
  return postIdem('/api/forum/topics', data, idempotencyKey)
}

/** F07 回帖（幂等；quoteReplyId 为引用楼层，可空） */
export function apiCreateReply(
  topicId: string,
  data: { text: string; quoteReplyId: string | null },
  idempotencyKey: string
): Promise<{ id: string; floorNo: number }> {
  return postIdem('/api/forum/topics/' + encodeURIComponent(topicId) + '/replies', data, idempotencyKey)
}

/** F08 帖子点赞/取消（状态型，天然幂等） */
export function apiLikeTopic(id: string, liked: boolean): Promise<{ liked: boolean }> {
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
