/**
 * 材料演示数据（mock/material.ts 与 mock/material-submit.ts 的数据部分）
 * 与赛队数据对齐：t1 电波突击队 3/4 项（缺承诺书）→ 75% 待补 1 项；t2 全交 100%；t3 归档任务全交 100%
 */
export interface MockTask {
  id: string
  contestId: string
  /** 任务名（2026-10-14 对齐后端 name） */
  name: string
  deadline: string
  createdAt: string
}

export interface MockRequirement {
  id: string
  taskId: string
  materialType: string
  /** 要求名称（MR01 必填，2026-10-19 对齐后端补） */
  name: string
  requiredFlag: number
  allowedFormats: string[]
  namingRule: string
  maxSizeMb: number
}

export interface MockSubmission {
  id: string
  requirementId: string
  teamId: string
  fileId: string
  fileName: string
  /** 覆盖上传后旧版本 valid=false（保留痕迹不计入齐全率） */
  version: number
  uploadedBy: string
  uploadedAt: string
  valid: boolean
}

/** 任务 id 发号器 */
let taskSeq = 10
export function nextTaskId(): string {
  taskSeq += 1
  return 'k' + taskSeq
}
/** 要求 id 发号器 */
let reqSeq = 20
export function nextReqId(): string {
  reqSeq += 1
  return 'r' + reqSeq
}
let subSeq = 30
export function nextSubId(): string {
  subSeq += 1
  return 'ms' + subSeq
}

export const tasks: MockTask[] = [
  { id: 'k1', contestId: 'c1', name: '2026 电设校内选拔赛报名材料', deadline: '2026-10-20 23:59', createdAt: '2026-10-02 10:00:00' }
]

/** c3 归档任务单独放（已结束比赛的留存记录，演示 5006 拒删与历史查看） */
export const archivedTasks: MockTask[] = [
  { id: 'k2', contestId: 'c3', name: '2026 春季校赛归档材料', deadline: '2026-04-30 23:59', createdAt: '2026-03-20 09:00:00' }
]

export const requirements: MockRequirement[] = [
  // 材料类型值 2026-10-19 对齐后端 MaterialTypeEnum（原 PROJECT_PROPOSAL/COMMITMENT_LETTER 为 mock 期自造）
  { id: 'r1', taskId: 'k1', materialType: 'REGISTRATION_FORM', name: '报名表', requiredFlag: true ? 1 : 0, allowedFormats: ['pdf', 'docx'], namingRule: '队名-报名表', maxSizeMb: 10 },
  { id: 'r2', taskId: 'k1', materialType: 'PROJECT_PLAN', name: '项目书', requiredFlag: true ? 1 : 0, allowedFormats: ['pdf'], namingRule: '', maxSizeMb: 20 },
  { id: 'r3', taskId: 'k1', materialType: 'COMMITMENT', name: '承诺书', requiredFlag: true ? 1 : 0, allowedFormats: ['pdf'], namingRule: '', maxSizeMb: 10 },
  { id: 'r4', taskId: 'k1', materialType: 'TEAM_PHOTO', name: '团队合照', requiredFlag: true ? 1 : 0, allowedFormats: ['jpg', 'png'], namingRule: '队名-合照', maxSizeMb: 10 },
  { id: 'r5', taskId: 'k2', materialType: 'REGISTRATION_FORM', name: '报名表', requiredFlag: true ? 1 : 0, allowedFormats: ['pdf', 'docx'], namingRule: '', maxSizeMb: 10 },
  { id: 'r6', taskId: 'k2', materialType: 'RESEARCH_REPORT', name: '研究报告', requiredFlag: true ? 1 : 0, allowedFormats: ['pdf', 'docx'], namingRule: '', maxSizeMb: 20 }
]

export const submissions: MockSubmission[] = [
  // t1 电波突击队：报名表/项目书/合照已交，承诺书未交（待补 1 项 · 75%）
  { id: 'ms1', requirementId: 'r1', teamId: 't1', fileId: 'f-mock-101', fileName: '电波突击队-报名表.pdf', version: 1, uploadedBy: '李明轩', uploadedAt: '2026-10-08 14:20:00', valid: true },
  { id: 'ms2', requirementId: 'r2', teamId: 't1', fileId: 'f-mock-102', fileName: '智能循迹小车-项目书.pdf', version: 1, uploadedBy: '李明轩', uploadedAt: '2026-10-09 09:10:00', valid: true },
  { id: 'ms3', requirementId: 'r4', teamId: 't1', fileId: 'f-mock-103', fileName: '电波突击队-合照.jpg', version: 1, uploadedBy: '李明轩', uploadedAt: '2026-10-09 16:40:00', valid: true },
  // t2 像素工坊：全交；报名表覆盖过一次（v2 生效 v1 失效，演示版本递增）
  { id: 'ms4', requirementId: 'r1', teamId: 't2', fileId: 'f-mock-104', fileName: '像素工坊-报名表.pdf', version: 1, uploadedBy: '孙嘉仪', uploadedAt: '2026-10-07 11:00:00', valid: false },
  { id: 'ms5', requirementId: 'r1', teamId: 't2', fileId: 'f-mock-105', fileName: '像素工坊-报名表-v2.pdf', version: 2, uploadedBy: '孙嘉仪', uploadedAt: '2026-10-08 20:30:00', valid: true },
  { id: 'ms6', requirementId: 'r2', teamId: 't2', fileId: 'f-mock-106', fileName: '校园闲置交易小程序-项目书.pdf', version: 1, uploadedBy: '孙嘉仪', uploadedAt: '2026-10-07 11:05:00', valid: true },
  { id: 'ms7', requirementId: 'r3', teamId: 't2', fileId: 'f-mock-107', fileName: '像素工坊-承诺书.pdf', version: 1, uploadedBy: '孙嘉仪', uploadedAt: '2026-10-07 11:10:00', valid: true },
  { id: 'ms8', requirementId: 'r4', teamId: 't2', fileId: 'f-mock-108', fileName: '像素工坊-合照.jpg', version: 1, uploadedBy: '孙嘉仪', uploadedAt: '2026-10-07 11:12:00', valid: true },
  // t3 极光智创：归档任务全交（100%）
  { id: 'ms9', requirementId: 'r5', teamId: 't3', fileId: 'f-mock-109', fileName: '极光智创-报名表.pdf', version: 1, uploadedBy: '周子墨', uploadedAt: '2026-04-10 10:00:00', valid: true },
  { id: 'ms10', requirementId: 'r6', teamId: 't3', fileId: 'f-mock-110', fileName: '宿舍智能门锁-研究报告.pdf', version: 1, uploadedBy: '周子墨', uploadedAt: '2026-04-12 15:30:00', valid: true }
]
