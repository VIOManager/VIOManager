/**
 * 技术交流 mock 处理器（T01–T04 用户侧 + T10–T12 管理侧）
 * 契约：《.agentdocs/plans/2026-10-08/技术交流-接口清单.md》（草案待 lzh 确认，以 Apifox 录入为准）
 * 错误码：9001 推送不存在或已撤回 / 9002 无权执行管理操作（清单 §2）
 * 注册顺序敏感：/tags 必须先于 /:id 注册
 */
import { registerMock, MockBizError, markIdem, requireAdmin } from './registry'
import { techPosts, findTechPost, allocTechId, techTags, type MockTechPost } from './tech-data'

/** 创建/撤回要求幂等键（清单 T10/T12）；T03 收藏、T11 置顶天然幂等不要键 */
function requireIdem(key: string): void {
  if (!key) throw new MockBizError(1001, '缺少 X-Idempotency-Key 请求头')
  if (!markIdem(key)) throw new MockBizError(1005, '请勿重复提交')
}

/** 列表项（正文与链接不随列表下发，详情页再取） */
function toListItem(p: MockTechPost) {
  return {
    id: p.id, title: p.title, summary: p.summary, category: p.category, contentType: p.contentType,
    sourceType: p.sourceType, sourceName: p.sourceName, pinned: p.pinned, publishAt: p.publishAt,
    viewCount: p.viewCount, collectCount: p.collectCount, collected: p.collected, tags: p.tags.slice()
  }
}

// T01 推送列表：category/tag/keyword 筛选 + 分页；默认发布时间倒序、置顶置前
registerMock('get', '/api/tech-posts', (ctx) => {
  const category = ctx.query.category ?? ''
  const tag = ctx.query.tag ?? ''
  const keyword = ctx.query.keyword ?? ''
  const page = Number(ctx.query.page ?? '1')
  const size = Number(ctx.query.size ?? '20')
  let list: MockTechPost[] = []
  for (let i = 0; i < techPosts.length; i++) {
    const p = techPosts[i]
    if (p.withdrawn) continue
    if (category && category !== 'ALL' && p.category !== category) continue
    if (tag && p.tags.indexOf(tag) < 0) continue
    if (keyword && p.title.indexOf(keyword) < 0 && p.summary.indexOf(keyword) < 0) continue
    list.push(p)
  }
  list.sort((a, b) => {
    if (a.pinned !== b.pinned) return a.pinned ? -1 : 1
    return Number(b.id) - Number(a.id)
  })
  const total = list.length
  const start = (page - 1) * size
  const slice = list.slice(start, start + size)
  const items = []
  for (let i = 0; i < slice.length; i++) items.push(toListItem(slice[i]))
  return { list: items, total, page, size }
})

// T04 订阅标签列表（固定路径，先于 /:id 注册）
registerMock('get', '/api/tech-posts/tags', () => techTags.slice())

// T02 推送详情：浏览 +1；撤回品 9001
registerMock('get', '/api/tech-posts/:id', (ctx) => {
  const p = findTechPost(ctx.params.id)
  if (!p) throw new MockBizError(9001, '推送不存在或已撤回')
  p.viewCount++
  return {
    ...toListItem(p),
    content: p.content.slice(),
    linkUrl: p.linkUrl
  }
})

// T03 收藏/取消：body collected，收藏关系唯一约束天然幂等，清单明确不要幂等头
registerMock('post', '/api/tech-posts/:id/collect', (ctx) => {
  const p = findTechPost(ctx.params.id)
  if (!p) throw new MockBizError(9001, '推送不存在或已撤回')
  const collected = ctx.body.collected === true
  if (p.collected !== collected) {
    p.collected = collected
    p.collectCount += collected ? 1 : -1
  }
  return { collected }
})

// T10 管理员手工录入（幂等 + 9002）。body：title summary content contentType category tags linkUrl pinned
registerMock('post', '/api/tech-posts', (ctx) => {
  requireIdem(ctx.idemKey)
  requireAdmin()
  const title = String(ctx.body.title ?? '').trim()
  const summary = String(ctx.body.summary ?? '').trim()
  const contentType = String(ctx.body.contentType ?? 'ARTICLE')
  const category = String(ctx.body.category ?? 'OTHER')
  if (!title || !summary) throw new MockBizError(1001, '标题与摘要不能为空')
  if (contentType === 'LINK' && !String(ctx.body.linkUrl ?? '').trim()) {
    throw new MockBizError(1001, '外链类型必须填写链接地址')
  }
  const rawTags = ctx.body.tags
  const tags: string[] = []
  if (Array.isArray(rawTags)) {
    for (let i = 0; i < rawTags.length; i++) tags.push(String(rawTags[i]))
  }
  const rawContent = String(ctx.body.content ?? '').trim()
  const content: string[] = []
  if (rawContent) {
    const paras = rawContent.split(/\n\s*\n/)
    for (let i = 0; i < paras.length; i++) {
      if (paras[i].trim()) content.push(paras[i].trim())
    }
  }
  const id = allocTechId()
  const today = new Date()
  const mm = String(today.getMonth() + 1).padStart(2, '0')
  const dd = String(today.getDate()).padStart(2, '0')
  techPosts.push({
    id, title, summary, category,
    contentType: contentType as MockTechPost['contentType'],
    sourceType: 'ADMIN', sourceName: '管理员录入',
    pinned: ctx.body.pinned === true,
    publishAt: mm + '-' + dd + ' ' + '09:00',
    viewCount: 1, collectCount: 0, collected: false, tags,
    content, linkUrl: String(ctx.body.linkUrl ?? ''), withdrawn: false
  })
  return { id }
})

// T11 置顶/取消置顶（天然幂等，body 带目标态；仅管理员）
registerMock('put', '/api/tech-posts/:id/pin', (ctx) => {
  requireAdmin()
  const p = findTechPost(ctx.params.id)
  if (!p) throw new MockBizError(9001, '推送不存在或已撤回')
  p.pinned = ctx.body.pinned === true
  return { pinned: p.pinned }
})

// T12 撤回推送（逻辑删，幂等 + 9001/9002）
registerMock('post', '/api/tech-posts/:id/withdraw', (ctx) => {
  requireIdem(ctx.idemKey)
  requireAdmin()
  const p = findTechPost(ctx.params.id)
  if (!p) throw new MockBizError(9001, '推送不存在或已撤回')
  p.withdrawn = true
  return { withdrawn: true }
})
