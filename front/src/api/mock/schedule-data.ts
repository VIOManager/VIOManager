/**
 * 排班管理 mock 数据层（接口层在 ./schedule.ts）
 * 对应契约：《排班管理-接口清单》（2026-10-12，错误码 8001–8039；拍板：同周同人同子组织仅一条 8005 由服务层校验）
 * 核心机制：
 *   空课判定 freeAt——班级在 (weekday, slotIndex) 无课即空课；单双周要算（ODD 奇周占用 / EVEN 偶周占用 / ALL 都占）；
 *   名单只含已确认映射班级的成员（PRD 铁规则：未确认班级永远不参与空课统计）
 * 演示数据：4 条班级映射（机自 23-1 未确认）/ 三班课表 / o11 默认排班规则 / 3 条在排班次（含 2 条冲突标记）
 * TODO(联调)：后端排班接口就绪后删除本文件
 */
import { members } from './member-data'
import type { MockMember } from './member-data'

export interface MockClassMapping {
  id: string
  semesterId: string
  classKey: string
  className: string
  grade: string
  gradeSource: string
  confirmed: boolean
}

export interface MockCourse {
  id: string
  classKey: string
  weekday: number
  slotIndex: number
  courseName: string
  teacher: string
  location: string
  weekParity: 'ALL' | 'ODD' | 'EVEN'
}

export interface MockScheduleRule {
  id: string
  subOrgId: string
  semesterId: string | null
  weekday: number
  slotIndex: number
  enabled: boolean
  minPerson: number
  maxPerson: number
}

export interface MockSchedule {
  id: string
  semesterId: string
  weekNo: number
  subOrgId: string
  memberId: string
  memberName: string
  className: string
  weekday: number
  slotIndex: number
  dutyName: string
  courseConflict: boolean
  conflictNote: string
  remark: string
  status: 'ACTIVE' | 'CANCELLED'
}

export interface MockImportBatch {
  id: string
  semesterId: string
  status: 'PENDING' | 'MAPPING' | 'COMPLETED' | 'FAILED'
  totalCount: number
  successCount: number
  failCount: number
  createdAt: string
  errors: Array<{ rowNo: number; reason: string }>
}

// ── 种子：班级映射（课表班级名 ↔ 档案班级名；机自 23-1 待确认演示） ──
export const classMappings: MockClassMapping[] = [
  { id: 'cm1', semesterId: 's2', classKey: '软工23-2班', className: '软工23-2班', grade: '2023', gradeSource: '学号推导', confirmed: true },
  { id: 'cm2', semesterId: 's2', classKey: '计科23-1班', className: '计科23-1班', grade: '2023', gradeSource: '学号推导', confirmed: true },
  { id: 'cm3', semesterId: 's2', classKey: '电信23-1班', className: '电信23-1班', grade: '2023', gradeSource: '学号推导', confirmed: true },
  { id: 'cm4', semesterId: 's2', classKey: '机自23-1班', className: '机自23-1班', grade: '2023', gradeSource: '班级名兜底', confirmed: false }
]

// ── 种子：课表（全学期通用，单双周由 weekParity 表达） ──
export const courses: MockCourse[] = [
  { id: 'c01', classKey: '软工23-2班', weekday: 1, slotIndex: 1, courseName: '高等数学', teacher: '王老师', location: 'A101', weekParity: 'ALL' },
  { id: 'c02', classKey: '软工23-2班', weekday: 1, slotIndex: 2, courseName: '大学英语', teacher: '刘老师', location: 'B203', weekParity: 'ALL' },
  { id: 'c03', classKey: '软工23-2班', weekday: 2, slotIndex: 3, courseName: '体育', teacher: '赵老师', location: '体育馆', weekParity: 'ALL' },
  { id: 'c04', classKey: '软工23-2班', weekday: 3, slotIndex: 1, courseName: '数据结构', teacher: '陈老师', location: 'C305', weekParity: 'ODD' },
  { id: 'c05', classKey: '软工23-2班', weekday: 5, slotIndex: 4, courseName: '班会', teacher: '辅导员', location: 'D102', weekParity: 'EVEN' },
  { id: 'c06', classKey: '计科23-1班', weekday: 1, slotIndex: 1, courseName: '高等数学', teacher: '王老师', location: 'A102', weekParity: 'ALL' },
  { id: 'c07', classKey: '计科23-1班', weekday: 3, slotIndex: 2, courseName: '程序设计', teacher: '孙老师', location: '机房 2', weekParity: 'ALL' },
  { id: 'c08', classKey: '电信23-1班', weekday: 2, slotIndex: 1, courseName: '电路原理', teacher: '周老师', location: 'E201', weekParity: 'ALL' },
  { id: 'c09', classKey: '电信23-1班', weekday: 4, slotIndex: 3, courseName: '信号与系统', teacher: '吴老师', location: 'E305', weekParity: 'ODD' }
]

