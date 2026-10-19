/**
 * 材料接口（C 线 MT/MR 任务与要求 + MS 提交下载）
 * 对应方案：《前端ABC模块页面开发-优化方案》PC1+PC2；PRD §3.7
 * 2026-10-14 联调对齐：路径统一改为后端实现的 /api/materials/** 前缀
 *    （后端 MaterialXxxController 实测路由，与前端对接文档旧路径不同）
 * 关键约束：提交记录存在则要求/任务禁删（5005/5006）；覆盖上传版本号 +1 旧版失效
 * ⚠️ apiTaskOverview 契约分歧：后端 MS01 只有按队伍查的单对象概览
 *    （GET /api/materials/teams/{teamId}/overview），前端管理端"一任务一矩阵"无对应接口，
 *    待 C 线确认形状后前端改造——本函数暂保留旧调用，联调期该区块会报资源不存在
 */
import { get, post, postIdem, putIdem, delIdem, download } from './http'

export interface MaterialTask {
  id: string
  contestId: string
  contestName: string
  /** 任务名（后端字段 name，2026-10-14 联调对齐） */
  name: string
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
  /** 12 种预设类型之一（后端 materialType；中文名用 materialTypeLabel 或前端字典翻译） */
  materialType: string
  /** 要求名称（MR01 必填，如"报名表PDF版"；2026-10-19 补——原前端漏了此字段，后端回"要求名称不能为空"） */
  name: string
  /** 后端为 0/1 整数（1=必传） */
  requiredFlag: number
  /** 允许扩展名小写白名单，如 ['pdf','docx']（后端 allowedFormats） */
  allowedFormats: string[]
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
  matched: Array<{ requirementId: string; materialType: string }>
  unmatched: string[]
}

// ── 任务与要求（管理端，MT/MR）──
export function apiMaterialTasks(contestId?: string): Promise<MaterialTask[]> {
  return get('/api/materials/tasks', contestId ? { contestId } : {})
}
export function apiCreateTask(body: { contestId: string; name: string; deadline: string }): Promise<MaterialTask> {
  return postIdem('/api/materials/tasks', body, crypto.randomUUID())
}
export function apiUpdateTask(id: string, body: { name: string; deadline: string }): Promise<null> {
  return putIdem('/api/materials/tasks/' + id, body, crypto.randomUUID())
}
/** 删除任务：任务下任一要求已有提交记录则拒删（5006） */
export function apiDeleteTask(id: string): Promise<null> {
  return delIdem('/api/materials/tasks/' + id, crypto.randomUUID())
}
export function apiRequirements(taskId: string): Promise<MaterialRequirement[]> {
  return get('/api/materials/tasks/' + taskId + '/requirements')
}
export function apiCreateRequirement(
  taskId: string,
  body: { materialType: string; name: string; requiredFlag: number; allowedFormats: string[]; namingRule: string; maxSizeMb: number }
): Promise<MaterialRequirement> {
  return postIdem('/api/materials/tasks/' + taskId + '/requirements', body, crypto.randomUUID())
}
export function apiUpdateRequirement(
  id: string,
  body: { materialType: string; name: string; requiredFlag: number; allowedFormats: string[]; namingRule: string; maxSizeMb: number }
): Promise<null> {
  return putIdem('/api/materials/requirements/' + id, body, crypto.randomUUID())
}
/** 删除要求：已有提交记录则拒删（5005） */
export function apiDeleteRequirement(id: string): Promise<null> {
  return delIdem('/api/materials/requirements/' + id, crypto.randomUUID())
}

// ── 提交与下载（MS）──
/** 管理端队伍提交概览：⚠️ 契约分歧见文件头注——后端仅按队伍查单对象，矩阵接口待 C 线定 */
export function apiTaskOverview(taskId: string): Promise<TeamMaterialRow[]> {
  return get('/api/materials/tasks/' + taskId + '/overview')
}
/** 学生端我的材料：本人所在每支队伍的任务/要求/提交状态（仅学生角色） */
export function apiMyMaterials(): Promise<MyTeamMaterial[]> {
  return get('/api/materials/my')
}
/** 单条提交/覆盖上传（后端 MS02）：5002 过截止 / 5003 非在队成员 / 5004 格式不符
 *  ⚠️ fileSize 必传（字节）：后端入参校验正数，最终以文件表权威值覆盖（2026-10-19 修——
 *  此前前端漏发，提交恒被 1001"fileSize 必须为正数"拒掉，行状态永远停在未交） */
export function apiSubmitMaterial(requirementId: string, body: {
  teamId: string
  fileId: string
  fileName: string
  fileSize: number
}): Promise<{ version: number }> {
  return post('/api/materials/requirements/' + requirementId + '/submit', body)
}
/** zip 批量上传（后端 MS03）：返回匹配要求与不匹配文件清单 */
export function apiBatchUpload(body: { teamId: string; taskId: string; fileId: string; fileName: string }): Promise<BatchMatchResult> {
  return post('/api/materials/submissions/batch', body)
}
/** 打包下载队伍全部材料（后端 MS05）：GET 文件流，浏览器直接保存 zip */
export function apiExportTeamPackage(teamId: string): Promise<void> {
  return download('/api/materials/teams/' + teamId + '/package', '材料打包.zip')
}
