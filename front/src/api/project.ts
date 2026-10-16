/**
 * 项目管理接口（PR01–PR31）
 * 契约源：《项目管理-接口清单》（2026-10-12，错误码 7xxx；录入 Apifox 后以 Apifox 为准）
 * 机制要点：
 *   项目是所有人的个人资产（写操作一律仅所有人 7002）；赛队是租客（队长挂载，可挂多队打不同比赛）
 *   可见性八条按序命中（清单 §2）：所有人/成员/指导 → 超管全部 → 部门管理员子树 → PUBLIC → ORG 同部门 → ACTIVE 授权
 *   PR20 节点提交审核走审批中心 MILESTONE_APPROVE，通过后自动给挂载该比赛的项目生成任务
 *   PR29 查看申请走 PROJECT_VIEW 审批；ONCE 授权看详情一次即消耗（PR03）
 *   PR02/PR20/PR26/PR29 带幂等头
 */
import { del, get, post, postIdem, put } from './http'

export type ProjectStatus = 'ACTIVE' | 'FINISHED' | 'ARCHIVED'
export type ProjectVisibility = 'PRIVATE' | 'ORG' | 'PUBLIC'

export interface ProjectItem {
  id: string
  name: string
  ownerName: string
  orgName: string
  semesterName: string
  contestName: string
  status: ProjectStatus
  visibility: ProjectVisibility
  startDate: string
  endDate: string
}

export interface ProjectDetail extends ProjectItem {
  description: string
  remark: string
  ownerId: string
  orgId: string
  semesterId: string
  contestId: string
  trackId: string
  /** 当前登录人是否所有人（前端按钮显隐；最终权限后端判定） */
  viewerIsOwner: boolean
  /** 指导教师列表（清单未出独立读接口，暂挂详情扩展字段；TODO(联调)：以后端清单修订为准） */
  advisors: ProjectAdvisor[]
}

export interface ProjectMember {
  memberId: string
  name: string
  role: 'OWNER' | 'MEMBER'
  joinedAt: string
}

export interface ProjectAdvisor {
  memberId: string
  name: string
  college: string
}

export interface ProjectTeamRef {
  teamId: string
  teamName: string
  contestName: string
  trackName: string
  attachedByName: string
  attachedAt: string
  status: 'ACTIVE' | 'RELEASED'
}

/** PR12 本队挂载的项目列表项（与 PR11 互为镜像视图） */
export interface TeamProjectItem {
  projectId: string
  name: string
  ownerName: string
  teamId: string
  teamName: string
  contestName: string
  trackName: string
  attachedByName: string
  attachedAt: string
  status: 'ACTIVE' | 'RELEASED'
}

export type MilestoneStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED'
export type MilestoneType = 'REGISTER_START' | 'REGISTER_END' | 'WORK_SUBMIT' | 'OTHER'

export interface Milestone {
  id: string
  contestId: string
  nodeType: MilestoneType
  name: string
  nodeTime: string
  description: string
  status: MilestoneStatus
  rejectReason: string
  approvedAt: string
  approvedBy: string
  createdAt: string
}

export type TaskStatus = 'TODO' | 'DOING' | 'DONE'
export type TaskPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'

export interface ProjectTask {
  id: string
  title: string
  description: string
  assigneeMemberId: string
  assigneeName: string
  dueDate: string
  priority: TaskPriority
  status: TaskStatus
  sortNo: number
  /** 节点同步生成的任务带溯源 id；手工任务为空 */
  milestoneNodeId: string
  completedAt: string
  completedByName: string
}

export interface ProjectFile {
  id: string
  fileId: string
  fileName: string
  fileSize: number
  fileCategory: 'DOC' | 'IMAGE' | 'CODE' | 'OTHER'
  scanStatus: string
  uploadedByName: string
  createdAt: string
}

export type GrantType = 'ONCE' | 'LIMITED'
export type GrantStatus = 'PENDING' | 'ACTIVE' | 'EXPIRED' | 'REVOKED'

export interface ViewGrant {
  id: string
  applicantName: string
  grantType: GrantType
  status: GrantStatus
  expireAt: string
  usedCount: number
  createdAt: string
}

// ── PR01–PR05 项目主体 ──

export function apiProjects(q?: { keyword?: string; status?: string; contestId?: string; semesterId?: string; orgId?: string }): Promise<ProjectItem[]> {
  return get('/api/projects', (q ?? {}) as Record<string, unknown>)
}

export function apiCreateProject(body: {
  name: string; description: string; contestId?: string; trackId?: string
  semesterId?: string; orgId?: string; visibility?: string; startDate?: string; endDate?: string; remark?: string
}): Promise<{ id: string }> {
  return postIdem('/api/projects', body, crypto.randomUUID())
}

/** PR03 详情（ONCE 授权在此消耗） */
export function apiProjectDetail(id: string): Promise<ProjectDetail> {
  return get('/api/projects/' + id)
}

export function apiUpdateProject(id: string, body: { name?: string; description?: string; status?: string; visibility?: string; remark?: string }): Promise<null> {
  return put('/api/projects/' + id, body)
}

/** PR05 删除（逻辑删；有 ACTIVE 挂载赛队时 7010） */
export function apiDeleteProject(id: string): Promise<null> {
  return del('/api/projects/' + id)
}

// ── PR06–PR10 成员与指导教师（不走审批）──

export function apiProjectMembers(id: string): Promise<ProjectMember[]> {
  return get('/api/projects/' + id + '/members')
}

export function apiAddProjectMember(id: string, memberId: string): Promise<null> {
  return post('/api/projects/' + id + '/members', { memberId })
}

