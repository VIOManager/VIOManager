/**
 * 知识库接口（KB01–KB08 暂定）
 * 依据：PRD §3.14（分类浏览/收藏/评论；管理员发布/编辑/置顶/删除；内容来源=管理员上传/外部链接/AI 总结）
 * ⚠️ 暂定契约：知识库接口清单未出（9xxx 段预留给知识库与 AI），本文件按 PRD 先行约定，
 *    待后端清单入库后对齐；TODO(联调)：以正式清单为准
 * 分类走字典 DR01（KB_CATEGORY，系统配置里维护）；发布/编辑/置顶/删除仅管理员（1003）
 */
import { del, get, post, postIdem, put } from './http'

export type KbSourceType = 'ADMIN_UPLOAD' | 'LINK' | 'AI_SUMMARY'

export interface KbItem {
  id: string
  title: string
  category: string
  /** 摘要（列表卡片用） */
  summary: string
  sourceType: KbSourceType
  linkUrl: string
  pinned: boolean
  viewCount: number
  favoriteCount: number
  commentCount: number
  publisherName: string
  createdAt: string
}

export interface KbDetail extends KbItem {
  content: string
  /** 当前登录人是否已收藏 */
  favorited: boolean
}

export interface KbComment {
  id: string
  authorName: string
  content: string
  createdAt: string
  /** 当前登录人是否作者（删除按钮显隐用；后端仍校验） */
  mine: boolean
}

export function apiKbItems(q?: { category?: string; keyword?: string }): Promise<KbItem[]> {
  return get('/api/kb/items', (q ?? {}) as Record<string, unknown>)
}

/** KB03 详情（浏览量 +1） */
export function apiKbDetail(id: string): Promise<KbDetail> {
  return get('/api/kb/items/' + id)
}

/** KB02 发布（仅管理员；幂等头） */
export function apiCreateKbItem(body: { title: string; category: string; summary: string; content: string; sourceType: string; linkUrl?: string }): Promise<{ id: string }> {
  return postIdem('/api/kb/items', body, crypto.randomUUID())
}

export function apiUpdateKbItem(id: string, body: { title?: string; category?: string; summary?: string; content?: string; linkUrl?: string }): Promise<null> {
  return put('/api/kb/items/' + id, body)
}

export function apiDeleteKbItem(id: string): Promise<null> {
  return del('/api/kb/items/' + id)
}

/** KB06 置顶切换（仅管理员） */
export function apiPinKbItem(id: string, pinned: boolean): Promise<null> {
  return put('/api/kb/items/' + id + '/pin', { pinned })
}

/** KB07 收藏/取消收藏（登录；返回当前状态） */
export function apiToggleKbFavorite(id: string): Promise<{ favorited: boolean }> {
  return post('/api/kb/items/' + id + '/favorite', {})
}

export function apiKbComments(id: string): Promise<KbComment[]> {
  return get('/api/kb/items/' + id + '/comments')
}

export function apiAddKbComment(id: string, content: string): Promise<{ id: string }> {
  return post('/api/kb/items/' + id + '/comments', { content })
}

/** 删评论：本人或管理员 */
export function apiDeleteKbComment(commentId: string): Promise<null> {
  return del('/api/kb/comments/' + commentId)
}
