/**
 * 学期管理接口（A 线 S01–S08，仅超管）
 * 对应清单：docs/api/A线-基础数据-接口清单.md §4 + PRD §3.4
 * 切换任务流程：发起（超管密码二次确认）→ 30 分钟待执行窗口 → 可取消 / 执行（乐观锁 version）
 */
import { get, postIdem, putIdem, delIdem } from './http'

export type SemesterStatus = 'PLANNED' | 'ACTIVE' | 'ARCHIVED'

export interface Semester {
  id: string
  name: string
  startDate: string
  endDate: string
  totalWeeks: number
  status: SemesterStatus
  /** 是否当前学期（后端 VO 出参；与 status 联动展示用） */
  isCurrent?: boolean
}

export type SwitchTaskStatus = 'PENDING' | 'EXECUTING' | 'SUCCESS' | 'FAILED' | 'CANCELLED'

/** 切换任务（含影响快照与乐观锁版本号） */
export interface SwitchTask {
  id: string
  toSemesterName: string
  status: SwitchTaskStatus
  createdAt: string
  /** 待执行窗口截止时间（30 分钟） */
  deadline: string
  /** 影响快照：待归档比赛数 / 待继承赛队数 / 成员关系数 */
  impact: { contestCount: number; teamCount: number; memberRelationCount: number }
  version: number
}

export interface SemesterForm {
  name: string
  startDate: string
  endDate: string
  totalWeeks: number
}

/**
 * 联调兜底学期：A 线学期接口后端未实现（404）时，跨模块借用学期的展示场景
 * （概览刻度尺/排班课表/比赛表单）用后端库已知学期占位；学期管理页不兜底（见各自调用处）
 */
export const FALLBACK_SEMESTERS: Semester[] = [
  { id: '301', name: '2026-2027学年第一学期', startDate: '', endDate: '', totalWeeks: 20, status: 'ACTIVE' }
]

export function apiSemesters(): Promise<Semester[]> {
  return get('/api/semesters')
}
/**
 * 2026-10-17 联调对齐（A 线学期后端上线）：
 * 1. 写操作全部带 X-Idempotency-Key（后端 S02/S03/S08 均 @Idempotent，缺头直接 1001）
 * 2. 周数字段后端叫 totalWeeks（前端原 weekCount 是 mock 期自造名）
 */
/** 名称不可重复（3008） */
export function apiCreateSemester(body: SemesterForm): Promise<Semester> {
  return postIdem('/api/semesters', body, crypto.randomUUID())
}
export function apiUpdateSemester(id: string, body: SemesterForm): Promise<Semester> {
  return putIdem('/api/semesters/' + id, body, crypto.randomUUID())
}
/** 有关联业务数据拒绝删除（3009） */
export function apiDeleteSemester(id: string): Promise<null> {
  return delIdem('/api/semesters/' + id, crypto.randomUUID())
}
/** 发起切换：超管密码二次确认（密码错 2002） */
export function apiCreateSwitchTask(toSemesterId: string, password: string): Promise<SwitchTask> {
  return postIdem('/api/semesters/switch-tasks', { toSemesterId, password }, crypto.randomUUID())
}
export function apiSwitchTasks(): Promise<SwitchTask[]> {
  return get('/api/semesters/switch-tasks')
}
/**
 * 切换任务列表（静默兜底版）：2026-10-17 联调发现后端该接口 500（code 1000 系统繁忙），
 * TODO(联调) A 线修复后换回直通版 apiSwitchTasks 并删除本函数
 */
export async function apiSwitchTasksSafe(): Promise<SwitchTask[]> {
  try {
    return await get<SwitchTask[]>('/api/semesters/switch-tasks', undefined, { silent: true })
  } catch {
    return []
  }
}
export function apiCancelSwitchTask(id: string): Promise<null> {
  return postIdem('/api/semesters/switch-tasks/' + id + '/cancel', {}, crypto.randomUUID())
}
/** 执行：携带 version 乐观锁（版本不符 3011） */
export function apiExecuteSwitchTask(id: string, version: number): Promise<null> {
  return postIdem('/api/semesters/switch-tasks/' + id + '/execute', { version }, crypto.randomUUID())
}

/**
 * 学期列表（静默兜底版）：跨模块借用学期的展示场景专用（概览刻度尺/排班课表/比赛表单）——
 * A 线接口未实现时不弹错误，返回 FALLBACK_SEMESTERS 占位；学期管理页请用直通版 apiSemesters
 */
export async function apiSemestersSafe(): Promise<Semester[]> {
  try {
    return await get<Semester[]>('/api/semesters', undefined, { silent: true })
  } catch {
    return FALLBACK_SEMESTERS
  }
}

/** 当前激活学期（ACTIVE 优先，无则取第一条；接口失败走 FALLBACK）——排班/课表面板共用 */
export async function activeSemester(): Promise<Semester | null> {
  const list = await apiSemestersSafe()
  return list.find((s) => s.status === 'ACTIVE') ?? list[0] ?? null
}
