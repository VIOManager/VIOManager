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

/** 审批业务类型（审批中心清单 §6 词表，9 项；新增 bizType 须先在该清单登记） */
export const APPROVAL_TYPE_LABEL: Record<string, string> = {
  TEAM_CREATE: '组队申请',
  TEAM_ADD_MEMBER: '加人申请',
  TEAM_REMOVE_MEMBER: '移除队员',
  TEAM_ADD_ADVISOR: '指导教师申请',
  TEAM_DISSOLVE: '解散赛队',
  ACHIEVEMENT_SUBMIT: '成果提交',
  PROJECT_VIEW: '项目查看申请',
  MILESTONE_APPROVE: '节点审核',
  SEMESTER_SWITCH: '学期切换'
}

/** 审批状态机（审批中心清单 §3：PENDING 一跳终结，四个终态） */
export const APPROVAL_STATUS_LABEL: Record<string, string> = {
  PENDING: '待处理',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
  EXPIRED: '已过期'
}

/** 审批流水动作（approval_record.action） */
export const APPROVAL_ACTION_LABEL: Record<string, string> = {
  CREATE: '发起申请',
  CANCEL: '取消申请',
  APPROVE: '批准',
  REJECT: '驳回',
  AGREE: '同意',
  REFUSE: '拒绝',
  EXPIRE: '过期'
}

/** 审批流水操作人身份（approval_record.operator_role） */
export const APPROVAL_ROLE_LABEL: Record<string, string> = {
  APPLICANT: '申请人',
  TARGET: '目标人',
  APPROVER: '审批人',
  SYSTEM: '系统'
}

/** 成果类型（PRD §3.8：获奖/评级/资质/活动成果/其他） */
export const ACHIEVEMENT_TYPE_LABEL: Record<string, string> = {
  AWARD: '获奖',
  RATING: '评级',
  QUALIFICATION: '资质',
  ACTIVITY: '活动成果',
  OTHER: '其他'
}

/** 成果状态：队长提交待审批 → 管理员通过/驳回（管理员录入直接生效） */
export const ACHIEVEMENT_STATUS_LABEL: Record<string, string> = {
  PENDING: '待审批',
  APPROVED: '已通过',
  REJECTED: '已驳回'
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

/** 绩效周报状态机（绩效清单 §2：驳回不删记录，改后重进待审核） */
export const WEEKLY_STATUS_LABEL: Record<string, string> = {
  SUBMITTED: '待审核',
  SCORED: '已打分',
  REJECTED: '已驳回'
}

/** 绩效内置标签（绩效清单 §4，打分后自动挂） */
export const PERF_TAG_LABEL: Record<string, string> = {
  ACTIVITY_UP: '活跃度上升',
  ACTIVITY_DOWN: '活跃度下降',
  TOP_CONTRIBUTOR: '功劳度高',
  STREAK: '连续贡献',
  EFFICIENT_WEEK: '高效周'
}

/** 排班导入批次状态机（排班清单 §2） */
export const IMPORT_BATCH_STATUS_LABEL: Record<string, string> = {
  PENDING: '待归一化',
  MAPPING: '待确认映射',
  COMPLETED: '已完成',
  FAILED: '解析失败'
}

/** 排班单双周标记（course_timetable.week_parity） */
export const WEEK_PARITY_LABEL: Record<string, string> = {
  ALL: '每周',
  ODD: '单周',
  EVEN: '双周'
}

/** 项目状态（项目清单：ACTIVE→FINISHED→ARCHIVED 按序流转） */
export const PROJECT_STATUS_LABEL: Record<string, string> = {
  ACTIVE: '进行中',
  FINISHED: '已完结',
  ARCHIVED: '已归档'
}

/** 项目可见性（项目清单 §2） */
export const PROJECT_VISIBILITY_LABEL: Record<string, string> = {
  PRIVATE: '仅项目内',
  ORG: '本部门可见',
  PUBLIC: '全平台可见'
}

/** 项目任务优先级 */
export const TASK_PRIORITY_LABEL: Record<string, string> = {
  LOW: '低',
  NORMAL: '普通',
  HIGH: '高',
  URGENT: '紧急'
}

/** 项目任务状态 */
export const TASK_STATUS_LABEL: Record<string, string> = {
  TODO: '待办',
  DOING: '进行中',
  DONE: '已完成'
}

/** 比赛时间节点类型（项目清单 §3.4） */
export const MILESTONE_TYPE_LABEL: Record<string, string> = {
  REGISTER_START: '报名开始',
  REGISTER_END: '报名截止',
  WORK_SUBMIT: '作品提交',
  OTHER: '其他'
}

/** 时间节点状态机（DRAFT→PENDING→APPROVED/REJECTED，REJECTED 可重提） */
export const MILESTONE_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  PENDING: '审核中',
  APPROVED: '已通过',
  REJECTED: '已驳回'
}

/** 查看授权类型（项目清单 §3.7：ONCE 一次性 / LIMITED 限时） */
export const GRANT_TYPE_LABEL: Record<string, string> = {
  ONCE: '一次性',
  LIMITED: '限时'
}

/** 查看授权状态 */
export const GRANT_STATUS_LABEL: Record<string, string> = {
  PENDING: '待审批',
  ACTIVE: '生效中',
  EXPIRED: '已失效',
  REVOKED: '已撤销'
}

/** 项目文件分类（PR26 fileCategory） */
export const PROJECT_FILE_CATEGORY_LABEL: Record<string, string> = {
  DOC: '文档',
  IMAGE: '图片',
  CODE: '代码',
  OTHER: '其他'
}

/** 项目内角色（project_member.role） */
export const PROJECT_ROLE_LABEL: Record<string, string> = {
  OWNER: '所有人',
  MEMBER: '成员'
}

/** 排班星期（weekday 1–7） */
export const WEEKDAY_LABEL: Record<number, string> = {
  1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六', 7: '周日'
}

/** 排班节次（slotIndex 1–5，两大节一档） */
export const SLOT_LABEL: Record<number, string> = {
  1: '1–2 节', 2: '3–4 节', 3: '5–6 节', 4: '7–8 节', 5: '9–10 节'
}

/** 知识库条目形态（lzh 2026-10-14 拍板简化：外部链接或上传文件二选一） */
export const KB_SOURCE_LABEL: Record<string, string> = {
  LINK: '外部链接',
  FILE: '文件'
}

/** 组织类型（A 线 O02 orgType，与后端 OrgSaveDTO 枚举一致；ROOT 仅 mock 根节点数据使用） */
export const ORG_TYPE_LABEL: Record<string, string> = {
  COLLEGE: '学院',
  DEPARTMENT: '部门',
  CLASS: '班级',
  CLUB: '社团',
  SUB_ORG: '子组织',
  OTHER: '其他',
  ROOT: '根节点'
}
