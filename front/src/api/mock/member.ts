/**
 * 人员档案 mock（A 线 M01–M13）
 * ⚠️ 演示数据：8 名成员；账号与 mock/auth 的三账号互通（admin↔李明 / dept01↔陈志远 / 2023319024↔王雨桐）
 * 裁剪规则（M02）：超管/部门管理员 → 全量；学生本人 → 全量；其他学生 → 仅姓名/学院/班级/技能（limited）
 * 保护分支：3001 学号重复 / 3002 学号不可改 / 3003 队长拒删（李明轩）/ 3004 删当前登录账号
 * TODO(联调)：后端人员接口就绪后删除本文件；删除成员的"业务记录保留"由后端保证
 */
import { fail, ok, registerMock } from './registry'
import { findMockUserByToken } from './auth'
import { findOrgNodeById } from './org'
import type { MemberDetail, MemberListItem } from '@/api/member'
import type { PageResult } from '@/api/types'

interface MockMember extends MemberListItem {
  gender: string
  nation: string
  email: string
  idCard: string
  photoFileId: string
  signatureFileId: string
  createdAt: string
  /** 演示 3003：队长身份禁止删除 */
  isLeader?: boolean
  /** 绑定的登录账号（hasAccount=true 时有效） */
  loginName?: string
}

let seq = 20

const members: MockMember[] = [
  { id: 'm1', name: '李明', studentNo: 'T1001', college: '计算机与信息工程学院', major: '软件工程', className: '', phone: '13800000001', memberType: 'TEACHER', orgId: 'o1', orgName: '科创团队本部', hasAccount: true, idCardLast4: '0031', skills: ['python'], gender: '男', nation: '汉族', email: 'liming@usth.edu.cn', idCard: '230103198501010031', photoFileId: '', signatureFileId: '', createdAt: '2026-09-01 09:00:00', loginName: 'admin' },
  { id: 'm2', name: '陈志远', studentNo: 'T1002', college: '计算机与信息工程学院', major: '计算机科学与技术', className: '', phone: '13800000002', memberType: 'TEACHER', orgId: 'o11', orgName: '数媒协会', hasAccount: true, idCardLast4: '0217', skills: ['python', '视频剪辑'], gender: '男', nation: '汉族', email: 'chenzy@usth.edu.cn', idCard: '230103198802140217', photoFileId: '', signatureFileId: '', createdAt: '2026-09-01 09:00:00', loginName: 'dept01' },
  { id: 'm3', name: '赵天宇', studentNo: '2023319001', college: '电子与信息工程学院', major: '电子信息工程', className: '电信23-1班', phone: '13900000003', memberType: 'STUDENT', orgId: 'o12', orgName: '电控工作室', hasAccount: false, idCardLast4: '1042', skills: ['CAD', '机械设计'], gender: '男', nation: '汉族', email: 'zhaoty@stu.usth.edu.cn', idCard: '230106200503141042', photoFileId: '', signatureFileId: '', createdAt: '2026-09-02 10:00:00' },
  { id: 'm4', name: '陈子昂', studentNo: '2023319002', college: '计算机与信息工程学院', major: '软件工程', className: '软工23-2班', phone: '13900000004', memberType: 'STUDENT', orgId: 'o13', orgName: '建模组', hasAccount: false, idCardLast4: '2218', skills: ['python', 'AIGC'], gender: '男', nation: '汉族', email: 'chenza@stu.usth.edu.cn', idCard: '230106200505122218', photoFileId: '', signatureFileId: '', createdAt: '2026-09-02 10:00:00' },
  { id: 'm5', name: '李明轩', studentNo: '2023319003', college: '计算机与信息工程学院', major: '软件工程', className: '软工23-2班', phone: '13900000005', memberType: 'STUDENT', orgId: 'o11', orgName: '数媒协会', hasAccount: true, idCardLast4: '3390', skills: ['视频剪辑'], gender: '男', nation: '汉族', email: 'limx@stu.usth.edu.cn', idCard: '230106200508233390', photoFileId: '', signatureFileId: '', createdAt: '2026-09-03 14:00:00', isLeader: true, loginName: '2023319003' },
  { id: 'm6', name: '孙嘉仪', studentNo: '2023319004', college: '计算机与信息工程学院', major: '计算机科学与技术', className: '计科23-1班', phone: '13900000006', memberType: 'STUDENT', orgId: 'o111', orgName: '数媒二楼', hasAccount: false, idCardLast4: '5567', skills: ['AIGC'], gender: '女', nation: '汉族', email: 'sunjy@stu.usth.edu.cn', idCard: '230106200511085567', photoFileId: '', signatureFileId: '', createdAt: '2026-09-03 14:00:00' },
  { id: 'm7', name: '王雨桐', studentNo: '2023319024', college: '计算机与信息工程学院', major: '软件工程', className: '软工23-2班', phone: '13900000007', memberType: 'STUDENT', orgId: 'o11', orgName: '数媒协会', hasAccount: true, idCardLast4: '8821', skills: ['python'], gender: '女', nation: '满族', email: 'wangyt@stu.usth.edu.cn', idCard: '230106200603098821', photoFileId: '', signatureFileId: '', createdAt: '2026-09-05 16:00:00', loginName: '2023319024' },
  { id: 'm8', name: '周子墨', studentNo: '2023319005', college: '机械工程学院', major: '机械设计制造及其自动化', className: '机自23-1班', phone: '13900000008', memberType: 'STUDENT', orgId: 'o111', orgName: '数媒二楼', hasAccount: false, idCardLast4: '6634', skills: ['CAD'], gender: '男', nation: '汉族', email: 'zhouzm@stu.usth.edu.cn', idCard: '230106200507196634', photoFileId: '', signatureFileId: '', createdAt: '2026-09-05 16:00:00' }
]

/** 当前登录账号对应的成员（admin→m1 / dept01→m2 / 2023319024→m7） */
function meAsMember(token: string): MockMember | null {
  const u = findMockUserByToken(token)
  if (!u) return null
  for (const m of members) {
    if (m.loginName && m.loginName === u.loginName) return m
  }
  return null
}

/** M02 详情按角色裁剪：管理角色/本人全量；其他学生仅基础字段 */
function detailFor(m: MockMember, token: string): MemberDetail {
  const u = findMockUserByToken(token)
  const me = meAsMember(token)
  const privileged = (u && (u.role === 'SUPER_ADMIN' || u.role === 'DEPT_ADMIN')) || (me && me.id === m.id)
  if (!privileged) {
    return { id: m.id, name: m.name, college: m.college, className: m.className, memberType: m.memberType, orgName: m.orgName, skills: m.skills, limited: true }
  }
  return {
    id: m.id, name: m.name, studentNo: m.studentNo, college: m.college, major: m.major, className: m.className,
    phone: m.phone, gender: m.gender, nation: m.nation, email: m.email, memberType: m.memberType,
    orgId: m.orgId, orgName: m.orgName, skills: m.skills, idCardLast4: m.idCardLast4, hasAccount: m.hasAccount,
    photoFileId: m.photoFileId, signatureFileId: m.signatureFileId, createdAt: m.createdAt
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
    let list: MockMember[] = []
    for (const m of members) {
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
    for (const m of members) {
      if (m.id !== ctx.params.id) continue
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
    for (const m of members) {
      if (m.id !== ctx.params.id) continue
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
