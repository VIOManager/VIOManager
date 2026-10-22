/**
 * 排班管理接口（TT01–TT08 课表导入 + SC01–SC11 排班/规则/导出）
 * 契约源：《排班管理-接口清单》（2026-10-12，错误码 8001–8039；录入 Apifox 后以 Apifox 为准）
 * 机制要点：
 *   课表三步：上传 Excel（同步解析）→ 班级映射确认（未确认班级不参与空课统计）→ 按格查空课
 *   排班校验链（SC02/SC03 按序）：8009 非本子组织 → 8007 时段未启用 → 8006 超人数上限
 *     → 8005 同周同人同子组织已有（拍板：一周一个班）→ 8008 课表冲突（带 force 强制保存并打冲突标记）
 *   SC02/TT02 带幂等头；学生只有 SC05 我的排班
 */
import { del, download, get, post, postIdem, put, upload } from './http'

// ── 课表导入 ──

export type ImportBatchStatus = 'PENDING' | 'MAPPING' | 'COMPLETED' | 'FAILED'

export interface ImportBatch {
  id: string
  semesterId: string
  semesterName: string
  status: ImportBatchStatus
  totalCount: number
  successCount: number
  failCount: number
  createdAt: string
}

export interface ImportBatchDetail extends ImportBatch {
  errors: Array<{ rowNo: number; reason: string }>
}

export interface ClassMapping {
  id: string
  semesterId: string
  /** 课表班级原始名 */
  classKey: string
  /** 档案侧班级名 */
  className: string
  grade: string
  gradeSource: string
  confirmed: boolean
}

export interface CourseRecord {
  id: string
  weekNo: number
  weekday: number
  slotIndex: number
  classKey: string
  courseName: string
  teacher: string
  location: string
  weekParity: 'ALL' | 'ODD' | 'EVEN'
}

// ── 空课查询 ──

/** SC06 整周矩阵每格：空课人数 + 按年级分布 */
export interface FreeSlotCell {
  weekday: number
  slotIndex: number
  freeCount: number
  gradeDist: Record<string, number>
}

/** SC07 单格空课名单行 */
export interface FreeMember {
  memberId: string
  name: string
  className: string
  grade: string
}

// ── 排班 ──

export interface ScheduleEntry {
  id: string
  memberId: string
  memberName: string
  className: string
  dutyName: string
  courseConflict: boolean
  conflictNote: string
  remark: string
}

/** SC01 周排班表：7×5 格子视图 */
export interface ScheduleCell {
  weekday: number
  slotIndex: number
  entries: ScheduleEntry[]
}

/** SC05 我的排班行（学生视角） */
export interface MySchedule {
  id: string
  weekNo: number
  subOrgName: string
  weekday: number
  slotIndex: number
  dutyName: string
  remark: string
}

export interface ScheduleRule {
  weekday: number
  slotIndex: number
  enabled: boolean
  minPerson: number
  maxPerson: number
}

// ── TT01–TT08 课表导入 ──

/** TT01 模板下载（后端返回 xlsx 二进制流，走 blob 下载；mock 时代曾按 {note} 文本解析，联调修正） */
export function apiTimetableTemplate(): Promise<void> {
  return download('/api/timetables/import-template', '课表导入模板.xlsx')
}

/** TT02 上传导入（multipart + 幂等头；同步解析返回批次与成败数。教务全校课表展开近 10 万条，超时放宽 120s） */
export function apiTimetableImport(file: File, idempotencyKey: string): Promise<{ batchId: string; totalCount: number; successCount: number; failCount: number }> {
  return upload('/api/timetables/imports', file, idempotencyKey, undefined, 120_000)
}

export function apiTimetableImports(semesterId?: string): Promise<ImportBatch[]> {
  return get('/api/timetables/imports', semesterId ? { semesterId } : {})
}

export function apiTimetableImportDetail(id: string): Promise<ImportBatchDetail> {
  return get('/api/timetables/imports/' + id)
}

/** TT05 归一化失败时人工指定学期（仅 PENDING，指定后续跑解析） */
export function apiAssignImportSemester(id: string, semesterId: string): Promise<null> {
  return put('/api/timetables/imports/' + id + '/semester', { semesterId })
}

/** TT06 映射列表（hasMember=true 只看有在册成员的班级；档案班级名由后端实时匹配，新成员即时生效） */
export function apiClassMappings(q?: { semesterId?: string; confirmed?: boolean; hasMember?: boolean }): Promise<ClassMapping[]> {
  return get('/api/timetables/class-mappings', (q ?? {}) as Record<string, unknown>)
}

/** TT07 批量确认映射（确认后才参与空课统计，PRD 铁规则） */
export function apiConfirmMappings(ids: string[]): Promise<null> {
  return put('/api/timetables/class-mappings/confirm', { ids })
}

export function apiCourses(q: { semesterId: string; weekNo?: number; classKey?: string }): Promise<CourseRecord[]> {
  return get('/api/timetables/courses', q as Record<string, unknown>)
}

// ── SC06/SC07 空课查询（仅管理端）──

export function apiFreeSlotMatrix(q: { semesterId: string; weekNo: number }): Promise<FreeSlotCell[]> {
  return get('/api/schedules/free-slots/matrix', q as unknown as Record<string, unknown>)
}

export function apiFreeSlotMembers(q: { semesterId: string; weekNo: number; weekday: number; slotIndex: number }): Promise<FreeMember[]> {
  return get('/api/schedules/free-slots', q as unknown as Record<string, unknown>)
}

// ── SC01–SC05 排班 ──

export function apiSchedules(q: { semesterId: string; weekNo: number; subOrgId: string }): Promise<ScheduleCell[]> {
  return get('/api/schedules', q as unknown as Record<string, unknown>)
}

/** SC02 批量排班（校验链见文件头；8008 冲突时确认后带 force 重提）。带幂等头 */
export function apiCreateSchedules(body: {
  semesterId: string
  weekNo: number
  subOrgId: string
  assignments: Array<{ memberId: string; weekday: number; slotIndex: number; dutyName: string }>
  force?: boolean
}): Promise<null> {
  return postIdem('/api/schedules', body, crypto.randomUUID())
}

export function apiUpdateSchedule(id: string, body: { weekday?: number; slotIndex?: number; dutyName?: string; remark?: string }): Promise<null> {
  return put('/api/schedules/' + id, body)
}

export function apiCancelSchedule(id: string): Promise<null> {
  return del('/api/schedules/' + id)
}

/** SC05 我的排班（学生） */
export function apiMySchedules(q?: { semesterId?: string; weekNo?: number }): Promise<MySchedule[]> {
  return get('/api/schedules/my', (q ?? {}) as Record<string, unknown>)
}

// ── SC10/SC11 排班规则 ──

export function apiScheduleRules(q: { subOrgId: string; semesterId?: string }): Promise<ScheduleRule[]> {
  return get('/api/schedules/rules', q as Record<string, unknown>)
}

/** SC11 批量保存（upsert；semesterId 空=子组织默认规则） */
export function apiSaveScheduleRules(body: { subOrgId: string; semesterId?: string; items: ScheduleRule[] }): Promise<null> {
  return put('/api/schedules/rules', body)
}

// ── SC08/SC09 导出（mock 返回提示文本）──

export function apiScheduleExport(q: { semesterId: string; weekNo: number; subOrgId: string }): Promise<{ note: string }> {
  return get('/api/schedules/export', q as unknown as Record<string, unknown>)
}

export function apiScheduleExportStats(q: { semesterId: string; subOrgId?: string }): Promise<{ note: string }> {
  return get('/api/schedules/export-stats', q as Record<string, unknown>)
}
