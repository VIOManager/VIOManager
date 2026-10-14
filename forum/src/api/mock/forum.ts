/**
 * 论坛帖子 mock 处理器（F01–F10）
 * TODO(联调-论坛清单)：论坛服务接口清单未出，路径 /api/forum/** 与错误码均为前端预设，
 *    清单定稿后对齐（帖子不存在暂用通用 1004，管理操作沿用 9002 口径）
 * 注册顺序敏感：/hot、/stats 必须先于 /:id 注册（见 registry.ts 头注）
 */
import { registerMock, MockBizError, markIdem } from './registry'
import { topics, findTopic, allocTopicId, nowText, type MockTopic } from './forum-data'
import { replies, repliesOf, findReply, allocReplyId } from './forum-replies'

/** 发帖/回帖统一走幂等：没带键 1001，重复键 1005（规范 §6） */
function requireIdem(key: string): void {
  if (!key) throw new MockBizError(1001, '缺少 X-Idempotency-Key 请求头')
  if (!markIdem(key)) throw new MockBizError(1005, '请勿重复提交')
}

/** 热度分：回复为主、点赞次之、浏览兜底（演示口径；真实服务为 Redis 加权+时间衰减） */
function hotScore(t: MockTopic): number {
  return t.replyCount * 3 + t.likeCount * 2 + Math.floor(t.viewCount / 50)
}

/** 列表项（脱去正文与附件，列表页轻载） */
function toListItem(t: MockTopic) {
  return {
    id: t.id, board: t.board, title: t.title, author: t.author, authorGroup: t.authorGroup,
    tags: t.tags.slice(), replyCount: t.replyCount, viewCount: t.viewCount, likeCount: t.likeCount,
    lastActiveAt: t.lastActiveAt, lastReplyBy: t.lastReplyBy, pinned: t.pinned, hot: t.hot
  }
}

// F01 帖子列表：board 可空、sort=latest/hot、keyword 模糊（标题与标签）、分页
registerMock('get', '/api/forum/topics', (ctx) => {
  const board = ctx.query.board ?? ''
  const sort = ctx.query.sort ?? 'latest'
  const keyword = ctx.query.keyword ?? ''
  const page = Number(ctx.query.page ?? '1')
  const size = Number(ctx.query.size ?? '10')
  // 第一步：过滤版块与关键词
  let list: MockTopic[] = []
  for (let i = 0; i < topics.length; i++) {
    const t = topics[i]
    if (board && board !== 'ALL' && t.board !== board) continue
    if (keyword) {
      let hit = t.title.indexOf(keyword) >= 0
      for (let j = 0; j < t.tags.length && !hit; j++) {
        if (t.tags[j].indexOf(keyword) >= 0) hit = true
      }
      if (!hit) continue
    }
    list.push(t)
  }
  // 第二步：排序（置顶永远在前，这是论坛铁律）
  list.sort((a, b) => {
    if (a.pinned !== b.pinned) return a.pinned ? -1 : 1
    if (sort === 'hot') return hotScore(b) - hotScore(a)
    // latest：按 id 降序（新帖 id 大）
    return Number(b.id) - Number(a.id)
  })
  // 第三步：切页
  const total = list.length
  const start = (page - 1) * size
  const slice = list.slice(start, start + size)
  const items = []
  for (let i = 0; i < slice.length; i++) items.push(toListItem(slice[i]))
  return { list: items, total, page, size }
})

// F02 今日热议 top5（固定路径，先于 /:id 注册）
registerMock('get', '/api/forum/topics/hot', () => {
  const sorted = topics.slice().sort((a, b) => hotScore(b) - hotScore(a))
  const top = sorted.slice(0, 5)
  const items = []
  for (let i = 0; i < top.length; i++) {
    items.push({ id: top[i].id, title: top[i].title, author: top[i].author })
  }
  return items
})

// F03 社区运行状况
registerMock('get', '/api/forum/topics/stats', () => {
  let replyTotal = 0
  for (let i = 0; i < topics.length; i++) replyTotal += topics[i].replyCount
  return { memberCount: 186, topicCount: topics.length, replyCount: replyTotal }
})

// F04 帖子详情（浏览 +1）；作者统计顺带算好（右栏"关于作者"用）
registerMock('get', '/api/forum/topics/:id', (ctx) => {
  const t = findTopic(ctx.params.id)
  if (!t) throw new MockBizError(1004, '帖子不存在或已删除')
  t.viewCount++
  let postCount = 0
  let likeTotal = 0
  for (let i = 0; i < topics.length; i++) {
    if (topics[i].author === t.author) {
      postCount++
      likeTotal += topics[i].likeCount
    }
  }
  return {
    id: t.id, board: t.board, title: t.title, author: t.author, authorGroup: t.authorGroup,
    tags: t.tags.slice(), replyCount: t.replyCount, viewCount: t.viewCount, likeCount: t.likeCount,
    liked: t.liked, collected: t.collected, pinned: t.pinned, publishAt: t.publishAt,
    body: t.body.slice(), attach: t.attach.slice(),
    authorStats: { postCount, likeTotal, archivedCount: Math.floor(likeTotal / 30) }
  }
})

