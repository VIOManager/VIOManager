/**
 * 个人中心 API（F11–F14 占位编号——论坛服务接口清单未落盘，落盘后对齐登记）
 * 需求来源：lzh 2026-10-23——"点赞收藏没有能看到的地方、个人没有创作的地方"
 * 口径：全部按"当前登录人"过滤（mock 用开发调试件判身份，联调后服务端按 JWT userId 判）
 * 我的订阅不在此文件：订阅关系本期不落库存 localStorage（技术交流清单 §3 拍板），
 *    页面侧直接读同一存储键 forum_tech_subs + T04 标签列表
 */
import { get } from './http'
import type { TopicItem } from './forum'
import type { TechPostItem } from './tech'

/** 我的回复条目：楼层内容 + 所属帖标题（个人中心列表跳转用） */
export interface MyReplyItem {
  topicId: string
  topicTitle: string
  floorNo: number
  text: string
  publishAt: string
  likeCount: number
}

/** 我的收藏：论坛帖 F09 + 技术推送 T03 两路合并展示 */
export interface MyCollections {
  topics: TopicItem[]
  techPosts: TechPostItem[]
}

/** 我的点赞：帖子 F08 + 楼层 F10 两路合并展示 */
export interface MyLikes {
  topics: TopicItem[]
  replies: MyReplyItem[]
}

/** F11 我的主题：本人发的帖，新帖在前 */
export function apiMyTopics(): Promise<TopicItem[]> {
  return get('/api/forum/me/topics')
}

/** F12 我的回复：本人在各帖的楼层（附所属帖标题） */
export function apiMyReplies(): Promise<MyReplyItem[]> {
  return get('/api/forum/me/replies')
}

/** F13 我的收藏：收藏的论坛帖 + 收藏的技术推送 */
export function apiMyCollections(): Promise<MyCollections> {
  return get('/api/forum/me/collections')
}

/** F14 我的点赞：点赞的帖 + 点赞的楼层 */
export function apiMyLikes(): Promise<MyLikes> {
  return get('/api/forum/me/likes')
}
