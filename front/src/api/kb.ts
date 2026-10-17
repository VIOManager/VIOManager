/**
 * 知识库接口——已对齐后端权威契约（feature/kb，/api/kb/docs 系列）
 * 联调对齐四项（2026-10-15 随 feature/kb 提交，PR 中请 lzh 按正式清单复核）：
 *   ① 路径 items→docs；② 置顶/收藏对齐后端端点语义；③ 评论列表解包后端分页壳；
 *   ④ 发评论补 X-Idempotency-Key（全站写操作规范，原实现漏带）
 * 字段模型：lzh 2026-10-14 拍板简化（链接或文件二选一），后端已吸收，双方一致
 */
import { del, download, get, postIdem, put } from './http'

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

export async function apiKbItems(q?: { keyword?: string }): Promise<KbItem[]> {
  return get('/api/kb/docs', (q ?? {}) as Record<string, unknown>)
}

/** KB03 详情（浏览量 +1，含 favorited） */
export function apiKbDetail(id: string): Promise<KbDetail> {
  return get('/api/kb/docs/' + id)
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
  return postIdem('/api/kb/docs', body, crypto.randomUUID())
}

export function apiUpdateKbItem(id: string, body: { title?: string; summary?: string; linkUrl?: string }): Promise<null> {
  return put('/api/kb/docs/' + id, body)
}

/** 附件下载：走文件服务流下载（GET /api/files/{fileId}/download；KB_FILE 全员可下，2026-10-15 实测链路正常） */
export function apiDownloadKbFile(fileId: string, fileName: string): Promise<void> {
  return download('/api/files/' + fileId + '/download', fileName)
}

export function apiDeleteKbItem(id: string): Promise<null> {
  return del('/api/kb/docs/' + id)
}

/** KB06 置顶切换（仅管理员；后端为翻转语义，目标态由调用方按当前态取反保证一致） */
export function apiPinKbItem(id: string, _pinned: boolean): Promise<null> {
  return put('/api/kb/docs/' + id + '/top')
}

/**
 * KB07 收藏/取消收藏（登录；返回操作后状态）。
 * 后端为 collect/uncollect 两个端点，按调用方传入的当前态取反。
 */
export async function apiToggleKbFavorite(id: string, favorited: boolean): Promise<{ favorited: boolean }> {
  if (favorited) {
    await del('/api/kb/docs/' + id + '/collect')
  } else {
    await postIdem('/api/kb/docs/' + id + '/collect', {}, crypto.randomUUID())
  }
  return { favorited: !favorited }
}

/** 后端评论列表是分页壳 { list, total }，这里解包成数组（当前页数据量小，直取第一页） */
export async function apiKbComments(id: string): Promise<KbComment[]> {
  const page = await get<{ list: KbComment[] }>('/api/kb/docs/' + id + '/comments')
  return page.list
}

export function apiAddKbComment(id: string, content: string): Promise<{ id: string }> {
  // 写操作必须带幂等头（全站规范 §6，原实现漏带会被后端 1001 拦下）
  return postIdem('/api/kb/docs/' + id + '/comments', { content }, crypto.randomUUID())
}

/** 删评论：本人或管理员 */
export function apiDeleteKbComment(commentId: string): Promise<null> {
  return del('/api/kb/comments/' + commentId)
}
