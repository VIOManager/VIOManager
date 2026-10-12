/**
 * 课表导入 mock 接口层（TT01–TT08，契约：《排班管理-接口清单》§2）
 * 数据在 ./schedule-data
 * 同步解析（拍板②）：TT02 上传即解析完返回成败数；学期名归一化失败留 PENDING 等 TT05 人工指定
 * 幂等：TT02 上传头带 X-Idempotency-Key（缺 1001、重复 1005）
 * TODO(联调)：后端排班接口就绪后删除本文件
 */
import { fail, ok, registerMock } from './registry'
import { findMockUserByToken } from './auth'
import { usedIdemKeys } from './approval-data'
import { nowText } from './approval-data'
import { semesters } from './semester'
import { classMappings, courses, importBatches } from './schedule-data'

let batchSeq = 3
let mappingSeq = 5

function adminOnly(token: string): boolean {
  const u = findMockUserByToken(token)
  return !!u && u.role !== 'STUDENT'
}

function semesterName(id: string): string {
  for (const s of semesters) {
    if (s.id === id) return s.name
  }
  return ''
}

registerMock({
  method: 'get',
  path: '/api/timetables/import-template',
  reply: (ctx) => {
    if (!adminOnly(ctx.token)) return fail(1003, '仅管理员可下载模板')
    return ok({ note: '课表导入模板下载（16 列 + 5 条示例行，mock 演示不落文件）' })
  }
})

registerMock({
  method: 'post',
  path: '/api/timetables/imports',
  reply: (ctx) => {
    if (!adminOnly(ctx.token)) return fail(1003, '仅管理员可导入课表')
    if (!ctx.idemKey) return fail(1001, '缺少幂等键（X-Idempotency-Key）')
    if (usedIdemKeys.has(ctx.idemKey)) return fail(1005, '请勿重复提交')
    usedIdemKeys.add(ctx.idemKey)
    // 同步解析演示：默认归一化到当前 ACTIVE 学期，152 行成 150 败 2，并产生 1 条待确认新映射
    const sem = semesters.find((s) => s.status === 'ACTIVE') ?? semesters[1]
    const batch = {
      id: 'tb' + batchSeq++, semesterId: sem.id, status: 'MAPPING' as const,
      totalCount: 152, successCount: 150, failCount: 2, createdAt: nowText(),
      errors: [
        { rowNo: 23, reason: '课程名为空' },
        { rowNo: 88, reason: '星期取值超出 1–7' }
      ]
    }
    importBatches.unshift(batch)
    classMappings.push({ id: 'cm' + mappingSeq++, semesterId: sem.id, classKey: '软工23-3班', className: '软工23-3班', grade: '2023', gradeSource: '学号推导', confirmed: false })
    return ok({ batchId: batch.id, totalCount: batch.totalCount, successCount: batch.successCount, failCount: batch.failCount })
  }
})

registerMock({
  method: 'get',
  path: '/api/timetables/imports',
  reply: (ctx) => {
    if (!adminOnly(ctx.token)) return fail(1003, '仅管理员可查看导入批次')
    const semesterId = String(ctx.query.semesterId ?? '')
    const out = []
    for (const b of importBatches) {
      if (semesterId && b.semesterId !== semesterId) continue
      out.push({ id: b.id, semesterId: b.semesterId, semesterName: semesterName(b.semesterId), status: b.status, totalCount: b.totalCount, successCount: b.successCount, failCount: b.failCount, createdAt: b.createdAt })
    }
    return ok(out)
  }
})

registerMock({
  method: 'get',
  path: '/api/timetables/imports/:id',
  reply: (ctx) => {
    if (!adminOnly(ctx.token)) return fail(1003, '仅管理员可查看导入批次')
    for (const b of importBatches) {
      if (b.id === ctx.params.id) {
        return ok({ id: b.id, semesterId: b.semesterId, semesterName: semesterName(b.semesterId), status: b.status, totalCount: b.totalCount, successCount: b.successCount, failCount: b.failCount, createdAt: b.createdAt, errors: b.errors })
      }
    }
    return fail(1004, '导入批次不存在')
  }
})

registerMock({
  method: 'put',
  path: '/api/timetables/imports/:id/semester',
  reply: (ctx) => {
    if (!adminOnly(ctx.token)) return fail(1003, '仅管理员可指定学期')
    for (const b of importBatches) {
      if (b.id !== ctx.params.id) continue
      if (b.status !== 'PENDING' || b.semesterId) return fail(8003, '导入批次当前状态不允许该操作')
      const semesterId = String(ctx.body.semesterId ?? '')
      if (!semesterName(semesterId)) return fail(1004, '学期不存在')
      // 指定学期后续跑解析（同步）：转 MAPPING 并生成演示映射
      b.semesterId = semesterId
      b.status = 'MAPPING'
      b.totalCount = 96
      b.successCount = 96
      b.failCount = 0
      classMappings.push({ id: 'cm' + mappingSeq++, semesterId, classKey: '软工24-1班', className: '软工24-1班', grade: '2024', gradeSource: '学号推导', confirmed: false })
      return ok(null)
    }
    return fail(1004, '导入批次不存在')
  }
})

registerMock({
  method: 'get',
  path: '/api/timetables/class-mappings',
  reply: (ctx) => {
    if (!adminOnly(ctx.token)) return fail(1003, '仅管理员可查看班级映射')
    const semesterId = String(ctx.query.semesterId ?? '')
    const confirmed = String(ctx.query.confirmed ?? '')
    const out = []
    for (const m of classMappings) {
      if (semesterId && m.semesterId !== semesterId) continue
      if (confirmed === 'true' && !m.confirmed) continue
      if (confirmed === 'false' && m.confirmed) continue
      out.push({ ...m })
    }
    return ok(out)
  }
})

registerMock({
  method: 'put',
  path: '/api/timetables/class-mappings/confirm',
  reply: (ctx) => {
    if (!adminOnly(ctx.token)) return fail(1003, '仅管理员可确认映射')
    const ids = Array.isArray(ctx.body.ids) ? (ctx.body.ids as string[]) : []
    let hit = 0
    for (const id of ids) {
      for (const m of classMappings) {
        if (m.id === id) {
          m.confirmed = true
          hit++
        }
      }
    }
    if (hit === 0 && ids.length > 0) return fail(8004, '班级映射不存在')
    return ok(null)
  }
})

registerMock({
  method: 'get',
  path: '/api/timetables/courses',
  reply: (ctx) => {
    if (!adminOnly(ctx.token)) return fail(1003, '仅管理员可查课表')
    const classKey = String(ctx.query.classKey ?? '')
    const out = []
    for (const c of courses) {
      if (classKey && c.classKey !== classKey) continue
      out.push({ id: c.id, weekNo: 0, weekday: c.weekday, slotIndex: c.slotIndex, classKey: c.classKey, courseName: c.courseName, teacher: c.teacher, location: c.location, weekParity: c.weekParity })
    }
    return ok(out)
  }
})
