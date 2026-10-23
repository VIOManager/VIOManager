/**
 * 个人中心 mock 处理器（F11–F14 占位编号，清单落盘后对齐）
 * 身份口径：ctx.loginName（开发调试件，见 stores/user.ts）——联调后真实服务按 JWT userId 判
 * 注册顺序：/api/forum/me/** 与 /api/forum/topics/:id 前缀段不同，无占位冲突
 */
import { registerMock } from './registry'
import { topics, findTopic, type MockTopic } from './forum-data'
import { replies } from './forum-replies'
import { techPosts, type MockTechPost } from './tech-data'

/** 帖子 → 列表项（与 mock/forum.ts 的 toListItem 同口径：脱正文与附件，列表页轻载） */
function topicItem(t: MockTopic) {
  return {
    id: t.id, board: t.board, title: t.title, author: t.author, authorGroup: t.authorGroup,
    tags: t.tags.slice(), replyCount: t.replyCount, viewCount: t.viewCount, likeCount: t.likeCount,
    lastActiveAt: t.lastActiveAt, lastReplyBy: t.lastReplyBy, pinned: t.pinned, hot: t.hot
  }
}

/** 技术推送 → 列表项（与 mock/tech.ts 的 toListItem 同口径：脱正文与外链） */
function techItem(p: MockTechPost) {
  return {
    id: p.id, title: p.title, summary: p.summary, category: p.category,
    contentType: p.contentType, sourceType: p.sourceType, sourceName: p.sourceName,
    pinned: p.pinned, publishAt: p.publishAt, viewCount: p.viewCount,
    collectCount: p.collectCount, collected: p.collected, tags: p.tags.slice()
  }
}

/** 楼层 → 我的回复条目：补所属帖标题；帖子已不存在记"帖子已删除"（楼层仍保留历史） */
function replyItem(r: (typeof replies)[number]) {
  const t = findTopic(r.topicId)
  return {
    topicId: r.topicId,
    topicTitle: t ? t.title : '帖子已删除',
    floorNo: r.floorNo,
    text: r.text,
    publishAt: r.publishAt,
    likeCount: r.likeCount
  }
}

// F11 我的主题：本人发的帖，新帖在前（id 降序，同 F01 latest 口径）
registerMock('get', '/api/forum/me/topics', (ctx) => {
  const mine: MockTopic[] = []
  for (let i = 0; i < topics.length; i++) {
    if (topics[i].author === ctx.loginName) mine.push(topics[i])
  }
  mine.sort((a, b) => Number(b.id) - Number(a.id))
  return mine.map(topicItem)
})

// F12 我的回复：本人在各帖的楼层，新回复在前
registerMock('get', '/api/forum/me/replies', (ctx) => {
  const mine: (typeof replies)[number][] = []
  for (let i = 0; i < replies.length; i++) {
    if (replies[i].author === ctx.loginName) mine.push(replies[i])
  }
  return mine.map(replyItem)
})

// F13 我的收藏：论坛帖（F09 收藏态）+ 技术推送（T03 收藏态）两路
registerMock('get', '/api/forum/me/collections', (ctx) => {
  const tlist: MockTopic[] = []
  for (let i = 0; i < topics.length; i++) {
    if (topics[i].collected) tlist.push(topics[i])
  }
  const plist: MockTechPost[] = []
  for (let i = 0; i < techPosts.length; i++) {
    if (techPosts[i].collected && !techPosts[i].withdrawn) plist.push(techPosts[i])
  }
  return { topics: tlist.map(topicItem), techPosts: plist.map(techItem) }
})

// F14 我的点赞：点赞的帖（F08）+ 点赞的楼层（F10）两路
registerMock('get', '/api/forum/me/likes', (ctx) => {
  const tlist: MockTopic[] = []
  for (let i = 0; i < topics.length; i++) {
    if (topics[i].liked) tlist.push(topics[i])
  }
  const rlist: (typeof replies)[number][] = []
  for (let i = 0; i < replies.length; i++) {
    if (replies[i].liked) rlist.push(replies[i])
  }
  return { topics: tlist.map(topicItem), replies: rlist.map(replyItem) }
})
