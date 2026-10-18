/**
 * 论坛帖子 mock 处理器（F01–F14）——镜像 backend/vio-forum 契约
 * 错误码对齐 ForumErrorCode：9101 锁定禁回 / 9102 主题不存在 / 9103 楼层不存在 /
 * 9105 父评论无效 / 9106 三级封顶；管理权限两档（超管+部门管理员）通用 1003
 * 注册顺序敏感：/hot、/stats 必须先于 /:id 注册（见 registry.ts 头注）
 */
import { registerMock, MockBizError, markIdem } from './registry'
import { topics, findTopic, allocTopicId, nowText, type MockTopic } from './forum-data'
import { replies, repliesOf, findReply, allocReplyId, type MockReply } from './forum-replies'
import type { ReplyItem } from '../forum'

/** 两档管理判定（与 registry.requireAdmin 同口径；查询侧 includeHidden/HIDDEN 展示用） */
function isAdminRole(role: string): boolean {
  return role === 'SUPER_ADMIN' || role === 'DEPT_ADMIN'
}

/** 发帖/回帖统一走幂等：没带键 1001，重复键 1005（规范 §6）；forum-admin.ts 管理端复用 */
export function requireIdem(key: string): void {
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
    lastActiveAt: t.lastActiveAt, lastReplyBy: t.lastReplyBy, pinned: t.pinned,
    status: t.status ?? 'NORMAL', hot: t.hot
  }
}

/** 楼层响应项：V4 两级字段全量下发（children 由 F05 挂到一级上）；HIDDEN 学生占位/管理员原文 */
function toReplyItem(r: MockReply, adminView: boolean): ReplyItem {
  let quote: ReplyItem['quote'] = null
  if (r.quoteReplyId) {
    const q = findReply(r.quoteReplyId)
    if (q) quote = { author: q.author, floorNo: q.floorNo, text: q.text.slice(0, 60) }
  }
  const status = r.status ?? 'NORMAL'
  return {
    id: r.id, floorNo: r.floorNo, author: r.author, authorGroup: r.authorGroup,
    publishAt: r.publishAt,
    text: status === 'HIDDEN' && !adminView ? '该楼层已被隐藏' : r.text,
    likeCount: r.likeCount, liked: r.liked, quote,
    parentReplyId: r.parentReplyId ?? null, children: [], status,
    edited: r.edited === true
  }
}

// F01 帖子列表：board 可空、sort=latest/hot、keyword 模糊（标题与标签）、分页；
// includeHidden 仅两档管理角色生效（服务端强制口径，学生传了也忽略）
registerMock('get', '/api/forum/topics', (ctx) => {
  const board = ctx.query.board ?? ''
  const sort = ctx.query.sort ?? 'latest'
  const keyword = ctx.query.keyword ?? ''
  const page = Number(ctx.query.page ?? '1')
  const size = Number(ctx.query.size ?? '10')
  const hiddenOk = ctx.query.includeHidden === 'true' && isAdminRole(ctx.role)
  // 第一步：过滤状态/版块与关键词
  let list: MockTopic[] = []
  for (let i = 0; i < topics.length; i++) {
    const t = topics[i]
    if (t.status === 'HIDDEN' && !hiddenOk) continue
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
// HIDDEN 一律 9102（含管理员——后端 M2d 口径：恢复走 F12 按 id 操作，不经过详情）
registerMock('get', '/api/forum/topics/:id', (ctx) => {
  const t = findTopic(ctx.params.id)
  if (!t || (t.status ?? 'NORMAL') === 'HIDDEN') throw new MockBizError(9102, '主题不存在或已隐藏')
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
    liked: t.liked, collected: t.collected, pinned: t.pinned, status: t.status ?? 'NORMAL',
    publishAt: t.publishAt,
    body: t.body.slice(), attach: t.attach.slice(),
    // 第三项后端语义为"被收藏数"（collectedCount，V4 已改名；演示数据无收藏计数，按获赞近似）
    authorStats: { postCount, likeTotal, collectedCount: Math.floor(likeTotal / 30) }
  }
})

// F05 楼层列表（一级分页、楼层号升序，size 上限 100 同后端；二级挂 children 不进分页；
// HIDDEN 主题整体 9102；HIDDEN 楼层学生占位、管理员原文——adminView 两档口径）
registerMock('get', '/api/forum/topics/:id/replies', (ctx) => {
  const t = findTopic(ctx.params.id)
  if (!t || (t.status ?? 'NORMAL') === 'HIDDEN') throw new MockBizError(9102, '主题不存在或已隐藏')
  const page = Number(ctx.query.page ?? '1')
  const size = Math.min(Number(ctx.query.size ?? '20') || 20, 100)
  const adminView = isAdminRole(ctx.role)
  const all = repliesOf(t.id)
  // 一级楼层进分页；二级不占分页位、children 全量内嵌（推入顺序即时间正序）
  const level1: MockReply[] = []
  for (let i = 0; i < all.length; i++) {
    if (!all[i].parentReplyId) level1.push(all[i])
  }
  const start = (page - 1) * size
  const items: ReplyItem[] = []
  for (let i = start; i < Math.min(start + size, level1.length); i++) {
    const vo = toReplyItem(level1[i], adminView)
    for (let j = 0; j < all.length; j++) {
      if (all[j].parentReplyId === level1[i].id) vo.children.push(toReplyItem(all[j], adminView))
    }
    items.push(vo)
  }
  return { list: items, total: level1.length, page, size }
})

// F06 发帖（幂等）。body：board title bodyText tags pinned
registerMock('post', '/api/forum/topics', (ctx) => {
  requireIdem(ctx.idemKey)
  const board = String(ctx.body.board ?? '')
  const title = String(ctx.body.title ?? '').trim()
  const bodyText = String(ctx.body.bodyText ?? '').trim()
  // 置顶仅超管生效，其他角色静默置 false（后端 TopicCreateDTO 同口径，不报错）
  const pinned = ctx.body.pinned === true && ctx.role === 'SUPER_ADMIN'
  if (!board || !title || !bodyText) throw new MockBizError(1001, '版块、标题、正文都不能为空')
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
    body, attach: [], status: 'NORMAL'
  })
  return { id }
})

