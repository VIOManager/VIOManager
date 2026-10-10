/**
 * 学期管理接口（A 线 S01–S08，仅超管）
 * 对应清单：docs/api/A线-基础数据-接口清单.md §4 + PRD §3.4
 * 切换任务流程：发起（超管密码二次确认）→ 30 分钟待执行窗口 → 可取消 / 执行（乐观锁 version）
 */
import { del, get, post, put } from './http'

export type SemesterStatus = 'PLANNED' | 'ACTIVE' | 'ARCHIVED'

export interface Semester {
  id: string
  name: string
  startDate: string
  endDate: string
  weekCount: number
  status: SemesterStatus
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
  weekCount: number
}

export function apiSemesters(): Promise<Semester[]> {
  return get('/api/semesters')
}
/** 名称不可重复（3008） */
export function apiCreateSemester(body: SemesterForm): Promise<Semester> {
  return post('/api/semesters', body)
}
export function apiUpdateSemester(id: string, body: SemesterForm): Promise<Semester> {
  return put('/api/semesters/' + id, body)
}
/** 有关联业务数据拒绝删除（3009） */
export function apiDeleteSemester(id: string): Promise<null> {
  return del('/api/semesters/' + id)
}
/** 发起切换：超管密码二次确认（密码错 2002） */
export function apiCreateSwitchTask(toSemesterId: string, password: string): Promise<SwitchTask> {
  return post('/api/semesters/switch-tasks', { toSemesterId, password })
}
export function apiSwitchTasks(): Promise<SwitchTask[]> {
  return get('/api/semesters/switch-tasks')
}
export function apiCancelSwitchTask(id: string): Promise<null> {
  return post('/api/semesters/switch-tasks/' + id + '/cancel')
}
/** 执行：携带 version 乐观锁（版本不符 3011） */
export function apiExecuteSwitchTask(id: string, version: number): Promise<null> {
  return post('/api/semesters/switch-tasks/' + id + '/execute', { version })
}
