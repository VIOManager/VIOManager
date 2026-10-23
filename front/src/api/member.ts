/**
 * 人员档案接口（A 线 M01–M13）
 * 对应清单：docs/api/A线-基础数据-接口清单.md §3 + PRD §3.2
 * ⚠️ 隐私：列表只返回 idCardLast4；完整身份证走 M10（写审计日志）；证件照/签名走文件服务
 * 写操作按规范 §6 带 X-Idempotency-Key（postIdem/putIdem/delIdem，键随每次点击生成，
 * 同审批/材料线的联调口径）；M12 技能替换后端未挂幂等，普通 PUT。
 */
import { delIdem, download, get, postIdem, put, putIdem, upload } from './http'
import type { PageResult } from './types'

export type MemberType = 'STUDENT' | 'TEACHER' | 'OTHER'

/** 列表项（M01）：不含身份证/证件照/邮箱，进详情 */
export interface MemberListItem {
  id: string
  name: string
  studentNo: string
  college: string
  major: string
  className: string
  phone: string
  memberType: MemberType
  orgId: string
  orgName: string
  hasAccount: boolean
  idCardLast4: string
  skills: string[]
}

/** 详情（M02）：后端按角色裁剪——无权视角只回 limited=true 与基础字段 */
export interface MemberDetail {
  id: string
  name: string
  college: string
  className: string
  memberType: MemberType
  orgName: string
  skills: string[]
  /** true=受限视角（仅姓名/学院/班级/技能/类型/组织） */
  limited?: boolean
  studentNo?: string
  major?: string
  phone?: string
  gender?: string
  nation?: string
  email?: string
  idCardLast4?: string
  hasAccount?: boolean
  orgId?: string
  photoFileId?: string
  signatureFileId?: string
  createdAt?: string
  /** 已绑定账号的登录名（M13 绑定后返回） */
  loginName?: string
}

export interface MemberQuery {
  page: number
  size: number
  keyword?: string
  orgId?: string
  memberType?: string
  skill?: string
  hasAccount?: string
}

/** 新建/编辑表单（M03/M04）；学号可空、不可重复、不可修改（3001/3002）
 *  2026-10-17 对齐后端 DTO：可空字段空串一律不传（@Pattern/@Long 对空串报 1001，
 *  编辑是部分更新语义"不传=不改"），编辑时 body 必须带 id（P02 契约） */
export interface MemberForm {
  name: string
  studentNo: string
  gender: string
  memberType: MemberType
  skills: string[]
  phone?: string
  college?: string
  major?: string
  className?: string
  nation?: string
  email?: string
  orgId?: string
  /** 身份证号（18 位选填；编辑不传=保持原值，传了后端重新加密覆盖） */
  idCard?: string
  /** 编辑必带：后端 P02 要求 id 同时出现在 body（幂等/审计定位用） */
  id?: string
}

export interface ImportResult {
  successCount: number
  failCount: number
  errors: { row: number; reason: string }[]
}

export function apiMembers(q: MemberQuery): Promise<PageResult<MemberListItem>> {
  return get('/api/members', q as unknown as Record<string, unknown>)
}

/** 受限候选（M14，2026-10-23 增）：任意登录角色可用，队长流程加载候选专用
 *  （M01 是管理员专属，学生队长直调必 403）；只有目录级字段，无任何敏感信息 */
export interface MemberOption {
  id: string
  name: string
  memberType: MemberType
  studentNo: string
  jobNo: string
  orgId: string
  orgName: string
  college: string
}

export interface MemberOptionQuery {
  keyword?: string
  memberType?: MemberType
  page?: number
  size?: number
}

export function apiMemberOptions(q: MemberOptionQuery): Promise<PageResult<MemberOption>> {
  return get('/api/members/options', q as unknown as Record<string, unknown>)
}
export function apiMemberDetail(id: string): Promise<MemberDetail> {
  return get('/api/members/' + id)
}
export function apiCreateMember(body: MemberForm): Promise<MemberDetail> {
  return postIdem('/api/members', body, crypto.randomUUID())
}
export function apiUpdateMember(id: string, body: MemberForm): Promise<MemberDetail> {
  return putIdem('/api/members/' + id, body, crypto.randomUUID())
}
/** 队长拒删（3003）；当前登录账号拒删（3004） */
export function apiDeleteMember(id: string): Promise<null> {
  return delIdem('/api/members/' + id, crypto.randomUUID())
}
/** 完整身份证号（M10，写审计日志；仅超管/本部门管理员/本队队长/本人） */
export function apiMemberIdCard(id: string): Promise<{ idCard: string }> {
  return get('/api/members/' + id + '/id-card')
}
/** 技能整组替换：后端 M12 无 @Idempotent（替换天然幂等），普通 PUT 即可 */
export function apiUpdateSkills(id: string, skills: string[]): Promise<null> {
  return put('/api/members/' + id + '/skills', { skills })
}
export function apiImportMembers(file: File, idempotencyKey: string): Promise<ImportResult> {
  return upload('/api/members/import', file, idempotencyKey)
}
/** 证件照/电子签名上传（M08/M09）：先传文件服务拿 fileId，再回填档案 */
export function apiBindPhoto(id: string, fileId: string): Promise<null> {
  return postIdem('/api/members/' + id + '/photo', { fileId }, crypto.randomUUID())
}
export function apiBindSignature(id: string, fileId: string): Promise<null> {
  return postIdem('/api/members/' + id + '/signature', { fileId }, crypto.randomUUID())
}
/** 账号绑定（M13）：POST 绑定（初始密码 abc123）；PUT 改登录名/重置密码（重置后回默认密码态） */
export function apiBindAccount(id: string, loginName?: string): Promise<null> {
  return postIdem('/api/members/' + id + '/account', { loginName }, crypto.randomUUID())
}
export function apiResetAccount(id: string, loginName?: string, resetPassword?: boolean): Promise<null> {
  return putIdem('/api/members/' + id + '/account', { loginName, resetPassword }, crypto.randomUUID())
}

/**
 * 下载导入模板（M06）。mock 模式前端自生成 CSV 占位；真实模式走后端文件流。
 * ⚠️ 必须用 download()（axios 带 Authorization + 解析 Content-Disposition 文件名）——
 * window.open 发不了请求头，M06 有 @RequireRole 会被 401 拦下。
 * ⚠️ 待确认：PRD §3.2 文字写"13 列"但字段清单实际列了 14 项，本模板按字段清单（14 列）
 */
export function apiDownloadTemplate(): void {
  const useMock = import.meta.env.VITE_USE_MOCK === 'true'
  if (!useMock) {
    void download('/api/members/import-template', '人员导入模板.xlsx')
    return
  }
  const header = '姓名,学号/学工号,手机号,学院,专业,班级,性别,民族,身份证号,邮箱,证件照,电子签名,所属组织,类型'
  const blob = new Blob(['﻿' + header + '\n'], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = '人员导入模板.csv'
  a.click()
  URL.revokeObjectURL(a.href)
}
