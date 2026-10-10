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

/** 人员类型（PRD §3.2：学生/教师/其他） */
export const MEMBER_TYPE_LABEL: Record<string, string> = {
  STUDENT: '学生',
  TEACHER: '教师',
  OTHER: '其他'
}

/** 学期状态（A 线 S01） */
export const SEMESTER_STATUS_LABEL: Record<string, string> = {
  PLANNED: '未开始',
  ACTIVE: '进行中',
  ARCHIVED: '已归档'
}

/** 学期切换任务状态（A 线 S06） */
export const SWITCH_TASK_STATUS_LABEL: Record<string, string> = {
  PENDING: '待执行',
  EXECUTING: '执行中',
  SUCCESS: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消'
}

/** 比赛状态机（B 线 C01：DRAFT→PUBLISHED→REGISTERING→RUNNING→FINISHED→ARCHIVED） */
export const CONTEST_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  REGISTERING: '报名中',
  RUNNING: '比赛进行中',
  FINISHED: '已结束',
  ARCHIVED: '已归档'
}

/** 规则键名翻译（B 线 §0 六键，全组唯一） */
export const RULE_KEY_LABEL: Record<string, string> = {
  memberLimit: '每队人数上限',
  prefillLimit: '报名预填人数上限',
  allowRepeatJoin: '允许重复参赛',
  crossTeamLeader: '允许跨队担任队长',
  maxAdvisorCount: '指导教师数量上限',
  crossTrackLimit: '跨赛道限制'
}

/** 规则来源（三级继承标注） */
export const RULE_SOURCE_LABEL: Record<string, string> = {
  TEMPLATE: '模板默认',
  CONTEST: '比赛覆盖',
  TRACK: '赛道覆盖'
}

/** 待处理比赛池状态（B 线 P01） */
export const POOL_STATUS_LABEL: Record<string, string> = {
  PENDING: '待处理',
  CONFIRMED: '已确认',
  IGNORED: '已忽略'
}

/** 规则值展示：布尔→是/否，数值→原样 */
export function ruleValueText(v: number | boolean): string {
  return typeof v === 'boolean' ? (v ? '是' : '否') : String(v)
}
