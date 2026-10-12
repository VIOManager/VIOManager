/**
 * 学期管理 mock（A 线 S01–S08）
 * ⚠️ 演示数据：三个学期（已归档/进行中/未开始）；切换任务覆盖状态机 PENDING→CANCELLED/SUCCESS
 * 校验：3008 学期名称重复 / 3009 进行中与有关联数据的学期拒删 / 2002 密码二次确认 / 3011 版本冲突
 * 密码校验用 mock/auth 的账号表（超管 admin 的当前密码）
 * TODO(联调)：后端学期接口就绪后删除本文件
 */
import { fail, ok, registerMock } from './registry'
import { findMockUserByToken } from './auth'
import type { Semester, SwitchTask } from '@/api/semester'

let seq = 10
let taskSeq = 1

export const semesters: Semester[] = [
  { id: 's1', name: '2026春季学期', startDate: '2026-03-02', endDate: '2026-07-10', weekCount: 18, status: 'ARCHIVED' },
  { id: 's2', name: '2026秋季学期', startDate: '2026-08-31', endDate: '2027-01-09', weekCount: 18, status: 'ACTIVE' },
  { id: 's3', name: '2027春季学期', startDate: '2027-03-01', endDate: '2027-07-09', weekCount: 18, status: 'PLANNED' }
]

const tasks: SwitchTask[] = []

/** 超管密码二次确认：用当前登录人的 mock 密码核对 */
function checkPassword(token: string, password: string): boolean {
  const u = findMockUserByToken(token)
  return !!u && u.password === password
}

registerMock({
  method: 'get',
  path: '/api/semesters/switch-tasks',
  reply: () => ok(tasks)
})

registerMock({
  method: 'post',
  path: '/api/semesters/switch-tasks',
  reply: (ctx) => {
    if (!checkPassword(ctx.token, String(ctx.body.password ?? ''))) return fail(2002, '密码不正确，已取消发起')
    const toId = String(ctx.body.toSemesterId ?? '')
    let target: Semester | null = null
    for (const s of semesters) {
      if (s.id === toId) target = s
    }
    if (!target) return fail(1004, '目标学期不存在')
    if (target.status === 'ACTIVE') return fail(1001, '目标学期已是进行中')
    for (const t of tasks) {
      if (t.status === 'PENDING') return fail(1001, '已有待执行的切换任务，请先取消或执行')
    }
    const task: SwitchTask = {
      id: 'st' + taskSeq++,
      toSemesterName: target.name,
      status: 'PENDING',
      createdAt: '2026-10-10 10:00:00',
      deadline: '2026-10-10 10:30:00',
      impact: { contestCount: 3, teamCount: 12, memberRelationCount: 186 },
      version: 1
    }
    tasks.unshift(task)
    return ok(task)
  }
})

registerMock({
  method: 'post',
  path: '/api/semesters/switch-tasks/:id/cancel',
  reply: (ctx) => {
    for (const t of tasks) {
      if (t.id !== ctx.params.id) continue
      if (t.status !== 'PENDING') return fail(1001, '任务不在待执行状态')
      t.status = 'CANCELLED'
      return ok(null)
    }
    return fail(1004, '任务不存在')
  }
})

registerMock({
  method: 'post',
  path: '/api/semesters/switch-tasks/:id/execute',
  reply: (ctx) => {
    for (const t of tasks) {
      if (t.id !== ctx.params.id) continue
      if (t.status !== 'PENDING') return fail(1001, '任务不在待执行状态')
      if (Number(ctx.body.version) !== t.version) return fail(3011, '任务版本已变化，请刷新后重试')
      // 执行：旧学期归档、目标学期激活
      for (const s of semesters) {
        if (s.status === 'ACTIVE') s.status = 'ARCHIVED'
        if (s.name === t.toSemesterName) s.status = 'ACTIVE'
      }
      t.status = 'SUCCESS'
      t.version += 1
      return ok(null)
    }
    return fail(1004, '任务不存在')
  }
})

// ⚠️ 注册顺序：switch-tasks 系列必须先于 /:id
registerMock({
  method: 'get',
  path: '/api/semesters',
  reply: () => ok(semesters)
})

registerMock({
  method: 'post',
  path: '/api/semesters',
  reply: (ctx) => {
    const name = String(ctx.body.name ?? '').trim()
    if (!name) return fail(1001, '学期名称不能为空')
    for (const s of semesters) {
      if (s.name === name) return fail(3008, '学期名称已存在')
    }
    const s: Semester = {
      id: 's' + seq++,
      name,
      startDate: String(ctx.body.startDate ?? ''),
      endDate: String(ctx.body.endDate ?? ''),
      weekCount: Number(ctx.body.weekCount ?? 16),
      status: 'PLANNED'
    }
    semesters.push(s)
    return ok(s)
  }
})

registerMock({
  method: 'put',
  path: '/api/semesters/:id',
  reply: (ctx) => {
    for (const s of semesters) {
      if (s.id !== ctx.params.id) continue
      const name = String(ctx.body.name ?? '').trim()
      if (name && name !== s.name) {
        for (const o of semesters) {
          if (o.name === name) return fail(3008, '学期名称已存在')
        }
        s.name = name
      }
      s.startDate = String(ctx.body.startDate ?? s.startDate)
      s.endDate = String(ctx.body.endDate ?? s.endDate)
      s.weekCount = Number(ctx.body.weekCount ?? s.weekCount)
      return ok(s)
    }
    return fail(1004, '学期不存在')
  }
})

registerMock({
  method: 'delete',
  path: '/api/semesters/:id',
  reply: (ctx) => {
    for (const s of semesters) {
      if (s.id !== ctx.params.id) continue
      // 进行中与已归档学期都有关联业务数据（3009）；只有未开始且无数据的能删
      if (s.status !== 'PLANNED') return fail(3009, '学期有关联业务数据，禁止删除')
      semesters.splice(semesters.indexOf(s), 1)
      return ok(null)
    }
    return fail(1004, '学期不存在')
  }
})