// F07 回帖（幂等）。body：text quoteReplyId / parentReplyId（可空，两级封顶）
registerMock('post', '/api/forum/topics/:id/replies', (ctx) => {
  requireIdem(ctx.idemKey)
  const t = findTopic(ctx.params.id)
  if (!t) throw new MockBizError(9102, '主题不存在或已隐藏')
  if ((t.status ?? 'NORMAL') === 'LOCKED') throw new MockBizError(9101, '主题被锁定，暂停回复')
  const text = String(ctx.body.text ?? '').trim()
  if (!text) throw new MockBizError(1001, '回复内容不能为空')
  // 引用条无效（不存在/异主题/已隐藏）静默降级为无引用，不挡回帖（后端 L11 拍板口径）
  let quoteReplyId = ctx.body.quoteReplyId ? String(ctx.body.quoteReplyId) : null
  if (quoteReplyId) {
    const q = findReply(quoteReplyId)
    if (!q || q.topicId !== t.id || (q.status ?? 'NORMAL') === 'HIDDEN') quoteReplyId = null
  }
  // 二级父校验：不存在/异主题/已隐藏 9105；父本身已是二级 9106（两级封顶）
  const parentReplyId = ctx.body.parentReplyId ? String(ctx.body.parentReplyId) : null
  if (parentReplyId) {
    const p = findReply(parentReplyId)
    if (!p || p.topicId !== t.id || (p.status ?? 'NORMAL') === 'HIDDEN') {
      throw new MockBizError(9105, '父楼层不存在、已隐藏或不属于该主题')
    }
    if (p.parentReplyId) throw new MockBizError(9106, '评论最多两级，不能回复二级评论')
  }
  // 楼层号两级共用一个计数器（后端 FOR UPDATE 行锁口径）；replyCount 与列表活跃只按一级刷
  const all = repliesOf(t.id)
  let maxFloor = 0
  for (let i = 0; i < all.length; i++) {
    if (all[i].floorNo > maxFloor) maxFloor = all[i].floorNo
  }
  const floorNo = maxFloor + 1
  const id = allocReplyId(t.id, floorNo)
  replies.push({
    id, topicId: t.id, floorNo, author: ctx.loginName, authorGroup: '综合',
    publishAt: nowText(), text, likeCount: 0, liked: false, quoteReplyId, parentReplyId
  })
  if (!parentReplyId) {
    t.replyCount = t.replyCount + 1
    t.lastActiveAt = '刚刚'
    t.lastReplyBy = ctx.loginName
  }
  return { id, floorNo, level: parentReplyId ? 2 : 1 }
})

// F08 帖子点赞/取消（状态型操作：body 带目标态，重复点无副作用，不需要幂等头；返回服务端权威计数）
registerMock('post', '/api/forum/topics/:id/like', (ctx) => {
  const t = findTopic(ctx.params.id)
  if (!t || (t.status ?? 'NORMAL') === 'HIDDEN') throw new MockBizError(9102, '主题不存在或已隐藏')
  const liked = ctx.body.liked === true
  if (t.liked !== liked) {
    t.liked = liked
    t.likeCount += liked ? 1 : -1
  }
  return { liked, likeCount: t.likeCount }
})

// F09 帖子收藏/取消（收藏关系唯一约束天然幂等）
registerMock('post', '/api/forum/topics/:id/collect', (ctx) => {
  const t = findTopic(ctx.params.id)
  if (!t || (t.status ?? 'NORMAL') === 'HIDDEN') throw new MockBizError(9102, '主题不存在或已隐藏')
  const collected = ctx.body.collected === true
  t.collected = collected
  return { collected }
})

// F10 楼层点赞/取消（同 F08 状态型口径；楼层不存在 9103）
registerMock('post', '/api/forum/replies/:id/like', (ctx) => {
  const r = findReply(ctx.params.id)
  if (!r) throw new MockBizError(9103, '楼层不存在')
  const liked = ctx.body.liked === true
  if (r.liked === liked) return { liked }
  r.liked = liked
  r.likeCount += liked ? 1 : -1
  return { liked }
})
