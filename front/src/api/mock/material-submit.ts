/**
 * 材料提交 mock（C 线 MS01–06：概览/我的材料/单条提交/zip 批量/打包下载）
 * 校验：5001 要求与赛队比赛不匹配 / 5002 已过截止 / 5003 非在队在册成员 / 5004 格式不符
 * 联动：提交后重算队伍齐全率与 material_status（赛队列表/详情里的材料状态随之变化）
 * ⚠️ zip 批量为演示模拟：按压缩包文件名包含类型中文名匹配要求，真实解压由后端做
 * TODO(联调)：后端材料提交接口就绪后删除本文件
 */
import { fail, ok, registerMock } from './registry'
import { meAsMember } from './member'
import { teams } from './team-data'
import type { MockTeam } from './team-data'
import { nextSubId, requirements, submissions } from './material-data'
import type { MockTask } from './material-data'
import { allTasks, findTask, toReqView } from './material'
import { teamVisible } from './scope'
import { MATERIAL_TYPE_LABEL } from '@/config/labels'
import type { MaterialSubmitInfo, MyTeamMaterial, TeamMaterialRow } from '@/api/material'

/** 队伍在某要求下的有效提交（覆盖后旧版 valid=false 不计） */
function validSubmission(requirementId: string, teamId: string) {
  for (const s of submissions) {
    if (s.requirementId === requirementId && s.teamId === teamId && s.valid) return s
  }
  return null
}

/** 提交后重算队伍齐全率与材料状态（写入 team-data，赛队列表联动） */
export function recomputeTeam(teamId: string): void {
  for (const tm of teams) {
    if (tm.id !== teamId) continue
    const task = findTaskOfContest(tm)
    if (!task) return
    const reqs = requirements.filter((r) => r.taskId === task.id)
    if (reqs.length === 0) return
    let submitted = 0
    let requiredMissing = 0
    for (const r of reqs) {
      if (validSubmission(r.id, teamId)) submitted += 1
      else if (r.requiredFlag === 1) requiredMissing += 1
    }
    tm.materialCompleteRate = Math.round((submitted / reqs.length) * 100)
    tm.materialStatus = requiredMissing > 0 ? '待补 ' + requiredMissing + ' 项' : '齐全'
  }
}

function findTaskOfContest(tm: MockTeam): MockTask | null {
  for (const t of allTasks()) {
    if (t.contestId === tm.contestId) return t
  }
  return null
}

/** 是否该队伍在队在册成员（CONFIRMED/UNCONFIRMED，PRD §3.7 上传门槛） */
function isActiveMember(tm: MockTeam, memberId: string): boolean {
  for (const m of tm.members) {
    if (m.memberId === memberId && (m.status === 'CONFIRMED' || m.status === 'UNCONFIRMED')) return true
  }
  return false
}

function submitInfoOf(requirementId: string, teamId: string): MaterialSubmitInfo {
  const s = validSubmission(requirementId, teamId)
  if (!s) return { requirementId, status: 'NOT_SUBMITTED', version: 0, fileName: '', uploadedAt: '' }
  return { requirementId, status: 'SUBMITTED', version: s.version, fileName: s.fileName, uploadedAt: s.uploadedAt }
}

// ── MS03 管理端队伍提交概览 ──
registerMock({
  method: 'get',
  path: '/api/material-tasks/:id/overview',
  reply: (ctx) => {
    const task = findTask(ctx.params.id)
    if (!task) return fail(5001, '材料任务不存在')
    const reqs = requirements.filter((r) => r.taskId === task.id)
    const rows: TeamMaterialRow[] = []
    for (const tm of teams) {
      if (tm.contestId !== task.contestId || tm.status !== 'ACTIVE') continue
      // 部门管理员：仅本部门子树队伍（按队长组织归属）
      if (!teamVisible(tm.leaderId, ctx.token)) continue
      rows.push({
        teamId: tm.id, teamName: tm.name, leaderName: tm.leaderName,
        completeRate: tm.materialCompleteRate, statusText: tm.materialStatus,
        items: reqs.map((r) => submitInfoOf(r.id, tm.id))
      })
    }
    return ok(rows)
  }
})

// ── MS04 学生端我的材料 ──
registerMock({
  method: 'get',
  path: '/api/my/materials',
  reply: (ctx) => {
    const me = meAsMember(ctx.token)
    const out: MyTeamMaterial[] = []
    if (!me) return ok(out)
    for (const tm of teams) {
      if (tm.status !== 'ACTIVE' || !isActiveMember(tm, me.id)) continue
      const task = findTaskOfContest(tm)
      if (!task) continue
      const reqs = requirements.filter((r) => r.taskId === task.id)
      let requiredMissing = 0
      const items = reqs.map((r) => {
        if (!validSubmission(r.id, tm.id) && r.requiredFlag === 1) requiredMissing += 1
        return { requirement: toReqView(r), ...submitInfoOf(r.id, tm.id) }
      })
      out.push({
        teamId: tm.id, teamName: tm.name,
        task: { id: task.id, title: task.name, deadline: task.deadline },
        completeRate: tm.materialCompleteRate, requiredMissing, items
      })
    }
    return ok(out)
  }
})

