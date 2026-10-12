/**
 * 材料 mock（C 线 MT01–04 任务 / MR01–03 要求）
 * 校验：5001 比赛不存在或要求与赛队比赛不匹配 / 5005 要求已有提交记录拒删 / 5006 任务下已有提交记录拒删
 * 提交与概览处理器在 mock/material-submit.ts（MS01–06）
 * TODO(联调)：后端材料接口就绪后删除本文件
 */
import { fail, ok, registerMock } from './registry'
import { contests } from './contest-data'
import { teams } from './team-data'
import { nextReqId, nextTaskId, requirements, submissions, archivedTasks, tasks } from './material-data'
import type { MockTask } from './material-data'
import { MATERIAL_TYPES } from '@/config/labels'
import type { MaterialRequirement, MaterialTask } from '@/api/material'

/** 全部任务（进行中 + 归档） */
export function allTasks(): MockTask[] {
  return tasks.concat(archivedTasks)
}

export function findTask(id: string): MockTask | null {
  for (const t of allTasks()) {
    if (t.id === id) return t
  }
  return null
}

export function findRequirement(id: string) {
  for (const r of requirements) {
    if (r.id === id) return r
  }
  return null
}

/** 任务视图：附加要求数与队伍交齐统计（teamComplete 借用赛队齐全率字段） */
export function toTaskView(t: MockTask): MaterialTask {
  let contestName = ''
  let reqCount = 0
  let teamTotal = 0
  let teamComplete = 0
  for (const c of contests) {
    if (c.id === t.contestId) contestName = c.name
  }
  for (const r of requirements) {
    if (r.taskId === t.id) reqCount += 1
  }
  for (const tm of teams) {
    if (tm.contestId !== t.contestId || tm.status !== 'ACTIVE') continue
    teamTotal += 1
    if (tm.materialCompleteRate >= 100) teamComplete += 1
  }
  return {
    id: t.id, contestId: t.contestId, contestName: contestName, title: t.title, deadline: t.deadline,
    requirementCount: reqCount, teamTotal: teamTotal, teamComplete: teamComplete, createdAt: t.createdAt
  }
}

export function toReqView(r: MaterialRequirement): MaterialRequirement {
  return { id: r.id, taskId: r.taskId, type: r.type, required: r.required, formats: r.formats.slice(), namingRule: r.namingRule, maxSizeMb: r.maxSizeMb }
}

/** 要求表单校验（PC1 验收：要求表单校验） */
function validateRequirement(body: Record<string, unknown>): string | null {
  const type = String(body.type ?? '')
  let typeOk = false
  for (const t of MATERIAL_TYPES) {
    if (t.value === type) typeOk = true
  }
  if (!typeOk) return '材料类型必须是 12 种预设之一'
  const formats = Array.isArray(body.formats) ? (body.formats as string[]) : []
  if (formats.length === 0) return '至少允许一种文件格式'
  const maxMb = Number(body.maxSizeMb)
  if (!maxMb || maxMb < 1 || maxMb > 200) return '单文件大小限制须在 1–200MB 之间'
  return null
}

// ── MT：任务 ──
registerMock({
  method: 'get',
  path: '/api/material-tasks',
  reply: (ctx) => {
    const contestId = String(ctx.query.contestId ?? '')
    const out: MaterialTask[] = []
    for (const t of allTasks()) {
      if (contestId && t.contestId !== contestId) continue
      out.push(toTaskView(t))
    }
    return ok(out)
  }
})

registerMock({
  method: 'post',
  path: '/api/material-tasks',
  reply: (ctx) => {
    const contestId = String(ctx.body.contestId ?? '')
    const title = String(ctx.body.title ?? '').trim()
    const deadline = String(ctx.body.deadline ?? '').trim()
    if (!title || !deadline) return fail(1001, '任务名与截止时间不能为空')
    let contestExists = false
    for (const c of contests) {
      if (c.id === contestId) contestExists = true
    }
    if (!contestExists) return fail(5001, '所选比赛不存在')
    for (const t of allTasks()) {
      if (t.contestId === contestId) return fail(5001, '该比赛已配置材料任务，请直接维护其要求')
    }
    const task: MockTask = { id: nextTaskId(), contestId, title, deadline, createdAt: '2026-10-10 10:00:00' }
    tasks.push(task)
    return ok(toTaskView(task))
  }
})

