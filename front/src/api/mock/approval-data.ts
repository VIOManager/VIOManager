/**
 * 审批中心 mock 数据层（从 mock/approval.ts 拆出，对齐 team.ts/team-data.ts 的分层惯例）
 * 对应契约：《审批中心-接口清单》（错误码 6xxx）
 * 关键机制：
 *   1. 业务线对接 SPI：registerApprovalHandler 注册 onApproved/onRejected 回调，
 *      模拟后端 ApprovalHandler 按 bizType 路由（未注册的类型落兜底：不动作，对应后端 FallbackApprovalHandler）
 *   2. 已读三位：readApplicant/readTarget/readApprover，P07 只置当前视角对应位
 *   3. 已用幂等键集合：模拟规范 §6"重复键拦截返回 1005 不回放"
 * ⚠️ 演示数据：approverOrgId 为审批责任组织（队伍单据=队长所在组织，2026-10-10 拍板），
 *    部门管理员 TODO 视角按它做子树裁剪（mock/scope.ts）
 * TODO(联调)：后端审批中心就绪后删除本文件
 */
import type { ApprovalStatus } from '@/api/approval'

/** 审批流水行（对应表 approval_record） */
export interface MockApprovalRecord {
  action: string
  operatorName: string
  operatorRole: string
  commentText: string
  createdAt: string
}

/** 审批单（对应表 approval + 三位已读） */
export interface MockApproval {
  id: string
  bizType: string
  /** 更新型=既有业务对象 ID；创建型提交时为 null，批准回调里回填（清单 §5 契约 3） */
  bizId: string | null
  bizTitle: string
  status: ApprovalStatus
  applicantMemberId: string
  applicantName: string
  targetMemberId: string
  targetMemberName: string
  /** 审批责任组织 ID：决定哪个部门管理员收到待办 */
  approverOrgId: string
  applyReason: string
  /** 申请快照 JSON 字符串（详情展示 + 回调解析） */
  payloadJson: string
  handleComment: string
  handledBy: string
  handledAt: string
  createdAt: string
  readApplicant: boolean
  readTarget: boolean
  readApprover: boolean
  records: MockApprovalRecord[]
}

/** 业务线回调 SPI（模拟后端 ApprovalHandler；函数即 Bean，按 bizType 登记） */
export interface ApprovalHandler {
  onApproved?: (a: MockApproval) => void
  onRejected?: (a: MockApproval) => void
}

const handlers: Record<string, ApprovalHandler> = {}

/** 业务线 mock 在模块加载时注册回调（team.ts / achievement.ts） */
export function registerApprovalHandler(bizType: string, h: ApprovalHandler): void {
  handlers[bizType] = h
}

/** 单据终结后触发回调；未注册类型落兜底不动作（模拟后端 FallbackApprovalHandler 只记日志） */
export function fireSettled(a: MockApproval): void {
  const h = handlers[a.bizType]
  if (!h) return
  if (a.status === 'APPROVED' && h.onApproved) h.onApproved(a)
  if (a.status === 'REJECTED' && h.onRejected) h.onRejected(a)
}

/** 已用幂等键（审批写操作共享；重复键回 1005） */
export const usedIdemKeys = new Set<string>()

/** 当前时间文本（操作用真实时刻，区别于种子数据的固定演示时间） */
export function nowText(): string {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return (
    d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) +
    ' ' + p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds())
  )
}

/** 追加工厂流水 */
export function addRecord(a: MockApproval, action: string, operatorName: string, operatorRole: string, commentText: string): void {
  a.records.push({ action, operatorName, operatorRole, commentText, createdAt: nowText() })
}

let seq = 7

/** 供 team/achievement 等 mock 写入新审批单（进程内对接，对应后端 ApprovalClient.createApproval） */
export interface PushApprovalInput {
  bizType: string
  bizId: string | null
  bizTitle: string
  applyReason: string
  payload?: Record<string, unknown>
  applicantMemberId: string
  applicantName: string
  approverOrgId: string
  targetMemberId?: string
  targetMemberName?: string
}

export function pushApproval(input: PushApprovalInput): MockApproval {
  const item: MockApproval = {
    id: 'ap' + seq++,
    bizType: input.bizType,
    bizId: input.bizId,
    bizTitle: input.bizTitle,
    status: 'PENDING',
    applicantMemberId: input.applicantMemberId,
    applicantName: input.applicantName,
    targetMemberId: input.targetMemberId ?? '',
    targetMemberName: input.targetMemberName ?? '',
    approverOrgId: input.approverOrgId,
    applyReason: input.applyReason,
    payloadJson: input.payload ? JSON.stringify(input.payload) : '',
    handleComment: '',
    handledBy: '',
    handledAt: '',
    createdAt: nowText(),
    readApplicant: true,
    readTarget: false,
    readApprover: false,
    records: []
  }
  addRecord(item, 'CREATE', input.applicantName, 'APPLICANT', input.applyReason)
  approvals.unshift(item)
  return item
}