// F05 楼层列表（分页参数兼容，演示数据量全量返回；引用信息在服务端拼好）
registerMock('get', '/api/forum/topics/:id/replies', (ctx) => {
  const t = findTopic(ctx.params.id)
  if (!t) throw new MockBizError(1004, '帖子不存在或已删除')
  const page = Number(ctx.query.page ?? '1')
  const size = Number(ctx.query.size ?? '50')
  const all = repliesOf(t.id)
  const items = []
  for (let i = 0; i < all.length; i++) {
    const r = all[i]
    let quote = null
    if (r.quoteReplyId) {
      const q = findReply(r.quoteReplyId)
      if (q) quote = { author: q.author, floorNo: q.floorNo, text: q.text.slice(0, 60) }
    }
    items.push({
      id: r.id, floorNo: r.floorNo, author: r.author, authorGroup: r.authorGroup,
      publishAt: r.publishAt, text: r.text, likeCount: r.likeCount, liked: r.liked, quote
    })
  }
  return { list: items, total: all.length, page, size }
})

// F06 发帖（幂等）。body：board title bodyText tags pinned
registerMock('post', '/api/forum/topics', (ctx) => {
  requireIdem(ctx.idemKey)
  const board = String(ctx.body.board ?? '')
  const title = String(ctx.body.title ?? '').trim()
  const bodyText = String(ctx.body.bodyText ?? '').trim()
  const pinned = ctx.body.pinned === true
  if (!board || !title || !bodyText) throw new MockBizError(1001, '版块、标题、正文都不能为空')
  if (pinned) requireAdmin(ctx)
  // tags 兼容数组和逗号分隔字符串两种入参
  const rawTags = ctx.body.tags
  const tags: string[] = []
  if (Array.isArray(rawTags)) {
    for (let i = 0; i < rawTags.length; i++) tags.push(String(rawTags[i]))
  } else if (typeof rawTags === 'string' && rawTags.trim()) {
    const parts = rawTags.split(/[,，]/)
    for (let i = 0; i < parts.length; i++) {
      if (parts[i].trim()) tags.push(parts[i].trim())
    }
  }
  // 正文按空行分段（发帖表单的约定，详情页按段渲染）
  const body: string[] = []
  const paras = bodyText.split(/\n\s*\n/)
  for (let i = 0; i < paras.length; i++) {
    if (paras[i].trim()) body.push(paras[i].trim())
  }
  const id = allocTopicId()
  topics.push({
    id, board: board as MockTopic['board'], title, author: ctx.loginName, authorGroup: '综合',
    tags, replyCount: 0, viewCount: 1, likeCount: 0, liked: false, collected: false,
    publishAt: nowText(), lastActiveAt: '刚刚', lastReplyBy: ctx.loginName, pinned, hot: false,
    body, attach: []
  })
  return { id }
})

// F07 回帖（幂等）。body：text quoteReplyId（可空）
registerMock('post', '/api/forum/topics/:id/replies', (ctx) => {
  requireIdem(ctx.idemKey)
  const t = findTopic(ctx.params.id)
  if (!t) throw new MockBizError(1004, '帖子不存在或已删除')
  const text = String(ctx.body.text ?? '').trim()
  if (!text) throw new MockBizError(1001, '回复内容不能为空')
  const quoteReplyId = ctx.body.quoteReplyId ? String(ctx.body.quoteReplyId) : null
  if (quoteReplyId) {
    const q = findReply(quoteReplyId)
    if (!q || q.topicId !== t.id) throw new MockBizError(1001, '引用的楼层不存在')
  }
  const floorNo = t.replyCount + 1
  const id = allocReplyId(t.id, floorNo)
  replies.push({
    id, topicId: t.id, floorNo, author: ctx.loginName, authorGroup: '综合',
    publishAt: nowText(), text, likeCount: 0, liked: false, quoteReplyId
  })
  t.replyCount = floorNo
  t.lastActiveAt = '刚刚'
  t.lastReplyBy = ctx.loginName
  return { id, floorNo }
})

// F08 帖子点赞/取消（状态型操作：body 带目标态，重复点无副作用，不需要幂等头）
registerMock('post', '/api/forum/topics/:id/like', (ctx) => {
  const t = findTopic(ctx.params.id)
  if (!t) throw new MockBizError(1004, '帖子不存在或已删除')
  const liked = ctx.body.liked === true
  if (t.liked === liked) return { liked }
  t.liked = liked
  t.likeCount += liked ? 1 : -1
  return { liked }
})

// F09 帖子收藏/取消（收藏关系唯一约束天然幂等，同技术交流 T03 口径）
registerMock('post', '/api/forum/topics/:id/collect', (ctx) => {
  const t = findTopic(ctx.params.id)
  if (!t) throw new MockBizError(1004, '帖子不存在或已删除')
  const collected = ctx.body.collected === true
  t.collected = collected
  return { collected }
})

// F10 楼层点赞/取消（同 F08 状态型口径）
registerMock('post', '/api/forum/replies/:id/like', (ctx) => {
  const r = findReply(ctx.params.id)
  if (!r) throw new MockBizError(1004, '楼层不存在')
  const liked = ctx.body.liked === true
  if (r.liked === liked) return { liked }
  r.liked = liked
  r.likeCount += liked ? 1 : -1
  return { liked }
})

/** F06 置顶发帖的管理员校验（requireAdmin 的本地包装，错误文案带上下文） */
function requireAdmin(ctx: { role: string }): void {
  if (ctx.role !== 'SUPER_ADMIN') {
    throw new MockBizError(9002, '无权执行管理操作（置顶仅管理员）')
  }
}
