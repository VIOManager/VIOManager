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

/** 赛队成员状态机（B 线 §5） */
export const TEAM_MEMBER_STATUS_LABEL: Record<string, string> = {
  UNCONFIRMED: '待确认',
  CONFIRMED: '已确认',
  REJECTED: '已拒绝',
  EXITED: '已退出',
  REMOVED: '已移除'
}

/** 赛队内角色 */
export const TEAM_ROLE_LABEL: Record<string, string> = {
  LEADER: '队长',
  MEMBER: '队员'
}

/** 审批类型（暂定，骨架审批中心清单未出） */
export const APPROVAL_TYPE_LABEL: Record<string, string> = {
  TEAM_CREATE: '组队申请',
  TEAM_ADD_MEMBER: '加人申请',
  TEAM_REMOVE_MEMBER: '移除队员',
  TEAM_ADD_ADVISOR: '指导教师申请'
}

/** 材料类型（PRD §3.7 固定 12 种预设；defaultFormats 为新建要求时的默认允许格式） */
export interface MaterialTypeDef {
  value: string
  label: string
  defaultFormats: string[]
}

export const MATERIAL_TYPES: MaterialTypeDef[] = [
  { value: 'REGISTRATION_FORM', label: '报名表', defaultFormats: ['pdf', 'docx'] },
  { value: 'PROJECT_PROPOSAL', label: '项目书', defaultFormats: ['pdf'] },
  { value: 'COMMITMENT_LETTER', label: '承诺书', defaultFormats: ['pdf'] },
  { value: 'ID_COPY', label: '身份证复印件', defaultFormats: ['pdf', 'jpg'] },
  { value: 'STUDENT_ID_COPY', label: '学生证复印件', defaultFormats: ['pdf', 'jpg'] },
  { value: 'TEAM_PHOTO', label: '团队合照', defaultFormats: ['jpg', 'png'] },
  { value: 'DEFENSE_SLIDES', label: '答辩PPT', defaultFormats: ['pptx', 'pdf'] },
  { value: 'RESEARCH_REPORT', label: '研究报告', defaultFormats: ['pdf', 'docx'] },
  { value: 'SOURCE_CODE', label: '源代码', defaultFormats: ['zip', 'rar'] },
  { value: 'AWARD_CERTIFICATE', label: '获奖证书', defaultFormats: ['pdf', 'jpg'] },
  { value: 'EVIDENCE', label: '佐证材料', defaultFormats: ['pdf', 'jpg', 'zip'] },
  { value: 'OTHER', label: '其他', defaultFormats: ['pdf', 'zip'] }
]

/** 材料类型值 → 中文名（页面翻译用；含兜底） */
export const MATERIAL_TYPE_LABEL: Record<string, string> = Object.fromEntries(
  MATERIAL_TYPES.map((t) => [t.value, t.label])
)

/** 材料提交状态（C 线：未交 / 已交） */
export const MATERIAL_SUBMIT_LABEL: Record<string, string> = {
  NOT_SUBMITTED: '未交',
  SUBMITTED: '已交'
}
