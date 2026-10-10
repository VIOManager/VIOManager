/**
 * 人员档案 mock（A 线 M01–M13）
 * ⚠️ 演示数据：8 名成员；账号与 mock/auth 的三账号互通（admin↔李明 / dept01↔陈志远 / 2023319024↔王雨桐）
 * 裁剪规则（M02）：超管/部门管理员 → 全量；学生本人 → 全量；其他学生 → 仅姓名/学院/班级/技能（limited）
 * 保护分支：3001 学号重复 / 3002 学号不可改 / 3003 队长拒删（李明轩）/ 3004 删当前登录账号
 * TODO(联调)：后端人员接口就绪后删除本文件；删除成员的"业务记录保留"由后端保证
 */
import { fail, ok, registerMock } from './registry'
import { findMockUserByToken } from './auth'
import { findOrgNodeById } from './org-data'
import { deptScopeOrgIds, orgInScope } from './scope'
import { members } from './member-data'
import type { MockMember } from './member-data'

/** 新成员 id 发号器 */let seq = 20
import type { MemberDetail, MemberListItem } from '@/api/member'
import type { PageResult } from '@/api/types'

/** 当前登录账号对应的成员（admin→m1 / dept01→m2 / 2023319024→m7）；赛队等 mock 复用 */
export function meAsMember(token: string): MockMember | null {
  const u = findMockUserByToken(token)
  if (!u) return null
  for (const m of members) {
    if (m.loginName && m.loginName === u.loginName) return m
  }
  return null
}

/** M02 详情按角色裁剪：超管全量；部门管理员仅本部门成员全量；本人全量；其他学生仅基础字段 */
function detailFor(m: MockMember, token: string): MemberDetail {
  const u = findMockUserByToken(token)
  const me = meAsMember(token)
  const scope = deptScopeOrgIds(token)
  const privileged =
    (u && (u.role === 'SUPER_ADMIN' || (u.role === 'DEPT_ADMIN' && orgInScope(m.orgId, scope)))) ||
    (me && me.id === m.id)
  if (!privileged) {
    return { id: m.id, name: m.name, college: m.college, className: m.className, memberType: m.memberType, orgName: m.orgName, skills: m.skills, limited: true }
  }
  return {
    id: m.id, name: m.name, studentNo: m.studentNo, college: m.college, major: m.major, className: m.className,
    phone: m.phone, gender: m.gender, nation: m.nation, email: m.email, memberType: m.memberType,
    orgId: m.orgId, orgName: m.orgName, skills: m.skills, idCardLast4: m.idCardLast4, hasAccount: m.hasAccount,
    photoFileId: m.photoFileId, signatureFileId: m.signatureFileId, createdAt: m.createdAt, loginName: m.loginName
  }
}

function toListItem(m: MockMember): MemberListItem {
  return {
    id: m.id, name: m.name, studentNo: m.studentNo, college: m.college, major: m.major, className: m.className,
    phone: m.phone, memberType: m.memberType, orgId: m.orgId, orgName: m.orgName, hasAccount: m.hasAccount,
    idCardLast4: m.idCardLast4, skills: m.skills
  }
}

registerMock({
  method: 'get',
  path: '/api/members',
  reply: (ctx) => {
    const keyword = String(ctx.query.keyword ?? '').trim()
    const orgId = String(ctx.query.orgId ?? '')
    const memberType = String(ctx.query.memberType ?? '')
    const skill = String(ctx.query.skill ?? '')
    const hasAccount = String(ctx.query.hasAccount ?? '')
    const scope = deptScopeOrgIds(ctx.token)
    let list: MockMember[] = []
    for (const m of members) {
      // 部门管理员：仅本部门子树成员
      if (!orgInScope(m.orgId, scope)) continue
      if (keyword) {
        const hit = m.name.indexOf(keyword) >= 0 || m.studentNo.indexOf(keyword) >= 0 || m.phone.indexOf(keyword) >= 0
        if (!hit) continue
      }
      if (orgId && m.orgId !== orgId) continue
      if (memberType && m.memberType !== memberType) continue
      if (skill && m.skills.indexOf(skill) < 0) continue
      if (hasAccount === 'true' && !m.hasAccount) continue
      if (hasAccount === 'false' && m.hasAccount) continue
      list.push(m)
    }
    const page = Number(ctx.query.page ?? 1)
    const size = Number(ctx.query.size ?? 20)
    const start = (page - 1) * size
    const items = list.slice(start, start + size)
    const out: PageResult<MemberListItem> = { list: [], total: list.length, page, size }
    for (const m of items) out.list.push(toListItem(m))
    return ok(out)
  }
})

