/**
 * 项目管理 mock 数据层（接口层在 ./project.ts / ./project-ext.ts）
 * 对应契约：《项目管理-接口清单》（2026-10-12，错误码 7xxx）
 * 核心是可见性八条（清单 §2，所有读接口共用）：按序命中即放行
 *   1 所有人 → 2 成员/指导教师 → 3 超管 → 4 部门管理员子树 → 5 PUBLIC → 6 ORG 同部门 → 7 ACTIVE 授权 → 8 不可见 7003
 * 演示数据：p1 循迹小车（m5 所有，挂 t1）/ p2 闲置小程序（m6，挂 t2）/ p3 机械臂（m3，PRIVATE 演示不可见）/ p4 海报生成器（m4，PUBLIC）
 * TODO(联调)：后端项目接口就绪后删除本文件
 */
import type { ProjectStatus, ProjectVisibility } from '@/api/project'
import { nowText } from './approval-data'

export interface MockProject {
  id: string
  name: string
  description: string
  remark: string
  ownerId: string
  ownerName: string
  orgId: string
  orgName: string
  semesterId: string
  semesterName: string
  contestId: string
  contestName: string
  trackId: string
  status: ProjectStatus
  visibility: ProjectVisibility
  startDate: string
  endDate: string
}

export interface MockProjectMember {
  projectId: string
  memberId: string
  name: string
  role: 'OWNER' | 'MEMBER'
  joinedAt: string
}

export interface MockProjectAdvisor {
  projectId: string
  memberId: string
  name: string
  college: string
}

export interface MockTeamProjectRef {
  id: string
  teamId: string
  projectId: string
  trackId: string
  attachedByName: string
  attachedAt: string
  status: 'ACTIVE' | 'RELEASED'
}

export interface MockMilestone {
  id: string
  contestId: string
  nodeType: string
  name: string
  nodeTime: string
  description: string
  status: 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED'
  rejectReason: string
  approvedAt: string
  approvedBy: string
  createdAt: string
  /** 已通过节点是否已同步生成任务（幂等防重） */
  synced: boolean
}

export interface MockProjectTask {
  id: string
  projectId: string
  title: string
  description: string
  assigneeMemberId: string
  assigneeName: string
  dueDate: string
  priority: string
  status: 'TODO' | 'DOING' | 'DONE'
  sortNo: number
  milestoneNodeId: string
  completedAt: string
  completedByName: string
}

export interface MockProjectFile {
  id: string
  projectId: string
  fileId: string
  fileName: string
  fileSize: number
  fileCategory: string
  scanStatus: string
  uploadedByName: string
  uploadedById: string
  createdAt: string
}

export interface MockViewGrant {
  id: string
  projectId: string
  applicantMemberId: string
  applicantName: string
  grantType: 'ONCE' | 'LIMITED'
  status: 'PENDING' | 'ACTIVE' | 'EXPIRED' | 'REVOKED'
  expireAt: string
  usedCount: number
  createdAt: string
  /** 关联审批单 id（PR29 开单后回填，回调定位用） */
  approvalId: string
}

export const projects: MockProject[] = [
  { id: 'p1', name: '智能循迹小车', description: '基于 STM32 的红外循迹小车，含 PID 调速与赛道识别。', remark: '', ownerId: 'm5', ownerName: '李明轩', orgId: 'o11', orgName: '数媒协会', semesterId: 's2', semesterName: '2026秋季学期', contestId: 'c1', contestName: '2026 全国大学生电子设计竞赛（校内选拔赛）', trackId: 'tr1', status: 'ACTIVE', visibility: 'ORG', startDate: '2026-09-20', endDate: '2026-12-20' },
  { id: 'p2', name: '校园闲置交易小程序', description: '校内二手交易小程序，含商品发布、站内信与信用分。', remark: '', ownerId: 'm6', ownerName: '孙嘉仪', orgId: 'o111', orgName: '数媒二楼', semesterId: 's2', semesterName: '2026秋季学期', contestId: 'c1', contestName: '2026 全国大学生电子设计竞赛（校内选拔赛）', trackId: 'tr2', status: 'ACTIVE', visibility: 'ORG', startDate: '2026-09-22', endDate: '2026-12-20' },
  { id: 'p3', name: '机械臂视觉分拣', description: 'OpenMV 视觉识别 + 三轴机械臂分拣演示。', remark: '', ownerId: 'm3', ownerName: '赵天宇', orgId: 'o12', orgName: '电控工作室', semesterId: 's2', semesterName: '2026秋季学期', contestId: '', contestName: '', trackId: '', status: 'ACTIVE', visibility: 'PRIVATE', startDate: '2026-10-01', endDate: '2026-12-31' },
  { id: 'p4', name: '海报生成器', description: 'AIGC 校园活动海报一键生成（Web）。', remark: '', ownerId: 'm4', ownerName: '陈子昂', orgId: 'o13', orgName: '建模组', semesterId: 's2', semesterName: '2026秋季学期', contestId: '', contestName: '', trackId: '', status: 'ACTIVE', visibility: 'PUBLIC', startDate: '2026-10-08', endDate: '2026-12-31' }
]

