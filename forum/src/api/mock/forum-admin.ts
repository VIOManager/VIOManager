/**
 * 论坛管理端 mock 处理器（F11–F14）——镜像 backend/vio-forum 管理端契约
 * 权限两档（超管+部门管理员，registry.requireAdmin 判定，越权 1003）；编辑/状态全走幂等键
 * 9102 主题不存在（HIDDEN 主题仍可按 id 操作——软删恢复不经过详情，M2d/M2e 口径）；
 * 9103 楼层不存在；非法 to 值 1001（2026-10-17 拍板，非 1006）
 */
import { registerMock, requireAdmin, MockBizError } from './registry'
import { findTopic } from './forum-data'
import { findReply } from './forum-replies'
import { requireIdem } from './forum'

// F11 管理员编辑主题（幂等）。body：title bodyText tags
registerMock('put', '/api/forum/topics/:id', (ctx) => {
  requireAdmin()
  requireIdem(ctx.idemKey)
  const t = findTopic(ctx.params.id)
  if (!t) throw new MockBizError(9102, '主题不存在或已隐藏')
  const title = String(ctx.body.title ?? '').trim()
  const bodyText = String(ctx.body.bodyText ?? '').trim()
  if (!title || !bodyText) throw new MockBizError(1001, '标题和正文都不能为空')
  const tags: string[] = Array.isArray(ctx.body.tags) ? ctx.body.tags.map(String) : []
  // 正文按空行分段（与 F06 发帖同约定，详情页按段渲染）
  const body: string[] = []
  const paras = bodyText.split(/\n\s*\n/)
  for (let i = 0; i < paras.length; i++) {
    if (paras[i].trim()) body.push(paras[i].trim())
  }
  t.title = title
  t.body = body
  t.tags = tags
  return { id: t.id, title }
})

// F12 管理员设置主题状态（幂等）。to 白名单 NORMAL/LOCKED/HIDDEN——HIDDEN 即软删：
// 列表/详情/楼层/点赞对学生与管理员都不可见（9102），恢复只能按 id 走本接口
registerMock('post', '/api/forum/topics/:id/status', (ctx) => {
  requireAdmin()
  requireIdem(ctx.idemKey)
  const t = findTopic(ctx.params.id)
  if (!t) throw new MockBizError(9102, '主题不存在或已隐藏')
  const to = String(ctx.body.to ?? '')
  if (to !== 'NORMAL' && to !== 'LOCKED' && to !== 'HIDDEN') {
    throw new MockBizError(1001, 'to 取值不合法：仅 NORMAL / LOCKED / HIDDEN')
  }
  t.status = to
  return { id: t.id, status: to }
})

// F13 管理员编辑楼层（幂等）：覆盖原文并置 edited 标记透出
registerMock('put', '/api/forum/replies/:id', (ctx) => {
  requireAdmin()
  requireIdem(ctx.idemKey)
  const r = findReply(ctx.params.id)
  if (!r) throw new MockBizError(9103, '楼层不存在或已隐藏')
  const text = String(ctx.body.text ?? '').trim()
  if (!text) throw new MockBizError(1001, '楼层内容不能为空')
  r.text = text
  r.edited = true
  return { id: r.id, edited: true }
})

// F14 管理员设置楼层状态（幂等）。to 白名单 NORMAL/HIDDEN——HIDDEN 学生侧占位、管理员原文
registerMock('post', '/api/forum/replies/:id/status', (ctx) => {
  requireAdmin()
  requireIdem(ctx.idemKey)
  const r = findReply(ctx.params.id)
  if (!r) throw new MockBizError(9103, '楼层不存在或已隐藏')
  const to = String(ctx.body.to ?? '')
  if (to !== 'NORMAL' && to !== 'HIDDEN') {
    throw new MockBizError(1001, 'to 取值不合法：仅 NORMAL / HIDDEN')
  }
  r.status = to
  return { id: r.id, status: to }
})