export function apiRemoveProjectMember(id: string, memberId: string): Promise<null> {
  return del('/api/projects/' + id + '/members/' + memberId)
}

export function apiAddProjectAdvisor(id: string, memberId: string): Promise<null> {
  return post('/api/projects/' + id + '/advisors', { memberId })
}

export function apiRemoveProjectAdvisor(id: string, memberId: string): Promise<null> {
  return del('/api/projects/' + id + '/advisors/' + memberId)
}

// ── PR11–PR15 赛队挂载 ──

export function apiProjectTeams(id: string): Promise<ProjectTeamRef[]> {
  return get('/api/projects/' + id + '/teams')
}

/** PR13 列表；项目模块后端未实现期间由 TeamProjectPanel 静默降级（2026-10-16），故走 silent 不弹错 */
export function apiTeamProjects(teamId: string): Promise<TeamProjectItem[]> {
  return get('/api/teams/' + teamId + '/projects', undefined, { silent: true })
}

/** PR13 队长挂载（同学期 7008、重复 7007；挂载时回溯补建未同步节点任务） */
export function apiAttachProject(teamId: string, projectId: string, trackId?: string): Promise<null> {
  return post('/api/teams/' + teamId + '/projects', { projectId, trackId })
}

/** PR14 解绑（已生成任务不回收） */
export function apiDetachProject(teamId: string, projectId: string): Promise<null> {
  return del('/api/teams/' + teamId + '/projects/' + projectId)
}

/** PR15 可挂载项目池（挂载选择器）；同上静默降级 */
export function apiAttachableProjects(contestId?: string): Promise<ProjectItem[]> {
  return get('/api/projects/attachable', contestId ? { contestId } : {}, { silent: true })
}

// ── PR16–PR20 比赛时间节点 ──

export function apiMilestones(contestId: string, q?: { status?: string; nodeType?: string }): Promise<Milestone[]> {
  return get('/api/contests/' + contestId + '/milestones', (q ?? {}) as Record<string, unknown>)
}

export function apiCreateMilestone(contestId: string, body: { nodeType: string; name: string; nodeTime: string; description?: string }): Promise<{ id: string }> {
  return post('/api/contests/' + contestId + '/milestones', body)
}

/** PR18 编辑（仅 DRAFT/REJECTED，7009） */
export function apiUpdateMilestone(id: string, body: { nodeType?: string; name?: string; nodeTime?: string; description?: string }): Promise<null> {
  return put('/api/milestones/' + id, body)
}

/** PR19 删除（仅 DRAFT，7009） */
export function apiDeleteMilestone(id: string): Promise<null> {
  return del('/api/milestones/' + id)
}

/** PR20 提交审核（开 MILESTONE_APPROVE 审批单；带幂等头） */
export function apiSubmitMilestone(id: string): Promise<null> {
  return postIdem('/api/milestones/' + id + '/submit', {}, crypto.randomUUID())
}

// ── PR21–PR24 项目任务 ──

export function apiProjectTasks(id: string, q?: { status?: string; assigneeMemberId?: string }): Promise<ProjectTask[]> {
  return get('/api/projects/' + id + '/tasks', (q ?? {}) as Record<string, unknown>)
}

export function apiCreateTask(id: string, body: { title: string; description?: string; assigneeMemberId?: string; dueDate?: string; priority?: string; sortNo?: number }): Promise<{ id: string }> {
  return post('/api/projects/' + id + '/tasks', body)
}

/** PR23 编辑/状态更新（负责人本人仅可置 DONE；其余字段仅所有人） */
export function apiUpdateTask(id: string, taskId: string, body: { title?: string; description?: string; assigneeMemberId?: string; dueDate?: string; priority?: string; status?: string }): Promise<null> {
  return put('/api/projects/' + id + '/tasks/' + taskId, body)
}

export function apiDeleteTask(id: string, taskId: string): Promise<null> {
  return del('/api/projects/' + id + '/tasks/' + taskId)
}

// ── PR25–PR28 项目文件 ──

export function apiProjectFiles(id: string): Promise<ProjectFile[]> {
  return get('/api/projects/' + id + '/files')
}

/** PR26 关联已上传文件（先走骨架 F01 拿 fileId；所有人与项目成员可传） */
export function apiAttachFile(id: string, body: { fileId: string; fileName: string; fileSize?: number; fileCategory: string; remark?: string }): Promise<{ id: string }> {
  return postIdem('/api/projects/' + id + '/files', body, crypto.randomUUID())
}

export function apiDeleteProjectFile(id: string, fileRecordId: string): Promise<null> {
  return del('/api/projects/' + id + '/files/' + fileRecordId)
}

/** PR28 下载（先过项目可见性校验再转发，写审计；mock 回提示文本） */
export function apiDownloadProjectFile(fileRecordId: string): Promise<{ note: string }> {
  return get('/api/projects/files/' + fileRecordId + '/download')
}

// ── PR29–PR31 查看申请与授权 ──

/** PR29 提交查看申请（走审批中心 PROJECT_VIEW；已有 PENDING 拒绝 7011；带幂等头） */
export function apiRequestView(id: string, body: { grantType: 'ONCE' | 'LIMITED'; expireAt?: string }): Promise<null> {
  return postIdem('/api/projects/' + id + '/view-requests', body, crypto.randomUUID())
}

export function apiViewGrants(id: string): Promise<ViewGrant[]> {
  return get('/api/projects/' + id + '/view-grants')
}

export function apiRevokeGrant(id: string): Promise<null> {
  return put('/api/view-grants/' + id + '/revoke', {})
}
