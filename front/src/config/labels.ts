/**
 * 系统固定枚举的中文翻译表
 * 红线区分：业务可扩展字典（SKILL 等）一律走 useDict，本表只管代码里写死的状态枚举
 * （枚举值与后端约定一致；新增枚举翻译在此追加，页面禁止就地硬编码）
 */

/** 字典审核状态（A 线 D01） */
export const AUDIT_STATUS_LABEL: Record<string, string> = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回'
}

/** 字典项来源 */
export const DICT_SOURCE_LABEL: Record<string, string> = {
  PRESET: '预置',
  USER: '用户补充'
}