export const projectMembers: MockProjectMember[] = [
  { projectId: 'p1', memberId: 'm5', name: '李明轩', role: 'OWNER', joinedAt: '2026-09-20 10:00:00' },
  { projectId: 'p1', memberId: 'm7', name: '王雨桐', role: 'MEMBER', joinedAt: '2026-09-21 09:30:00' },
  { projectId: 'p2', memberId: 'm6', name: '孙嘉仪', role: 'OWNER', joinedAt: '2026-09-22 11:00:00' },
  { projectId: 'p2', memberId: 'm4', name: '陈子昂', role: 'MEMBER', joinedAt: '2026-09-23 14:00:00' },
  { projectId: 'p3', memberId: 'm3', name: '赵天宇', role: 'OWNER', joinedAt: '2026-10-01 09:00:00' },
  { projectId: 'p4', memberId: 'm4', name: '陈子昂', role: 'OWNER', joinedAt: '2026-10-08 15:00:00' }
]

export const projectAdvisors: MockProjectAdvisor[] = [
  { projectId: 'p1', memberId: 'm1', name: '李明', college: '计算机与信息工程学院' }
]

export const teamProjectRefs: MockTeamProjectRef[] = [
  { id: 'tpr1', teamId: 't1', projectId: 'p1', trackId: 'tr1', attachedByName: '李明轩', attachedAt: '2026-09-25 10:00:00', status: 'ACTIVE' },
  { id: 'tpr2', teamId: 't2', projectId: 'p2', trackId: 'tr2', attachedByName: '孙嘉仪', attachedAt: '2026-09-26 11:00:00', status: 'ACTIVE' }
]

export const milestones: MockMilestone[] = [
  { id: 'ms1', contestId: 'c1', nodeType: 'REGISTER_START', name: '报名开始', nodeTime: '2026-10-10 00:00', description: '', status: 'APPROVED', rejectReason: '', approvedAt: '2026-10-09 10:00:00', approvedBy: '李明', createdAt: '2026-10-08 09:00:00', synced: true },
  { id: 'ms2', contestId: 'c1', nodeType: 'WORK_SUBMIT', name: '作品提交截止', nodeTime: '2026-11-20 23:59', description: '作品书与佐证打包上传', status: 'PENDING', rejectReason: '', approvedAt: '', approvedBy: '', createdAt: '2026-10-11 10:00:00', synced: false },
  { id: 'ms3', contestId: 'c1', nodeType: 'OTHER', name: '中期检查', nodeTime: '2026-11-05 14:00', description: '', status: 'DRAFT', rejectReason: '', approvedAt: '', approvedBy: '', createdAt: '2026-10-11 10:05:00', synced: false }
]

export const projectTasks: MockProjectTask[] = [
  { id: 'pt1', projectId: 'p1', title: '传感器采购清单确认', description: '红外对管与编码器选型', assigneeMemberId: 'm7', assigneeName: '王雨桐', dueDate: '2026-10-18', priority: 'HIGH', status: 'DOING', sortNo: 1, milestoneNodeId: '', completedAt: '', completedByName: '' },
  { id: 'pt2', projectId: 'p1', title: '作品提交截止', description: '作品书与佐证打包上传', assigneeMemberId: '', assigneeName: '', dueDate: '2026-11-20', priority: 'URGENT', status: 'TODO', sortNo: 2, milestoneNodeId: 'ms2', completedAt: '', completedByName: '' }
]

export const projectFiles: MockProjectFile[] = [
  { id: 'pf1', projectId: 'p1', fileId: 'f-demo-1', fileName: '循迹小车方案 v2.pdf', fileSize: 2481024, fileCategory: 'DOC', scanStatus: 'CLEAN', uploadedByName: '李明轩', uploadedById: 'm5', createdAt: '2026-10-06 20:00:00' }
]

export const viewGrants: MockViewGrant[] = [
  { id: 'vg1', projectId: 'p3', applicantMemberId: 'm7', applicantName: '王雨桐', grantType: 'LIMITED', status: 'ACTIVE', expireAt: '2026-10-20 23:59', usedCount: 0, createdAt: '2026-10-09 15:00:00', approvalId: '' }
]

/**
 * 可见性八条（清单 §2，按序命中即放行）
 * scopeOrgs：部门管理员子树 id 集（null=超管/学生不裁剪语义由调用方分清——此处只看组织归属）
 */
export function visibleTo(p: MockProject, ctx: {
  isSuper: boolean
  isDeptAdmin: boolean
  meId: string
  meOrgId: string
  deptScope: string[] | null
  orgSubtreeOf: (orgId: string) => string[]
}): boolean {
  // 1 所有人
  if (ctx.meId && p.ownerId === ctx.meId) return true
  // 2 成员 / 指导教师
  if (ctx.meId) {
    for (const m of projectMembers) {
      if (m.projectId === p.id && m.memberId === ctx.meId) return true
    }
    for (const a of projectAdvisors) {
      if (a.projectId === p.id && a.memberId === ctx.meId) return true
    }
  }
  // 3 超管全部
  if (ctx.isSuper) return true
  // 4 部门管理员：项目组织在其子树
  if (ctx.isDeptAdmin && ctx.deptScope !== null && ctx.deptScope.indexOf(p.orgId) >= 0) return true
  // 5 PUBLIC 全平台
  if (p.visibility === 'PUBLIC') return true
  // 6 ORG 同部门（项目 org 子树内）
  if (p.visibility === 'ORG' && ctx.meOrgId && ctx.orgSubtreeOf(p.orgId).indexOf(ctx.meOrgId) >= 0) return true
  // 7 ACTIVE 查看授权（ONCE 未用完 / LIMITED 未到期）
  if (ctx.meId) {
    for (const g of viewGrants) {
      if (g.projectId !== p.id || g.applicantMemberId !== ctx.meId || g.status !== 'ACTIVE') continue
      if (g.grantType === 'ONCE' && g.usedCount < 1) return true
      if (g.grantType === 'LIMITED' && g.expireAt >= nowText().slice(0, 10)) return true
    }
  }
  return false
}
