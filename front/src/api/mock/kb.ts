/**
 * 知识库 mock 接口层（KB01–KB08，数据在 ./kb-data）
 * lzh 2026-10-14 拍板简化：条目 = 外部链接或上传文件二选一（分类/正文暂不做）
 * 2026-10-16 路径对齐真后端契约：/api/kb/items 族 → /api/kb/docs 族，置顶改 PUT top 翻转语义，
 *   收藏拆 collect/uncollect 两端点，评论列表回分页壳 {list,total}（此前 mock 还挂旧路径，联调对齐后一直空列表）
 * 权限：发布/编辑/置顶/删除仅管理员（1003）；浏览/收藏/评论登录即可；删评论=本人或管理员
 * 列表排序：置顶优先，其余按发布时间倒序
 * TODO(联调)：后端知识库清单（9xxx）就绪后删除本文件并对齐 api/kb.ts
 */
import { fail, ok, registerMock } from './registry'
import { findMockUserByToken } from './auth'
import { nowText, usedIdemKeys } from './approval-data'
import { kbComments, kbFavorites, kbItems, nextCommentId, nextKbId } from './kb-data'
import type { MockKbItem } from './kb-data'

function adminOnly(token: string): boolean {
  const u = findMockUserByToken(token)
  return !!u && u.role !== 'STUDENT'
}

function findItem(id: string): MockKbItem | null {
  for (const it of kbItems) {
    if (it.id === id) return it
  }
  return null
}

function favCount(itemId: string): number {
  return (kbFavorites[itemId] ?? []).length
}

function commentCount(itemId: string): number {
  let n = 0
  for (const c of kbComments) {
    if (c.itemId === itemId) n++
  }
  return n
}

function toListItem(it: MockKbItem) {
  return {
    id: it.id, title: it.title, summary: it.summary,
    sourceType: it.sourceType, linkUrl: it.linkUrl, fileId: it.fileId, fileName: it.fileName,
    pinned: it.pinned,
    viewCount: it.viewCount, favoriteCount: favCount(it.id), commentCount: commentCount(it.id),
    publisherName: it.publisherName, createdAt: it.createdAt
  }
}

registerMock({
  method: 'get',
  path: '/api/kb/docs',
  reply: (ctx) => {
    const keyword = String(ctx.query.keyword ?? '').trim()
    const out: MockKbItem[] = []
    for (const it of kbItems) {
      if (keyword && it.title.indexOf(keyword) < 0 && it.summary.indexOf(keyword) < 0) continue
      out.push(it)
    }
    // 置顶优先，其余按发布时间倒序
    out.sort((a, b) => {
      if (a.pinned !== b.pinned) return a.pinned ? -1 : 1
      return a.createdAt < b.createdAt ? 1 : -1
    })
    return ok(out.map(toListItem))
  }
})

registerMock({
  method: 'get',
  path: '/api/kb/docs/:id',
  reply: (ctx) => {
    const it = findItem(ctx.params.id)
    if (!it) return fail(1004, '内容不存在')
    it.viewCount++
    const u = findMockUserByToken(ctx.token)
    const favs = kbFavorites[it.id] ?? []
    return ok({ ...toListItem(it), favorited: !!u && favs.indexOf(u.loginName) >= 0 })
  }
})

registerMock({
  method: 'post',
  path: '/api/kb/docs',
  reply: (ctx) => {
    if (!adminOnly(ctx.token)) return fail(1003, '仅管理员可发布')
    if (!ctx.idemKey) return fail(1001, '缺少幂等键（X-Idempotency-Key）')
    if (usedIdemKeys.has(ctx.idemKey)) return fail(1005, '请勿重复提交')
    const title = String(ctx.body.title ?? '').trim()
    if (!title) return fail(1001, '标题不能为空')
    const sourceType = String(ctx.body.sourceType ?? '') === 'FILE' ? 'FILE' : 'LINK'
    const linkUrl = String(ctx.body.linkUrl ?? '').trim()
    const fileId = String(ctx.body.fileId ?? '')
    const fileName = String(ctx.body.fileName ?? '')
    // 形态二选一：链接必填地址；文件必带文件服务上传返回的 fileId 与原始文件名
    if (sourceType === 'LINK' && !linkUrl) return fail(1001, '外部链接地址不能为空')
    if (sourceType === 'FILE' && (!fileId || !fileName)) return fail(1001, '文件条目缺少文件信息，请先上传文件')
    usedIdemKeys.add(ctx.idemKey)
    const u = findMockUserByToken(ctx.token)
    const it: MockKbItem = {
      id: nextKbId(), title,
      summary: String(ctx.body.summary ?? ''),
      sourceType, linkUrl, fileId: sourceType === 'FILE' ? fileId : '', fileName: sourceType === 'FILE' ? fileName : '',
      pinned: false, viewCount: 0,
      publisherName: u ? u.realName : '管理员', createdAt: nowText()
    }
    kbItems.unshift(it)
    return ok({ id: it.id })
  }
})

