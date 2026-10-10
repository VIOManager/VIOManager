/**
 * 审批中心 mock（骨架：B/C 线产生的审批单汇总）
 * ⚠️ 暂定接口（组长骨架清单未出）：GET /api/approvals、GET /api/approvals/pending-count
 *    字段按 FS5 页面需要预留：bizType/bizId/title/detail/status/发起人/时间/未读
 * TODO(联调)：以骨架审批中心真实接口为准改造
 */
import { fail, ok, registerMock } from './registry'

export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface MockApproval {
  id: string
  bizType: string
  bizId: string
  title: string
  detail: string
  status: ApprovalStatus
  applicantName: string
  createdAt: string
  unread: boolean
  /** 目标处理人视角： memberId（如队员确认）；空=管理员 */
  targetMemberId?: string
}

let seq = 1

export const approvals: MockApproval[] = [
  { id: 'ap-demo1', bizType: 'TEAM_ADD_MEMBER', bizId: 't1', title: '李明轩 · 电波突击队申请加人：王雨桐', detail: '申请将王雨桐加入「电波突击队」', status: 'PENDING', applicantName: '李明轩', createdAt: '2026-10-07 21:14:00', unread: true },
  { id: 'ap-demo2', bizType: 'TEAM_CREATE', bizId: 't2', title: '陈子昂 · 创建赛队「像素工坊」', detail: '比赛：2026 电子设计校内选拔赛 · 软件赛道', status: 'PENDING', applicantName: '陈子昂', createdAt: '2026-10-07 15:02:00', unread: true }
]

/** 供 team/achievement 等 mock 写入新审批单 */
export function pushApproval(a: Omit<MockApproval, 'id' | 'status' | 'createdAt' | 'unread'>): MockApproval {
  const item: MockApproval = { ...a, id: 'ap' + seq++, status: 'PENDING', createdAt: '2026-10-10 12:00:00', unread: true }
  approvals.unshift(item)
  return item
}

registerMock({
  method: 'get',
  path: '/api/approvals',
  reply: () => ok(approvals)
})

registerMock({
  method: 'get',
  path: '/api/approvals/pending-count',
  reply: () => ok({ count: approvals.filter((a) => a.status === 'PENDING' && a.unread).length })
})

registerMock({
  method: 'post',
  path: '/api/approvals/:id/read',
  reply: (ctx) => {
    for (const a of approvals) {
      if (a.id === ctx.params.id) {
        a.unread = false
        return ok(null)
      }
    }
    return fail(1004, '审批单不存在')
  }
})