// ── 种子：排班规则（o11 默认：工作日 1-2 节启用，每格最多 2 人） ──
export const scheduleRules: MockScheduleRule[] = [
  { id: 'sr1', subOrgId: 'o11', semesterId: null, weekday: 1, slotIndex: 1, enabled: true, minPerson: 1, maxPerson: 2 },
  { id: 'sr2', subOrgId: 'o11', semesterId: null, weekday: 1, slotIndex: 2, enabled: true, minPerson: 1, maxPerson: 2 },
  { id: 'sr3', subOrgId: 'o11', semesterId: null, weekday: 2, slotIndex: 1, enabled: true, minPerson: 1, maxPerson: 2 },
  { id: 'sr4', subOrgId: 'o11', semesterId: null, weekday: 2, slotIndex: 2, enabled: true, minPerson: 1, maxPerson: 2 },
  { id: 'sr5', subOrgId: 'o11', semesterId: null, weekday: 3, slotIndex: 1, enabled: true, minPerson: 1, maxPerson: 2 },
  { id: 'sr6', subOrgId: 'o11', semesterId: null, weekday: 3, slotIndex: 2, enabled: true, minPerson: 1, maxPerson: 2 },
  { id: 'sr7', subOrgId: 'o11', semesterId: null, weekday: 4, slotIndex: 1, enabled: true, minPerson: 1, maxPerson: 2 },
  { id: 'sr8', subOrgId: 'o11', semesterId: null, weekday: 4, slotIndex: 2, enabled: true, minPerson: 1, maxPerson: 2 },
  { id: 'sr9', subOrgId: 'o11', semesterId: null, weekday: 5, slotIndex: 1, enabled: true, minPerson: 1, maxPerson: 2 },
  { id: 'sr10', subOrgId: 'o11', semesterId: null, weekday: 5, slotIndex: 2, enabled: true, minPerson: 1, maxPerson: 2 }
]

// ── 种子：在排班次（s2 W6；王雨桐/赵天宇两条带冲突标记演示） ──
export const schedules: MockSchedule[] = [
  { id: 'sc1', semesterId: 's2', weekNo: 6, subOrgId: 'o11', memberId: 'm7', memberName: '王雨桐', className: '软工23-2班', weekday: 1, slotIndex: 2, dutyName: '机房值守', courseConflict: true, conflictNote: '与「大学英语」冲突（强制排入）', remark: '', status: 'ACTIVE' },
  { id: 'sc2', semesterId: 's2', weekNo: 6, subOrgId: 'o11', memberId: 'm5', memberName: '李明轩', className: '软工23-2班', weekday: 3, slotIndex: 2, dutyName: '设备巡检', courseConflict: false, conflictNote: '', remark: '', status: 'ACTIVE' },
  { id: 'sc3', semesterId: 's2', weekNo: 6, subOrgId: 'o12', memberId: 'm3', memberName: '赵天宇', className: '电信23-1班', weekday: 2, slotIndex: 1, dutyName: '实验室值班', courseConflict: true, conflictNote: '与「电路原理」冲突（强制排入）', remark: '', status: 'ACTIVE' }
]

// ── 种子：导入批次（一单完成带 2 条错误明细；一单归一化失败待人工指定学期） ──
export const importBatches: MockImportBatch[] = [
  {
    id: 'tb1', semesterId: 's2', status: 'COMPLETED', totalCount: 486, successCount: 484, failCount: 2,
    createdAt: '2026-09-01 10:20:00',
    errors: [
      { rowNo: 137, reason: '班级名为空' },
      { rowNo: 402, reason: '节次号超出 1–5 范围' }
    ]
  },
  { id: 'tb2', semesterId: '', status: 'PENDING', totalCount: 0, successCount: 0, failCount: 0, createdAt: '2026-10-10 09:05:00', errors: [] }
]

/** 单双周命中（ODD 仅奇数教学周占用，EVEN 仅偶数周，ALL 都占） */
export function parityHit(parity: string, weekNo: number): boolean {
  if (parity === 'ALL') return true
  if (parity === 'ODD') return weekNo % 2 === 1
  return weekNo % 2 === 0
}

/** 班级在该格是否有课（返回冲突课程名列表，空=空课） */
export function coursesAt(classKey: string, weekNo: number, weekday: number, slotIndex: number): string[] {
  const out: string[] = []
  for (const c of courses) {
    if (c.classKey === classKey && c.weekday === weekday && c.slotIndex === slotIndex && parityHit(c.weekParity, weekNo)) {
      out.push(c.courseName)
    }
  }
  return out
}

/** 成员在该格是否空课（未确认映射班级一律不空课——不参与统计） */
export function memberFreeAt(m: MockMember, weekNo: number, weekday: number, slotIndex: number): boolean {
  let confirmed = false
  for (const cm of classMappings) {
    if (cm.classKey === m.className && cm.confirmed) confirmed = true
  }
  if (!confirmed) return false
  return coursesAt(m.className, weekNo, weekday, slotIndex).length === 0
}

/** 该格空课成员名单（SC07 同源） */
export function freeMembersAt(weekNo: number, weekday: number, slotIndex: number): MockMember[] {
  const out: MockMember[] = []
  for (const m of members) {
    if (m.memberType !== 'STUDENT') continue
    if (memberFreeAt(m, weekNo, weekday, slotIndex)) out.push(m)
  }
  return out
}
