/**
 * 知识库接口（KB01–KB08 暂定）
 * 依据：PRD §3.14；lzh 2026-10-14 拍板先行简化——条目 = 外部链接或上传文件二选一，
 *    分类与正文暂不做，待正式需求加回
 * ⚠️ 暂定契约：知识库接口清单未出（9xxx 段预留给知识库与 AI），本文件按前端先行口径约定，
 *    待后端清单入库后对齐；TODO(联调)：以正式清单为准
 * 权限：发布/编辑/置顶/删除仅管理员（1003）；浏览/收藏/评论登录即可
 * 文件两段式：先走文件服务 apiUploadFile 拿 fileId/fileName，再带进本模块建条目（与佐证/材料同模式）
 */
import { del, download, get, post, postIdem, put } from './http'

/** 条目形态：外部链接 / 上传文件 */
export type KbSourceType = 'LINK' | 'FILE'

export interface KbItem {
  id: string
  title: string
  /** 一句话摘要（列表卡片展示，选填） */
  summary: string
  sourceType: KbSourceType
  /** 外部链接地址（sourceType=LINK 时有值） */
  linkUrl: string
  /** 文件服务 ID（sourceType=FILE 时有值） */
  fileId: string
  /** 原始文件名（FILE 时展示与下载兜底用） */
  fileName: string
  pinned: boolean
  viewCount: number
  favoriteCount: number
  commentCount: number
  publisherName: string
  createdAt: string
}

export interface KbDetail extends KbItem {
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

export function apiKbItems(q?: { keyword?: string }): Promise<KbItem[]> {
  return get('/api/kb/items', (q ?? {}) as Record<string, unknown>)
}

/** KB03 详情（浏览量 +1） */
export function apiKbDetail(id: string): Promise<KbDetail> {
  return get('/api/kb/items/' + id)
}

export interface CreateKbBody {
  title: string
  summary: string
  sourceType: KbSourceType
  /** LINK 时必填 */
  linkUrl?: string
  /** FILE 时必填（文件服务上传返回值） */
  fileId?: string
  fileName?: string
}

/** KB02 发布（仅管理员；幂等头） */
export function apiCreateKbItem(body: CreateKbBody): Promise<{ id: string }> {
  return postIdem('/api/kb/items', body, crypto.randomUUID())
}

export function apiUpdateKbItem(id: string, body: { title?: string; summary?: string; linkUrl?: string }): Promise<null> {
  return put('/api/kb/items/' + id, body)
}

/** 附件下载：走文件服务流下载（后端已实现 GET /api/files/{fileId}/download） */
export function apiDownloadKbFile(fileId: string, fileName: string): Promise<void> {
  return download('/api/files/' + fileId + '/download', fileName)
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
