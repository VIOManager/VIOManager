/**
 * 项目线审批回调（模拟后端 ApprovalHandler Bean，契约：《项目管理-接口清单》§3.4/§3.7）
 * MILESTONE_APPROVE 通过 → 节点转 APPROVED + 给挂载该比赛的每个 ACTIVE 项目同步生成任务（防重：同节点不重复建）
 * PROJECT_VIEW 通过 → 授权转 ACTIVE（LIMITED 到期时间生效）；驳回 → 授权失效
 * TODO(联调)：后端审批回调就绪后删除本文件
 */
import { registerApprovalHandler, nowText } from './approval-data'
import { milestones, projectTasks, teamProjectRefs, viewGrants } from './project-data'
import { projects } from './project-data'
import { teams } from './team-data'

let taskSeq = 100

/** 解析审批快照 */
function payloadOf(raw: string): Record<string, unknown> {
  try {
    return JSON.parse(raw || '{}') as Record<string, unknown>
  } catch {
    return {}
  }
}

registerApprovalHandler('MILESTONE_APPROVE', {
  onApproved: (a) => {
    const p = payloadOf(a.payloadJson)
    const m = milestones.find((x) => x.id === String(p.milestoneId ?? ''))
    if (!m || m.status === 'APPROVED') return
    m.status = 'APPROVED'
    m.approvedAt = nowText()
    m.approvedBy = a.handledBy
    // 同步：挂载该比赛的 ACTIVE 赛队 → 其 ACTIVE 项目各建一条节点任务
    const projectIds: string[] = []
    for (const r of teamProjectRefs) {
      if (r.status !== 'ACTIVE') continue
      const t = teams.find((x) => x.id === r.teamId)
      if (!t || t.contestId !== m.contestId) continue
      const proj = projects.find((x) => x.id === r.projectId && x.status === 'ACTIVE')
      if (proj && projectIds.indexOf(proj.id) < 0) projectIds.push(proj.id)
    }
    for (const pid of projectIds) {
      let dup = false
      for (const t of projectTasks) {
        if (t.projectId === pid && t.milestoneNodeId === m.id) dup = true
      }
      if (dup) continue
      projectTasks.push({
        id: 'pt' + taskSeq++, projectId: pid, title: m.name, description: m.description,
        assigneeMemberId: '', assigneeName: '', dueDate: m.nodeTime, priority: 'HIGH', status: 'TODO',
        sortNo: 90, milestoneNodeId: m.id, completedAt: '', completedByName: ''
      })
    }
    m.synced = true
  },
  onRejected: (a) => {
    const p = payloadOf(a.payloadJson)
    const m = milestones.find((x) => x.id === String(p.milestoneId ?? ''))
    if (!m) return
    m.status = 'REJECTED'
    m.rejectReason = a.handleComment
  }
})

registerApprovalHandler('PROJECT_VIEW', {
  onApproved: (a) => {
    const p = payloadOf(a.payloadJson)
    const g = viewGrants.find((x) => x.id === String(p.grantId ?? ''))
    if (!g) return
    g.status = 'ACTIVE'
  },
  onRejected: (a) => {
    const p = payloadOf(a.payloadJson)
    const g = viewGrants.find((x) => x.id === String(p.grantId ?? ''))
    if (!g) return
    g.status = 'EXPIRED'
  }
})