registerMock({
  method: 'get',
  path: '/api/members/:id',
  reply: (ctx) => {
    for (const m of members) {
      if (m.id === ctx.params.id) return ok(detailFor(m, ctx.token))
    }
    return fail(1004, '人员不存在')
  }
})

registerMock({
  method: 'post',
  path: '/api/members',
  reply: (ctx) => {
    const name = String(ctx.body.name ?? '').trim()
    if (!name) return fail(1001, '姓名不能为空')
    const studentNo = String(ctx.body.studentNo ?? '').trim()
    if (studentNo) {
      for (const m of members) {
        if (m.studentNo === studentNo) return fail(3001, '学号已存在')
      }
    }
    const skills = Array.isArray(ctx.body.skills) ? (ctx.body.skills as string[]) : []
    const orgId = String(ctx.body.orgId ?? '')
    const orgNode = orgId ? findOrgNodeById(orgId) : null
    const m: MockMember = {
      id: 'm' + seq++, name, studentNo, phone: String(ctx.body.phone ?? ''),
      college: String(ctx.body.college ?? ''), major: String(ctx.body.major ?? ''), className: String(ctx.body.className ?? ''),
      memberType: (String(ctx.body.memberType ?? 'STUDENT') as MockMember['memberType']),
      orgId, orgName: orgNode ? orgNode.name : '',
      hasAccount: false, idCardLast4: '', skills, gender: String(ctx.body.gender ?? ''),
      nation: String(ctx.body.nation ?? ''), email: String(ctx.body.email ?? ''), idCard: '',
      photoFileId: '', signatureFileId: '', createdAt: '2026-10-10 00:00:00'
    }
    members.push(m)
    return ok(detailFor(m, ctx.token))
  }
})

registerMock({
  method: 'put',
  path: '/api/members/:id',
  reply: (ctx) => {
    const scope = deptScopeOrgIds(ctx.token)
    for (const m of members) {
      if (m.id !== ctx.params.id) continue
      if (!orgInScope(m.orgId, scope)) return fail(1003, '无权修改本部门以外的人员')
      const studentNo = String(ctx.body.studentNo ?? m.studentNo)
      if (studentNo !== m.studentNo) return fail(3002, '学号不可修改')
      m.name = String(ctx.body.name ?? m.name)
      m.phone = String(ctx.body.phone ?? m.phone)
      m.college = String(ctx.body.college ?? m.college)
      m.major = String(ctx.body.major ?? m.major)
      m.className = String(ctx.body.className ?? m.className)
      m.memberType = String(ctx.body.memberType ?? m.memberType) as MockMember['memberType']
      m.gender = String(ctx.body.gender ?? m.gender)
      m.nation = String(ctx.body.nation ?? m.nation)
      m.email = String(ctx.body.email ?? m.email)
      if (Array.isArray(ctx.body.skills)) m.skills = ctx.body.skills as string[]
      const newOrgId = String(ctx.body.orgId ?? m.orgId)
      if (newOrgId !== m.orgId) {
        const orgNode = findOrgNodeById(newOrgId)
        m.orgId = newOrgId
        m.orgName = orgNode ? orgNode.name : ''
      }
      return ok(detailFor(m, ctx.token))
    }
    return fail(1004, '人员不存在')
  }
})

