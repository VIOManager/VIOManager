/**
 * 技术交流分类（单一数据源）
 * 依据：《论坛V2EX风格重设计-优化方案》§6——六分类集 2026-10-09 lzh 已确认定稿：
 *    电控 / 建模 / AI / 视频剪辑 / 编程开发 / 其他
 * ⚠️ 冲突：分类集同时被本页 tab、行首色块、将来接口清单引用——增删分类只改本文件；
 *    category 字段按开放字符串处理，后端清单据此对齐（技术交流接口清单尚未落盘）
 */
export interface FeedCategory {
  code: string
  name: string
  /** 行首 48px 色块里的短字 */
  short: string
  /** 色块配色类名（样式在 FeedPage scoped 内，键名一一对应） */
  classKey: string
}

export const FEED_CATEGORIES: FeedCategory[] = [
  { code: 'EE', name: '电控', short: '电', classKey: 'k-ee' },
  { code: 'MODELING', name: '建模', short: '建', classKey: 'k-mo' },
  { code: 'AI', name: 'AI', short: 'AI', classKey: 'k-ai' },
  { code: 'VIDEO', name: '视频剪辑', short: '剪', classKey: 'k-vd' },
  { code: 'DEV', name: '编程开发', short: '编', classKey: 'k-dev' },
  { code: 'OTHER', name: '其他', short: '其', classKey: 'k-etc' }
]

/** 按分类名取配置；未登记的分类兜底为"其他"（开放字符串的防守） */
export function feedCategoryOf(name: string): FeedCategory {
  const c = FEED_CATEGORIES.find((x) => x.name === name)
  return c ?? FEED_CATEGORIES[FEED_CATEGORIES.length - 1]
}
