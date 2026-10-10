/**
 * 材料接口（C 线 MT01–MR03 任务与要求 / MS01–MS06 提交与下载）
 * 对应方案：《前端ABC模块页面开发-优化方案》PC1+PC2；PRD §3.7
 * 关键约束：提交记录存在则要求/任务禁删（5005/5006）；覆盖上传版本号 +1 旧版失效；
 *    齐全率与队伍 material_status 由提交动作触发重算（B 线赛队列表联动展示）
 * ⚠️ 上传权限暂定"在队在册成员"（PRD §3.7），是否收紧为仅队长待联调确认
 */
import { del, get, post, put } from './http'

export interface MaterialTask {
  id: string
  contestId: string
  contestName: string
  title: string
  /** 截止时间 yyyy-MM-dd HH:mm；过截止后提交报 5002 */
  deadline: string
  requirementCount: number
  /** 该任务下已交齐的队伍数 / 任务覆盖的队伍数（管理端概览用） */
  teamTotal: number
  teamComplete: number
  createdAt: string
}

export interface MaterialRequirement {
  id: string
  taskId: string
  /** 12 种预设类型之一（config/labels.ts MATERIAL_TYPES） */
  type: string
  required: boolean
  /** 允许扩展名小写白名单，如 ['pdf','docx'] */
  formats: string[]
  /** 命名规则（可选展示给队长） */
  namingRule: string
  maxSizeMb: number
}

/** 单条要求在某队伍下的提交状态 */
export interface MaterialSubmitInfo {
  requirementId: string
  status: 'NOT_SUBMITTED' | 'SUBMITTED'
  /** 当前版本号（覆盖上传 +1，从 1 起） */
  version: number
  fileName: string
  uploadedAt: string
}

/** 管理端队伍提交概览行 */
export interface TeamMaterialRow {
  teamId: string
  teamName: string
  leaderName: string
  completeRate: number
  /** 材料已交 / 材料待补 */
  statusText: string
  items: MaterialSubmitInfo[]
}

/** 学生端"我的材料"单支队伍视图 */
export interface MyTeamMaterial {
  teamId: string
  teamName: string
  task: { id: string; title: string; deadline: string }
  completeRate: number
  /** 必传未齐条数（>0 时队伍状态为材料待补） */
  requiredMissing: number
  items: Array<{ requirement: MaterialRequirement } & MaterialSubmitInfo>
}

/** zip 批量上传解析结果（MS05：不匹配文件清单展示） */
export interface BatchMatchResult {
  matched: Array<{ requirementId: string; type: string }>
  unmatched: string[]
}

// ── 任务与要求（管理端，MT/MR）──
export function apiMaterialTasks(contestId?: string): Promise<MaterialTask[]> {
  return get('/api/material-tasks', contestId ? { contestId } : {})
}
export function apiCreateTask(body: { contestId: string; title: string; deadline: string }): Promise<MaterialTask> {
  return post('/api/material-tasks', body)
}
export function apiUpdateTask(id: string, body: { title: string; deadline: string }): Promise<null> {
  return put('/api/material-tasks/' + id, body)
}
/** 删除任务：任务下任一要求已有提交记录则拒删（5006） */
export function apiDeleteTask(id: string): Promise<null> {
  return del('/api/material-tasks/' + id)
}
export function apiRequirements(taskId: string): Promise<MaterialRequirement[]> {
  return get('/api/material-tasks/' + taskId + '/requirements')
}
export function apiCreateRequirement(
  taskId: string,
  body: { type: string; required: boolean; formats: string[]; namingRule: string; maxSizeMb: number }
): Promise<MaterialRequirement> {
  return post('/api/material-tasks/' + taskId + '/requirements', body)
}
export function apiUpdateRequirement(
  id: string,
  body: { type: string; required: boolean; formats: string[]; namingRule: string; maxSizeMb: number }
): Promise<null> {
  return put('/api/requirements/' + id, body)
}
/** 删除要求：已有提交记录则拒删（5005） */
export function apiDeleteRequirement(id: string): Promise<null> {
  return del('/api/requirements/' + id)
}

// ── 提交与下载（MS）──
/** 管理端队伍提交概览（MS03）：一任务一矩阵 */
export function apiTaskOverview(taskId: string): Promise<TeamMaterialRow[]> {
  return get('/api/material-tasks/' + taskId + '/overview')
}
/** 学生端我的材料：本人所在每支队伍的任务/要求/提交状态（MS04） */
export function apiMyMaterials(): Promise<MyTeamMaterial[]> {
  return get('/api/my/materials')
}
/** 单条提交/覆盖上传（MS01）：5002 过截止 / 5003 非在队成员 / 5004 格式不符 */
export function apiSubmitMaterial(body: {
  requirementId: string
  teamId: string
  fileId: string
  fileName: string
}): Promise<{ version: number }> {
  return post('/api/material-submissions', body)
}
/** zip 批量上传（MS05）：返回匹配要求与不匹配文件清单 */
export function apiBatchUpload(body: { teamId: string; taskId: string; fileId: string; fileName: string }): Promise<BatchMatchResult> {
  return post('/api/material-submissions/batch', body)
}
/** 打包下载队伍全部材料（MS06）：mock 返回提示；真实后端为文件流 */
export function apiExportTeamPackage(taskId: string, teamId: string): Promise<{ note: string }> {
  return post('/api/material-tasks/' + taskId + '/export', { teamId })
}