registerMock({
  method: 'put',
  path: '/api/kb/docs/:id',
  reply: (ctx) => {
    if (!adminOnly(ctx.token)) return fail(1003, '仅管理员可编辑')
    const it = findItem(ctx.params.id)
    if (!it) return fail(1004, '内容不存在')
    if (ctx.body.title) it.title = String(ctx.body.title).trim()
    if (ctx.body.summary !== undefined) it.summary = String(ctx.body.summary)
    if (ctx.body.linkUrl !== undefined) it.linkUrl = String(ctx.body.linkUrl)
    return ok(null)
  }
})

registerMock({
  method: 'delete',
  path: '/api/kb/docs/:id',
  reply: (ctx) => {
    if (!adminOnly(ctx.token)) return fail(1003, '仅管理员可删除')
    const it = findItem(ctx.params.id)
    if (!it) return fail(1004, '内容不存在')
    kbItems.splice(kbItems.indexOf(it), 1)
    delete kbFavorites[it.id]
    for (let i = kbComments.length - 1; i >= 0; i--) {
      if (kbComments[i].itemId === it.id) kbComments.splice(i, 1)
    }
    return ok(null)
  }
})

registerMock({
  method: 'put',
  path: '/api/kb/docs/:id/top',
  reply: (ctx) => {
    if (!adminOnly(ctx.token)) return fail(1003, '仅管理员可置顶')
    const it = findItem(ctx.params.id)
    if (!it) return fail(1004, '内容不存在')
    // 对齐真后端翻转语义：PUT top 一次翻一次，不看 body
    it.pinned = !it.pinned
    return ok({ pinned: it.pinned })
  }
})

/** KB07 收藏（collect 端点；取消走 DELETE 同路径） */
registerMock({
  method: 'post',
  path: '/api/kb/docs/:id/collect',
  reply: (ctx) => {
    const it = findItem(ctx.params.id)
    if (!it) return fail(1004, '内容不存在')
    const u = findMockUserByToken(ctx.token)
    if (!u) return fail(1002, '登录已过期')
    const favs = kbFavorites[it.id] ?? (kbFavorites[it.id] = [])
    if (favs.indexOf(u.loginName) < 0) favs.push(u.loginName)
    return ok({ favorited: true })
  }
})

/** KB07 取消收藏（uncollect = DELETE collect） */
registerMock({
  method: 'delete',
  path: '/api/kb/docs/:id/collect',
  reply: (ctx) => {
    const it = findItem(ctx.params.id)
    if (!it) return fail(1004, '内容不存在')
    const u = findMockUserByToken(ctx.token)
    if (!u) return fail(1002, '登录已过期')
    const favs = kbFavorites[it.id] ?? []
    const i = favs.indexOf(u.loginName)
    if (i >= 0) favs.splice(i, 1)
    return ok({ favorited: false })
  }
})

registerMock({
  method: 'get',
  path: '/api/kb/docs/:id/comments',
  reply: (ctx) => {
    const u = findMockUserByToken(ctx.token)
    const isAdmin = !!u && u.role !== 'STUDENT'
    const out = []
    for (const c of kbComments) {
      if (c.itemId !== ctx.params.id) continue
      out.push({ id: c.id, authorName: c.authorName, content: c.content, createdAt: c.createdAt, mine: !!u && (u.loginName === c.authorLoginName || isAdmin) })
    }
    out.sort((a, b) => (a.createdAt < b.createdAt ? -1 : 1))
    // 对齐真后端分页壳 {list,total}（前端 apiKbComments 解包 .list）
    return ok({ list: out, total: out.length })
  }
})

registerMock({
  method: 'post',
  path: '/api/kb/docs/:id/comments',
  reply: (ctx) => {
    const it = findItem(ctx.params.id)
    if (!it) return fail(1004, '内容不存在')
    const u = findMockUserByToken(ctx.token)
    if (!u) return fail(1002, '登录已过期')
    const content = String(ctx.body.content ?? '').trim()
    if (!content) return fail(1001, '评论内容不能为空')
    const c = { id: nextCommentId(), itemId: it.id, authorName: u.realName, authorLoginName: u.loginName, content, createdAt: nowText() }
    kbComments.push(c)
    return ok({ id: c.id })
  }
})

registerMock({
  method: 'delete',
  path: '/api/kb/comments/:id',
  reply: (ctx) => {
    const u = findMockUserByToken(ctx.token)
    if (!u) return fail(1002, '登录已过期')
    for (const c of kbComments) {
      if (c.id !== ctx.params.id) continue
      const isAdmin = u.role !== 'STUDENT'
      if (c.authorLoginName !== u.loginName && !isAdmin) return fail(1003, '仅本人或管理员可删除评论')
      kbComments.splice(kbComments.indexOf(c), 1)
      return ok(null)
    }
    return fail(1004, '评论不存在')
  }
})
