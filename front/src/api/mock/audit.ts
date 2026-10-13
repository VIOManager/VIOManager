/**
 * 审计查询 mock（AQ01–AQ03，契约：docs/api/审计查询-接口清单.md）
 * 纯只读：写入侧由骨架 AOP 承担（mock 不模拟写入，种子数据代表"各线已产生的日志"）
 * 数据范围：超管全量；部门管理员按操作人所属组织子树过滤；学生 1003
 * TODO(联调)：后端审计接口就绪后删除本文件
 */
import { fail, ok, registerMock } from './registry'
import { findMockUserByToken } from './auth'
import { deptScopeOrgIds, orgInScope } from './scope'
import type { AuditLogDetail } from '@/api/audit'

interface MockAuditLog extends AuditLogDetail {
  /** 操作人所属组织（数据范围过滤用，不外返） */
  orgId: string
}

const logs: MockAuditLog[] = [
  { id: 'al1', realName: '李明', loginName: 'admin', role: 'SUPER_ADMIN', orgId: 'o1', bizType: 'AUTH_PASSWORD_CHANGE', bizTypeLabel: '修改密码', summary: '修改账号密码成功，旧会话全部作废', ip: '10.20.1.5', success: 1, errorMessage: '', costMillis: 42, createdAt: '2026-10-12 09:12:33', httpMethod: 'PUT', uri: '/api/auth/password', userAgent: 'Mozilla/5.0', requestParams: '' },
  { id: 'al2', realName: '李明', loginName: 'admin', role: 'SUPER_ADMIN', orgId: 'o1', bizType: 'APPROVAL_APPROVE', bizTypeLabel: '审批-批准', summary: '批准「陈子昂 · 创建赛队星链小队」', ip: '10.20.1.5', success: 1, errorMessage: '', costMillis: 128, createdAt: '2026-10-12 09:20:41', httpMethod: 'POST', uri: '/api/approvals/ap2/approve', userAgent: 'Mozilla/5.0', requestParams: '{"comment":"同意，注意赛前培训"}' },
  { id: 'al3', realName: '陈志远', loginName: 'dept01', role: 'DEPT_ADMIN', orgId: 'o11', bizType: 'APPROVAL_REJECT', bizTypeLabel: '审批-驳回', summary: '驳回「王雨桐 · 创建赛队电波分队」', ip: '10.20.3.12', success: 1, errorMessage: '', costMillis: 96, createdAt: '2026-10-12 10:05:18', httpMethod: 'POST', uri: '/api/approvals/ap4/reject', userAgent: 'Mozilla/5.0', requestParams: '{"comment":"队名与已有队伍过于接近"}' },
  { id: 'al4', realName: '陈志远', loginName: 'dept01', role: 'DEPT_ADMIN', orgId: 'o11', bizType: 'FILE_UPLOAD', bizTypeLabel: '文件上传', summary: '上传「数媒协会招新海报.zip」', ip: '10.20.3.12', success: 1, errorMessage: '', costMillis: 812, createdAt: '2026-10-12 10:31:02', httpMethod: 'POST', uri: '/api/files', userAgent: 'Mozilla/5.0', requestParams: '' },
  { id: 'al5', realName: '王雨桐', loginName: '2023319024', role: 'STUDENT', orgId: 'o11', bizType: 'FILE_UPLOAD', bizTypeLabel: '文件上传', summary: '上传「报名表-王雨桐.pdf」', ip: '10.21.5.44', success: 1, errorMessage: '', costMillis: 655, createdAt: '2026-10-12 11:02:47', httpMethod: 'POST', uri: '/api/files', userAgent: 'Mozilla/5.0', requestParams: '' },
  { id: 'al6', realName: '王雨桐', loginName: '2023319024', role: 'STUDENT', orgId: 'o11', bizType: 'APPROVAL_AGREE', bizTypeLabel: '审批-同意', summary: '同意加入「电波突击队」', ip: '10.21.5.44', success: 1, errorMessage: '', costMillis: 74, createdAt: '2026-10-12 11:15:20', httpMethod: 'POST', uri: '/api/approvals/ap1/agree', userAgent: 'Mozilla/5.0', requestParams: '{"comment":"同意加入"}' },
  { id: 'al7', realName: '周子墨', loginName: '2023319005', role: 'STUDENT', orgId: 'o12', bizType: 'FILE_DOWNLOAD', bizTypeLabel: '文件下载', summary: '下载「赛队章程模板.docx」', ip: '10.21.6.20', success: 1, errorMessage: '', costMillis: 230, createdAt: '2026-10-12 13:40:11', httpMethod: 'GET', uri: '/api/files/f-demo-9/download', userAgent: 'Mozilla/5.0', requestParams: '' },
  { id: 'al8', realName: '赵天宇', loginName: '2023319001', role: 'STUDENT', orgId: 'o12', bizType: 'FILE_UPLOAD', bizTypeLabel: '文件上传', summary: '上传「电路图.dsn」', ip: '10.21.6.31', success: 0, errorMessage: '病毒检测未通过（INFECTED）', costMillis: 1520, createdAt: '2026-10-12 14:22:56', httpMethod: 'POST', uri: '/api/files', userAgent: 'Mozilla/5.0', requestParams: '' },
  { id: 'al9', realName: '陈志远', loginName: 'dept01', role: 'DEPT_ADMIN', orgId: 'o11', bizType: 'APPROVAL_APPROVE', bizTypeLabel: '审批-批准', summary: '批准「李明轩 · 电波突击队申请指导教师：李明」', ip: '10.20.3.12', success: 1, errorMessage: '', costMillis: 88, createdAt: '2026-10-12 15:08:40', httpMethod: 'POST', uri: '/api/approvals/ap6/approve', userAgent: 'Mozilla/5.0', requestParams: '{"comment":"同意"}' },
  { id: 'al10', realName: '陈子昂', loginName: '2023319002', role: 'STUDENT', orgId: 'o13', bizType: 'AUTH_PASSWORD_CHANGE', bizTypeLabel: '修改密码', summary: '原密码不正确', ip: '10.22.1.8', success: 0, errorMessage: '原密码不正确（2002）', costMillis: 18, createdAt: '2026-10-12 16:47:03', httpMethod: 'PUT', uri: '/api/auth/password', userAgent: 'Mozilla/5.0', requestParams: '' },
  { id: 'al11', realName: '孙嘉仪', loginName: '2023319006', role: 'STUDENT', orgId: 'o111', bizType: 'APPROVAL_CANCEL', bizTypeLabel: '审批-取消', summary: '取消「像素工坊申请加人：周子墨」', ip: '10.21.7.52', success: 1, errorMessage: '', costMillis: 65, createdAt: '2026-10-12 17:30:29', httpMethod: 'POST', uri: '/api/approvals/ap5/cancel', userAgent: 'Mozilla/5.0', requestParams: '{"comment":"信息填错"}' },
  { id: 'al12', realName: '李明', loginName: 'admin', role: 'SUPER_ADMIN', orgId: 'o1', bizType: 'FILE_DOWNLOAD', bizTypeLabel: '文件下载', summary: '下载「获奖名单汇总.xlsx」', ip: '10.20.1.5', success: 1, errorMessage: '', costMillis: 340, createdAt: '2026-10-12 18:12:55', httpMethod: 'GET', uri: '/api/files/f-demo-3/download', userAgent: 'Mozilla/5.0', requestParams: '' }
]

