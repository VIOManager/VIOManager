/**
 * 技术交流接口契约（页面侧唯一入口）
 * 契约：《.agentdocs/plans/2026-10-08/技术交流-接口清单.md》T01–T04 / T10–T12（草案待 lzh 确认）
 * 幂等口径（清单 §3/§4）：T10 录入、T12 撤回带 X-Idempotency-Key；
 *    T03 收藏、T11 置顶为状态型天然幂等，清单明确不要幂等头
 */
import { get, post, put, postIdem } from './http'
import type { PageResult } from './types'

/** 推送列表项（FeedPage 行式列表用） */
export interface TechPostItem {
  id: string
  title: string
  summary: string
  /** 分类字典值（FEED_CATEGORIES 的 code） */
  category: string
  contentType: 'ARTICLE' | 'VIDEO' | 'LINK'
  sourceType: 'ADMIN' | 'RSS' | 'AI'
  sourceName: string
  pinned: boolean
  publishAt: string
  viewCount: number
  collectCount: number
  collected: boolean
  tags: string[]
}

/** 推送详情（含正文段落或外链地址） */
export interface TechPostDetail extends TechPostItem {
  content: string[]
  linkUrl: string
}

/** T01 推送列表 */
export function apiTechList(params: {
  category?: string
  tag?: string
  keyword?: string
  page?: number
  size?: number
}): Promise<PageResult<TechPostItem>> {
  return get('/api/tech-posts', params as Record<string, unknown>)
}

/** T02 推送详情（服务端浏览计数 +1） */
export function apiTechDetail(id: string): Promise<TechPostDetail> {
  return get('/api/tech-posts/' + encodeURIComponent(id))
}

/** T03 收藏/取消（天然幂等，无幂等头） */
export function apiCollectTechPost(id: string, collected: boolean): Promise<{ collected: boolean }> {
  return post('/api/tech-posts/' + encodeURIComponent(id) + '/collect', { collected })
}

/** T04 订阅标签列表 */
export function apiTechTags(): Promise<string[]> {
  return get('/api/tech-posts/tags')
}

/** T10 管理员录入推送（幂等：一张表单一个键，失败重试复用同键） */
export function apiCreateTechPost(
  data: {
    title: string
    summary: string
    content: string
    contentType: string
    category: string
    tags: string[]
    linkUrl: string
    pinned: boolean
  },
  idempotencyKey: string
): Promise<{ id: string }> {
  return postIdem('/api/tech-posts', data, idempotencyKey)
}

/** T11 置顶/取消置顶（天然幂等，无幂等头） */
export function apiPinTechPost(id: string, pinned: boolean): Promise<{ pinned: boolean }> {
  return put('/api/tech-posts/' + encodeURIComponent(id) + '/pin', { pinned })
}

/** T12 撤回推送（逻辑删，幂等） */
export function apiWithdrawTechPost(id: string, idempotencyKey: string): Promise<{ withdrawn: boolean }> {
  return postIdem('/api/tech-posts/' + encodeURIComponent(id) + '/withdraw', {}, idempotencyKey)
}