// ── MS01 单条提交/覆盖上传 ──
registerMock({
  method: 'post',
  path: '/api/material-submissions',
  reply: (ctx) => {
    const requirementId = String(ctx.body.requirementId ?? '')
    const teamId = String(ctx.body.teamId ?? '')
    const fileName = String(ctx.body.fileName ?? '')
    const r = requirements.find((x) => x.id === requirementId)
    if (!r) return fail(5001, '材料要求不存在')
    const tm = teams.find((x) => x.id === teamId)
    if (!tm) return fail(1004, '赛队不存在')
    const task = findTask(r.taskId)
    if (!task) return fail(5001, '材料任务不存在')
    if (task.contestId !== tm.contestId) return fail(5001, '材料要求与赛队所属比赛不匹配')
    if (new Date(task.deadline.replace(' ', 'T')).getTime() < Date.now()) return fail(5002, '已过截止时间，禁止提交或覆盖')
    const me = meAsMember(ctx.token)
    if (!me || !isActiveMember(tm, me.id)) return fail(5003, '仅本队在册成员可上传材料')
    const ext = fileName.indexOf('.') >= 0 ? fileName.split('.').pop()!.toLowerCase() : ''
    if (r.allowedFormats.indexOf(ext) < 0) return fail(5004, '文件格式不符，允许格式：' + r.allowedFormats.join(' / '))
    // 覆盖上传：旧版本失效、版本号 +1
    let version = 1
    for (const s of submissions) {
      if (s.requirementId === requirementId && s.teamId === teamId && s.valid) {
        s.valid = false
        version = s.version + 1
      }
    }
    submissions.push({
      id: nextSubId(), requirementId, teamId, fileId: String(ctx.body.fileId ?? ''),
      fileName, version, uploadedBy: me.name, uploadedAt: '2026-10-10 12:00:00', valid: true
    })
    recomputeTeam(teamId)
    return ok({ version })
  }
})

// ── MS05 zip 批量上传（演示模拟解压）──
registerMock({
  method: 'post',
  path: '/api/material-submissions/batch',
  reply: (ctx) => {
    const teamId = String(ctx.body.teamId ?? '')
    const taskId = String(ctx.body.taskId ?? '')
    const fileName = String(ctx.body.fileName ?? '')
    const task = findTask(taskId)
    if (!task) return fail(5001, '材料任务不存在')
    const tm = teams.find((x) => x.id === teamId)
    if (!tm) return fail(1004, '赛队不存在')
    if (task.contestId !== tm.contestId) return fail(5001, '材料任务与赛队所属比赛不匹配')
    if (new Date(task.deadline.replace(' ', 'T')).getTime() < Date.now()) return fail(5002, '已过截止时间，禁止提交')
    const me = meAsMember(ctx.token)
    if (!me || !isActiveMember(tm, me.id)) return fail(5003, '仅本队在册成员可上传材料')
    if (fileName.toLowerCase().lastIndexOf('.zip') !== fileName.length - 4) return fail(5004, '批量上传仅支持 zip 压缩包')
    // 演示模拟：包名含类型中文名即视为包含该材料（如 "承诺书+合照.zip"）
    const base = fileName.slice(0, fileName.length - 4)
    const matched: Array<{ requirementId: string; type: string }> = []
    for (const r of requirements) {
      if (r.taskId !== taskId || validSubmission(r.id, teamId)) continue
      if (base.indexOf(MATERIAL_TYPE_LABEL[r.materialType] ?? '') >= 0) {
        submissions.push({
          id: nextSubId(), requirementId: r.id, teamId, fileId: String(ctx.body.fileId ?? ''),
          fileName: tm.name + '-' + MATERIAL_TYPE_LABEL[r.materialType] + '.pdf', version: 1,
          uploadedBy: me.name, uploadedAt: '2026-10-10 12:00:00', valid: true
        })
        matched.push({ requirementId: r.id, type: r.materialType })
      }
    }
    recomputeTeam(teamId)
    // 固定演示一条不匹配文件，展示"不匹配文件清单"交互
    return ok({ matched, unmatched: ['说明.txt'] })
  }
})

// ── MS06 打包下载（mock 只回提示，真实后端为文件流）──
registerMock({
  method: 'post',
  path: '/api/material-tasks/:id/export',
  reply: (ctx) => {
    const task = findTask(ctx.params.id)
    if (!task) return fail(5001, '材料任务不存在')
    const teamId = String(ctx.body.teamId ?? '')
    const tm = teams.find((x) => x.id === teamId)
    if (!tm) return fail(1004, '赛队不存在')
    return ok({ note: '「' + tm.name + '」材料打包任务已生成（mock 演示，联调后为真实下载流）' })
  }
})