registerMock({
  method: 'delete',
  path: '/api/members/:id',
  reply: (ctx) => {
    const scope = deptScopeOrgIds(ctx.token)
    for (const m of members) {
      if (m.id !== ctx.params.id) continue
      if (!orgInScope(m.orgId, scope)) return fail(1003, '无权删除本部门以外的人员')
      if (m.isLeader) return fail(3003, '该人员是队长，需先解散队伍')
      const me = meAsMember(ctx.token)
      if (me && me.id === m.id) return fail(3004, '不能删除当前登录账号')
      members.splice(members.indexOf(m), 1)
      return ok(null)
    }
    return fail(1004, '人员不存在')
  }
})

registerMock({
  method: 'get',
  path: '/api/members/:id/id-card',
  reply: (ctx) => {
    for (const m of members) {
      if (m.id !== ctx.params.id) continue
      const d = detailFor(m, ctx.token)
      if (d.limited) return fail(3010, '无权查看该敏感信息')
      return ok({ idCard: m.idCard })
    }
    return fail(1004, '人员不存在')
  }
})

registerMock({
  method: 'put',
  path: '/api/members/:id/skills',
  reply: (ctx) => {
    for (const m of members) {
      if (m.id !== ctx.params.id) continue
      m.skills = Array.isArray(ctx.body.skills) ? (ctx.body.skills as string[]) : []
      return ok(null)
    }
    return fail(1004, '人员不存在')
  }
})

/** M07 Excel 导入：mock 不解析文件，返回固定行级错误样例（演示回显格式） */
registerMock({
  method: 'post',
  path: '/api/members/import',
  reply: (ctx) => {
    if (!ctx.idemKey) return fail(1001, '缺少幂等键 X-Idempotency-Key')
    const fd = ctx.body as unknown as FormData
    const file = fd && typeof fd.get === 'function' ? (fd.get('file') as File | null) : null
    if (!file) return fail(1001, '未收到文件')
    return ok({
      successCount: 6,
      failCount: 2,
      errors: [
        { row: 3, reason: '学号 2023319001 已存在' },
        { row: 7, reason: '所属组织「不存在的组织」匹配失败' }
      ]
    })
  }
})

/** M08/M09 证件照与电子签名回填（文件先走文件服务，此处记录 fileId，覆盖式） */
registerMock({
  method: 'post',
  path: '/api/members/:id/photo',
  reply: (ctx) => {
    for (const m of members) {
      if (m.id === ctx.params.id) {
        m.photoFileId = String(ctx.body.fileId ?? '')
        return ok(null)
      }
    }
    return fail(1004, '人员不存在')
  }
})
registerMock({
  method: 'post',
  path: '/api/members/:id/signature',
  reply: (ctx) => {
    for (const m of members) {
      if (m.id === ctx.params.id) {
        m.signatureFileId = String(ctx.body.fileId ?? '')
        return ok(null)
      }
    }
    return fail(1004, '人员不存在')
  }
})

/** M13 账号绑定：默认登录名=学工号、初始密码 abc123、defaultPassword=1 */
registerMock({
  method: 'post',
  path: '/api/members/:id/account',
  reply: (ctx) => {
    for (const m of members) {
      if (m.id !== ctx.params.id) continue
      if (m.hasAccount) return fail(1001, '该人员已绑定账号')
      const loginName = String(ctx.body.loginName ?? '').trim() || m.studentNo
      if (!loginName) return fail(1001, '无学号，需手工指定登录名')
      m.hasAccount = true
      m.loginName = loginName
      return ok(null)
    }
    return fail(1004, '人员不存在')
  }
})

/** M13 账号维护：改登录名 / 重置密码（重置后回默认密码态） */
registerMock({
  method: 'put',
  path: '/api/members/:id/account',
  reply: (ctx) => {
    for (const m of members) {
      if (m.id !== ctx.params.id) continue
      if (!m.hasAccount) return fail(1004, '该人员未绑定账号')
      const loginName = String(ctx.body.loginName ?? '').trim()
      if (loginName) m.loginName = loginName
      // resetPassword=true → 后端重置为 abc123（mock 无密码表，语义记录即可）
      return ok(null)
    }
    return fail(1004, '人员不存在')
  }
})
