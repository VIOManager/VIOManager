/**
 * 项目管理 mock 接口层·文件与查看授权（PR25–PR31，从 project-ext.ts 拆出控制行数）
 * 文件先走骨架 F01 拿 fileId 再 PR26 建关联（幂等）；PR29 查看申请开 PROJECT_VIEW 审批单（审批责任组织=项目所属组织）
 */
import { fail, ok, registerMock } from './registry'
import { findMockUserByToken } from './auth'
import { meAsMember } from './member'
import { nowText, pushApproval, usedIdemKeys } from './approval-data'
import { projectFiles, projectMembers, viewGrants } from './project-data'
import { findProject } from './project'

let fileSeq = 2
let grantSeq = 2
// ── PR25–PR28 项目文件 ──

registerMock({
  method: 'get',
  path: '/api/projects/:id/files',
  reply: (ctx) => {
    return ok(projectFiles.filter((f) => f.projectId === ctx.params.id))
  }
})

registerMock({
  method: 'post',
  path: '/api/projects/:id/files',
  reply: (ctx) => {
    if (!ctx.idemKey) return fail(1001, '缺少幂等键（X-Idempotency-Key）')
    if (usedIdemKeys.has(ctx.idemKey)) return fail(1005, '请勿重复提交')
    const p = findProject(ctx.params.id)
    if (!p) return fail(7001, '项目不存在')
    const me = meAsMember(ctx.token)
    if (!me) return fail(1003, '仅项目成员可上传')
    const isOwner = p.ownerId === me.id
    const isMember = projectMembers.some((m) => m.projectId === p.id && m.memberId === me.id)
    if (!isOwner && !isMember) return fail(7002, '仅所有人与项目成员可上传')
    const fileId = String(ctx.body.fileId ?? '')
    if (!fileId) return fail(1001, '缺少 fileId（先走骨架文件服务上传）')
    usedIdemKeys.add(ctx.idemKey)
    const f = {
      id: 'pf' + fileSeq++, projectId: p.id, fileId,
      fileName: String(ctx.body.fileName ?? '未命名'), fileSize: Number(ctx.body.fileSize ?? 0),
      fileCategory: String(ctx.body.fileCategory ?? 'OTHER'), scanStatus: 'CLEAN',
      uploadedByName: me.name, uploadedById: me.id, createdAt: nowText()
    }
    projectFiles.push(f)
    return ok({ id: f.id })
  }
})

registerMock({
  method: 'delete',
  path: '/api/projects/:id/files/:fileRecordId',
  reply: (ctx) => {
    const p = findProject(ctx.params.id)
    if (!p) return fail(7001, '项目不存在')
    const me = meAsMember(ctx.token)
    if (!me) return fail(1003, '仅项目成员可操作')
    for (const f of projectFiles) {
      if (f.id !== ctx.params.fileRecordId || f.projectId !== p.id) continue
      const isOwner = p.ownerId === me.id
      if (!isOwner && f.uploadedById !== me.id) return fail(7002, '成员仅可删除自己上传的文件')
      projectFiles.splice(projectFiles.indexOf(f), 1)
      return ok(null)
    }
    return fail(1004, '文件不存在')
  }
})

registerMock({
  method: 'get',
  path: '/api/projects/files/:fileRecordId/download',
  reply: (ctx) => {
    for (const f of projectFiles) {
      if (f.id === ctx.params.fileRecordId) return ok({ note: '文件「' + f.fileName + '」下载已审计（mock 演示不落流）' })
    }
    return fail(1004, '文件不存在')
  }
})

// ── PR29–PR31 查看申请与授权 ──

registerMock({
  method: 'post',
  path: '/api/projects/:id/view-requests',
  reply: (ctx) => {
    if (!ctx.idemKey) return fail(1001, '缺少幂等键（X-Idempotency-Key）')
    if (usedIdemKeys.has(ctx.idemKey)) return fail(1005, '请勿重复提交')
    const p = findProject(ctx.params.id)
    if (!p) return fail(7001, '项目不存在')
    const me = meAsMember(ctx.token)
    if (!me) return fail(1003, '仅成员可申请')
    const grantType = String(ctx.body.grantType ?? '')
    if (grantType !== 'ONCE' && grantType !== 'LIMITED') return fail(1001, '授权类型不合法')
    const expireAt = String(ctx.body.expireAt ?? '')
    if (grantType === 'LIMITED' && !expireAt) return fail(1001, '限时授权须填到期时间')
    for (const g of viewGrants) {
      if (g.projectId === p.id && g.applicantMemberId === me.id && g.status === 'PENDING') {
        return fail(7011, '已存在待审批的查看申请，请勿重复提交')
      }
    }
    usedIdemKeys.add(ctx.idemKey)
    const g = {
      id: 'vg' + grantSeq++, projectId: p.id, applicantMemberId: me.id, applicantName: me.name,
      grantType: grantType as 'ONCE' | 'LIMITED', status: 'PENDING' as const,
      expireAt, usedCount: 0, createdAt: nowText(), approvalId: ''
    }
    const ap = pushApproval({
      bizType: 'PROJECT_VIEW', bizId: p.id,
      bizTitle: me.name + ' · 申请查看项目「' + p.name + '」',
      applyReason: '授权类型：' + (grantType === 'ONCE' ? '一次性' : '限时至 ' + expireAt),
      payload: { grantId: g.id, projectId: p.id, grantType },
      applicantMemberId: me.id, applicantName: me.name,
      approverOrgId: p.orgId
    })
    g.approvalId = ap.id
    viewGrants.push(g)
    return ok(null)
  }
})

registerMock({
  method: 'get',
  path: '/api/projects/:id/view-grants',
  reply: (ctx) => {
    const p = findProject(ctx.params.id)
    if (!p) return fail(7001, '项目不存在')
    const me = meAsMember(ctx.token)
    const u = findMockUserByToken(ctx.token)
    const isOwner = !!me && p.ownerId === me.id
    const isAdmin = !!u && u.role !== 'STUDENT'
    if (!isOwner && !isAdmin) return fail(7003, '仅所有人与管理员可看授权列表')
    return ok(viewGrants.filter((g) => g.projectId === p.id))
  }
})

registerMock({
  method: 'put',
  path: '/api/view-grants/:id/revoke',
  reply: (ctx) => {
    for (const g of viewGrants) {
      if (g.id !== ctx.params.id) continue
      const p = findProject(g.projectId)
      const me = meAsMember(ctx.token)
      const u = findMockUserByToken(ctx.token)
      const isOwner = !!p && !!me && p.ownerId === me.id
      const isAdmin = !!u && u.role !== 'STUDENT'
      if (!isOwner && !isAdmin) return fail(7002, '仅所有人与管理员可撤销')
      g.status = 'REVOKED'
      return ok(null)
    }
    return fail(1004, '授权不存在')
  }
})