registerMock({
  method: 'put',
  path: '/api/material-tasks/:id',
  reply: (ctx) => {
    const t = findTask(ctx.params.id)
    if (!t) return fail(5001, '材料任务不存在')
    const title = String(ctx.body.title ?? '').trim()
    const deadline = String(ctx.body.deadline ?? '').trim()
    if (!title || !deadline) return fail(1001, '任务名与截止时间不能为空')
    t.title = title
    t.deadline = deadline
    return ok(null)
  }
})

registerMock({
  method: 'delete',
  path: '/api/material-tasks/:id',
  reply: (ctx) => {
    const t = findTask(ctx.params.id)
    if (!t) return fail(5001, '材料任务不存在')
    // 5006：任务下任一要求已有提交记录则拒删（PRD §3.7）
    for (const r of requirements) {
      if (r.taskId !== t.id) continue
      for (const s of submissions) {
        if (s.requirementId === r.id) return fail(5006, '该任务已有材料提交记录，禁止删除')
      }
    }
    // 一并移除其下未交过的要求，保持数据一致
    for (let i = requirements.length - 1; i >= 0; i--) {
      if (requirements[i].taskId === t.id) requirements.splice(i, 1)
    }
    const idx = tasks.indexOf(t)
    if (idx >= 0) tasks.splice(idx, 1)
    const aIdx = archivedTasks.indexOf(t)
    if (aIdx >= 0) archivedTasks.splice(aIdx, 1)
    return ok(null)
  }
})

// ── MR：要求 ──
registerMock({
  method: 'get',
  path: '/api/material-tasks/:id/requirements',
  reply: (ctx) => {
    const t = findTask(ctx.params.id)
    if (!t) return fail(5001, '材料任务不存在')
    const out: MaterialRequirement[] = []
    for (const r of requirements) {
      if (r.taskId === t.id) out.push(toReqView(r))
    }
    return ok(out)
  }
})

registerMock({
  method: 'post',
  path: '/api/material-tasks/:id/requirements',
  reply: (ctx) => {
    const t = findTask(ctx.params.id)
    if (!t) return fail(5001, '材料任务不存在')
    const err = validateRequirement(ctx.body)
    if (err) return fail(1001, err)
    const created = {
      id: nextReqId(), taskId: t.id,
      type: String(ctx.body.type), required: ctx.body.required === true,
      formats: (ctx.body.formats as string[]).map((f) => f.toLowerCase()),
      namingRule: String(ctx.body.namingRule ?? ''), maxSizeMb: Number(ctx.body.maxSizeMb)
    }
    requirements.push(created)
    return ok(toReqView(created))
  }
})

registerMock({
  method: 'put',
  path: '/api/requirements/:id',
  reply: (ctx) => {
    const r = findRequirement(ctx.params.id)
    if (!r) return fail(5001, '材料要求不存在')
    const err = validateRequirement(ctx.body)
    if (err) return fail(1001, err)
    r.type = String(ctx.body.type)
    r.required = ctx.body.required === true
    r.formats = (ctx.body.formats as string[]).map((f) => f.toLowerCase())
    r.namingRule = String(ctx.body.namingRule ?? '')
    r.maxSizeMb = Number(ctx.body.maxSizeMb)
    return ok(null)
  }
})

registerMock({
  method: 'delete',
  path: '/api/requirements/:id',
  reply: (ctx) => {
    const r = findRequirement(ctx.params.id)
    if (!r) return fail(5001, '材料要求不存在')
    // 5005：已有提交记录的要求拒删（PRD §3.7）
    for (const s of submissions) {
      if (s.requirementId === r.id) return fail(5005, '该要求已有提交记录，禁止删除')
    }
    requirements.splice(requirements.indexOf(r), 1)
    return ok(null)
  }
})