/** 业务线自审接口（如成果 review）同步终结关联审批单：只置状态与流水，不触发回调（业务数据已自改） */
export function settleByBiz(bizId: string, result: 'APPROVED' | 'REJECTED', operatorName: string, comment: string): void {
  for (const a of approvals) {
    if (a.bizId !== bizId || a.status !== 'PENDING') continue
    a.status = result
    a.handleComment = comment
    a.handledBy = operatorName
    a.handledAt = nowText()
    a.readApprover = true
    a.readApplicant = false
    addRecord(a, result === 'APPROVED' ? 'APPROVE' : 'REJECT', operatorName, 'APPROVER', comment)
    return
  }
}

/** 4007 复核：同业务对象同目标人是否已有 PENDING 的指定类型审批单（payload 快照比对 memberId） */
export function hasPendingApproval(bizType: string, bizId: string, memberId: string): boolean {
  for (const a of approvals) {
    if (a.bizType !== bizType || a.bizId !== bizId || a.status !== 'PENDING') continue
    try {
      const p = JSON.parse(a.payloadJson || '{}') as Record<string, unknown>
      if (String(p.memberId ?? '') === memberId) return true
    } catch {
      // 坏快照当不匹配
    }
  }
  return false
}

/** 种子数据见下方：覆盖三视角与各终态（ap1/ap5 待办红点，ap4 结果红点，ap2 跨部门不可见，ap6 历史已通过，
 *  ap8 指导教师确认单——目标人是教师兼部门管理员账号，验参与人视角不受队长类型过滤，2026-10-25）
 *  TEAM_CREATE 种子已随队长直建免审批拍板（2026-10-24）转为成果提交演示，该类型不再有活单 */