registerMock({
  method: 'get',
  path: '/api/audit-logs/biz-types',
  reply: (ctx) => {
    const u = findMockUserByToken(ctx.token)
    if (!u || u.role === 'STUDENT') return fail(1003, '无权访问审计模块')
    const seen: Record<string, string> = {}
    for (const l of logs) seen[l.bizType] = l.bizTypeLabel
    const out: Array<{ bizType: string; label: string }> = []
    for (const k in seen) out.push({ bizType: k, label: seen[k] })
    return ok(out)
  }
})

registerMock({
  method: 'get',
  path: '/api/audit-logs',
  reply: (ctx) => {
    const u = findMockUserByToken(ctx.token)
    if (!u || u.role === 'STUDENT') return fail(1003, '无权访问审计模块')
    const scope = deptScopeOrgIds(ctx.token)
    const keyword = String(ctx.query.keyword ?? '').trim()
    const bizType = String(ctx.query.bizType ?? '')
    const success = String(ctx.query.success ?? '')
    const startTime = String(ctx.query.startTime ?? '')
    const endTime = String(ctx.query.endTime ?? '')
    const page = Math.max(1, Number(ctx.query.page ?? 1) || 1)
    const size = Math.min(100, Math.max(1, Number(ctx.query.size ?? 20) || 20))
    const filtered: MockAuditLog[] = []
    for (const l of logs) {
      if (!orgInScope(l.orgId, scope)) continue
      if (keyword && l.realName.indexOf(keyword) < 0 && l.loginName.indexOf(keyword) < 0 && l.summary.indexOf(keyword) < 0) continue
      if (bizType && l.bizType !== bizType) continue
      if (success !== '' && String(l.success) !== success) continue
      if (startTime && l.createdAt < startTime) continue
      if (endTime && l.createdAt > endTime) continue
      filtered.push(l)
    }
    filtered.sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
    const start = (page - 1) * size
    const list = filtered.slice(start, start + size).map((l) => ({
      id: l.id, realName: l.realName, loginName: l.loginName, role: l.role,
      bizType: l.bizType, bizTypeLabel: l.bizTypeLabel, summary: l.summary, ip: l.ip,
      success: l.success, errorMessage: l.errorMessage, costMillis: l.costMillis, createdAt: l.createdAt
    }))
    return ok({ list, total: filtered.length, page, size })
  }
})

registerMock({
  method: 'get',
  path: '/api/audit-logs/:id',
  reply: (ctx) => {
    const u = findMockUserByToken(ctx.token)
    if (!u || u.role === 'STUDENT') return fail(1003, '无权访问审计模块')
    const scope = deptScopeOrgIds(ctx.token)
    for (const l of logs) {
      if (l.id !== ctx.params.id) continue
      if (!orgInScope(l.orgId, scope)) return fail(1003, '无权访问该日志')
      return ok(l)
    }
    return fail(1004, '日志不存在')
  }
})