export const approvals: MockApproval[] = [
  {
    id: 'ap8', bizType: 'TEAM_ADD_ADVISOR', bizId: 't1',
    bizTitle: '李明轩 · 电波突击队申请指导教师：陈志远',
    status: 'PENDING',
    applicantMemberId: 'm5', applicantName: '李明轩',
    targetMemberId: 'm2', targetMemberName: '陈志远',
    approverOrgId: 'o11',
    applyReason: '申请教师陈志远指导「电波突击队」',
    payloadJson: JSON.stringify({ teamId: 't1', memberId: 'm2', memberName: '陈志远' }),
    handleComment: '', handledBy: '', handledAt: '',
    createdAt: '2026-10-25 10:00:00',
    readApplicant: true, readTarget: false, readApprover: false,
    records: [
      { action: 'CREATE', operatorName: '李明轩', operatorRole: 'APPLICANT', commentText: '申请教师陈志远指导「电波突击队」', createdAt: '2026-10-25 10:00:00' }
    ]
  },
  {
    id: 'ap1', bizType: 'TEAM_ADD_MEMBER', bizId: 't1',
    bizTitle: '李明轩 · 电波突击队申请加人：王雨桐',
    status: 'PENDING',
    applicantMemberId: 'm5', applicantName: '李明轩',
    targetMemberId: 'm7', targetMemberName: '王雨桐',
    approverOrgId: 'o11',
    applyReason: '申请将王雨桐加入「电波突击队」',
    payloadJson: JSON.stringify({ teamId: 't1', memberId: 'm7', memberName: '王雨桐' }),
    handleComment: '', handledBy: '', handledAt: '',
    createdAt: '2026-10-07 21:14:00',
    readApplicant: true, readTarget: false, readApprover: false,
    records: [
      { action: 'CREATE', operatorName: '李明轩', operatorRole: 'APPLICANT', commentText: '申请将王雨桐加入「电波突击队」', createdAt: '2026-10-07 21:14:00' }
    ]
  },
  {
    id: 'ap2', bizType: 'ACHIEVEMENT_SUBMIT', bizId: 'a7',
    bizTitle: '陈子昂 · 提交成果：基于 AIGC 的校园海报生成系统',
    status: 'PENDING',
    applicantMemberId: 'm4', applicantName: '陈子昂',
    targetMemberId: '', targetMemberName: '',
    approverOrgId: 'o13',
    applyReason: '评级成果 · 校级优秀 · 待管理员审批',
    payloadJson: JSON.stringify({ achievementId: 'a7' }),
    handleComment: '', handledBy: '', handledAt: '',
    createdAt: '2026-10-07 15:02:00',
    readApplicant: true, readTarget: true, readApprover: false,
    records: [
      { action: 'CREATE', operatorName: '陈子昂', operatorRole: 'APPLICANT', commentText: '提交成果「基于 AIGC 的校园海报生成系统」待审批', createdAt: '2026-10-07 15:02:00' }
    ]
  },
  {
    id: 'ap3', bizType: 'ACHIEVEMENT_SUBMIT', bizId: 'a3',
    bizTitle: '李明轩 · 电波突击队提交成果：智能循迹小车',
    status: 'PENDING',
    applicantMemberId: 'm5', applicantName: '李明轩',
    targetMemberId: '', targetMemberName: '',
    approverOrgId: 'o11',
    applyReason: '评级成果 · 校级优秀 · 待管理员审批',
    payloadJson: JSON.stringify({ achievementId: 'a3' }),
    handleComment: '', handledBy: '', handledAt: '',
    createdAt: '2026-10-08 19:30:00',
    readApplicant: true, readTarget: true, readApprover: false,
    records: [
      { action: 'CREATE', operatorName: '李明轩', operatorRole: 'APPLICANT', commentText: '提交成果「智能循迹小车」待审批', createdAt: '2026-10-08 19:30:00' }
    ]
  },
  {
    id: 'ap4', bizType: 'ACHIEVEMENT_SUBMIT', bizId: 'a9',
    bizTitle: '王雨桐 · 提交成果：校园二手书交易平台',
    status: 'REJECTED',
    applicantMemberId: 'm7', applicantName: '王雨桐',
    targetMemberId: '', targetMemberName: '',
    approverOrgId: 'o11',
    applyReason: '评级成果 · 校级优秀 · 待管理员审批',
    payloadJson: JSON.stringify({ achievementId: 'a9' }),
    handleComment: '佐证材料不完整，缺获奖证书扫描件，补齐后重新提交', handledBy: '陈志远', handledAt: '2026-10-08 10:20:00',
    createdAt: '2026-10-07 20:05:00',
    readApplicant: false, readTarget: true, readApprover: true,
    records: [
      { action: 'CREATE', operatorName: '王雨桐', operatorRole: 'APPLICANT', commentText: '提交成果「校园二手书交易平台」待审批', createdAt: '2026-10-07 20:05:00' },
      { action: 'REJECT', operatorName: '陈志远', operatorRole: 'APPROVER', commentText: '佐证材料不完整，缺获奖证书扫描件，补齐后重新提交', createdAt: '2026-10-08 10:20:00' }
    ]
  },
  {
    id: 'ap5', bizType: 'TEAM_ADD_MEMBER', bizId: 't2',
    bizTitle: '孙嘉仪 · 像素工坊申请加人：周子墨',
    status: 'PENDING',
    applicantMemberId: 'm6', applicantName: '孙嘉仪',
    targetMemberId: 'm8', targetMemberName: '周子墨',
    approverOrgId: 'o111',
    applyReason: '申请将周子墨加入「像素工坊」',
    payloadJson: JSON.stringify({ teamId: 't2', memberId: 'm8', memberName: '周子墨' }),
    handleComment: '', handledBy: '', handledAt: '',
    createdAt: '2026-10-09 09:12:00',
    readApplicant: true, readTarget: false, readApprover: false,
    records: [
      { action: 'CREATE', operatorName: '孙嘉仪', operatorRole: 'APPLICANT', commentText: '申请将周子墨加入「像素工坊」', createdAt: '2026-10-09 09:12:00' }
    ]
  },
  {
    id: 'ap6', bizType: 'TEAM_ADD_ADVISOR', bizId: 't1',
    bizTitle: '李明轩 · 电波突击队申请指导教师：李明',
    status: 'APPROVED',
    applicantMemberId: 'm5', applicantName: '李明轩',
    targetMemberId: '', targetMemberName: '',
    approverOrgId: 'o11',
    applyReason: '申请教师李明指导「电波突击队」',
    payloadJson: JSON.stringify({ teamId: 't1', memberId: 'm1', memberName: '李明' }),
    handleComment: '同意', handledBy: '陈志远', handledAt: '2026-10-06 09:30:00',
    createdAt: '2026-10-05 20:10:00',
    readApplicant: true, readTarget: true, readApprover: true,
    records: [
      { action: 'CREATE', operatorName: '李明轩', operatorRole: 'APPLICANT', commentText: '申请教师李明指导「电波突击队」', createdAt: '2026-10-05 20:10:00' },
      { action: 'APPROVE', operatorName: '陈志远', operatorRole: 'APPROVER', commentText: '同意', createdAt: '2026-10-06 09:30:00' }
    ]
  }
]
