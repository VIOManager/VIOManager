/**
 * 系统配置 mock（SY01–SY10 字典管理 + CF01/CF02 系统参数；契约：docs/api/系统配置-接口清单.md）
 * 超管独占（其余 1003）；复用 1xxx 通用码（1001 参数/重复、1004 不存在、1006 状态不允许）
 * 数据共享：SKILL 条目直接操作 mock/dict.ts 的 skillItems（DR01 读取同源，审核/停用即时生效）；
 *    MATERIAL_TYPE 从 config/labels 的 12 类派生；KB_CATEGORY 独立种子
 * TODO(联调)：后端系统配置接口就绪后删除本文件
 */
import { fail, ok, registerMock } from './registry'
import { findMockUserByToken } from './auth'
import { usedIdemKeys } from './approval-data'
import { DICTS, skillItems } from './dict'
import { MATERIAL_TYPES } from '@/config/labels'
import type { DictItemAdmin, SysConfig } from '@/api/system'
import type { DictItem } from '@/api/dict'

interface MockDictType {
  id: string
  dictCode: string
  dictName: string
  remark: string
  preset: boolean
}

/** 条目统一视图（管理侧）；SKILL 条目由 skillItems 适配（见下） */
interface MockItem {
  id: string
  dictCode: string
  itemValue: string
  itemLabel: string
  sortNo: number
  source: 'PRESET' | 'USER'
  status: 'ENABLED' | 'DISABLED'
  auditStatus: 'PENDING' | 'APPROVED' | 'REJECTED'
  createdByName: string
}

const dictTypes: MockDictType[] = [
  { id: 'dt1', dictCode: 'SKILL', dictName: '技能标签', remark: '人员档案技能标签（PRD §3.1）', preset: true },
  { id: 'dt2', dictCode: 'MATERIAL_TYPE', dictName: '材料类型', remark: 'C 线 12 种预设材料类型', preset: true },
  { id: 'dt3', dictCode: 'KB_CATEGORY', dictName: '知识库分类', remark: '知识库条目分类（PRD §3.14）', preset: true }
]

/** MATERIAL_TYPE 12 类（labels 派生） */
const materialItems: MockItem[] = MATERIAL_TYPES.map((t, i) => ({
  id: 'dtm' + (i + 1), dictCode: 'MATERIAL_TYPE', itemValue: t.value, itemLabel: t.label,
  sortNo: i + 1, source: 'PRESET', status: 'ENABLED', auditStatus: 'APPROVED', createdByName: ''
}))

/** 旧式字典数组（DictItem 形状）适配为管理视图 */
function dictAdminView(code: string, arr: DictItem[]): MockItem[] {
  return arr.map((s, i) => ({
    id: s.id, dictCode: code, itemValue: s.label, itemLabel: s.label, sortNo: i + 1,
    source: s.source, status: (s as { status?: 'ENABLED' | 'DISABLED' }).status ?? 'ENABLED',
    auditStatus: s.auditStatus, createdByName: s.createdByName ?? ''
  }))
}

/** 字典原始数组（写操作直接改源，DR01 同源生效）；MATERIAL_TYPE 为 MockItem 形状单独处理 */
function rawDictArray(code: string): DictItem[] | null {
  if (code === 'SKILL') return skillItems
  if (code === 'KB_CATEGORY') return DICTS.KB_CATEGORY ?? null
  return null
}

/** 按类型取条目（管理视图） */
function itemsOf(dictCode: string): MockItem[] {
  if (dictCode === 'MATERIAL_TYPE') return materialItems
  const raw = rawDictArray(dictCode)
  if (raw) return dictAdminView(dictCode, raw)
  return []
}

/** 条目写回（改名/启停/审核按类型落到源数组） */
function writeItem(dictCode: string, id: string, patch: Partial<{ itemLabel: string; status: 'ENABLED' | 'DISABLED'; auditStatus: 'APPROVED' | 'REJECTED'; sortNo: number }>): boolean {
  if (dictCode === 'MATERIAL_TYPE') {
    for (const it of materialItems) {
      if (it.id === id) {
        if (patch.itemLabel) it.itemLabel = patch.itemLabel
        if (patch.status) it.status = patch.status
        if (patch.auditStatus) it.auditStatus = patch.auditStatus
        if (patch.sortNo !== undefined) it.sortNo = patch.sortNo
        return true
      }
    }
    return false
  }
  const raw = rawDictArray(dictCode)
  if (!raw) return false
  for (const it of raw) {
    if (it.id === id) {
      if (patch.itemLabel) it.label = patch.itemLabel
      if (patch.status) (it as { status?: string }).status = patch.status
      if (patch.auditStatus) it.auditStatus = patch.auditStatus
      return true
    }
  }
  return false
}

/** 按条目 id 定位所属类型编码 */
function locateItemType(id: string): string | null {
  for (const t of dictTypes) {
    const items = itemsOf(t.dictCode)
    for (const it of items) {
      if (it.id === id) return t.dictCode
    }
  }
  return null
}

/** 删除条目（源数组 splice，DR01 同源生效） */
function removeItem(dictCode: string, id: string): boolean {
  if (dictCode === 'MATERIAL_TYPE') {
    for (let i = materialItems.length - 1; i >= 0; i--) {
      if (materialItems[i].id === id) {
        materialItems.splice(i, 1)
        return true
      }
    }
    return false
  }
  const raw = rawDictArray(dictCode)
  if (!raw) return false
  for (let i = raw.length - 1; i >= 0; i--) {
    if (raw[i].id === id) {
      raw.splice(i, 1)
      return true
    }
  }
  return false
}

const configs: SysConfig[] = [
  { configKey: 'site.name', configValue: 'VIOManager 科创协作平台', configName: '站点名称', editable: true, remark: '登录页与页头展示' },
  { configKey: 'upload.maxMb', configValue: '20', configName: '上传大小上限（MB）', editable: true, remark: '骨架文件服务统一校验' },
  { configKey: 'approval.expireDays', configValue: '7', configName: '审批单有效期（天）', editable: true, remark: '到期自动转 EXPIRED' },
  { configKey: 'semester.switchWindowMin', configValue: '30', configName: '学期切换待执行窗口（分钟）', editable: false, remark: '涉及数据安全，仅运维改库' }
]

let typeSeq = 4

function superOnly(token: string): boolean {
  const u = findMockUserByToken(token)
  return !!u && u.role === 'SUPER_ADMIN'
}

// ── 类型管理 ──

registerMock({
  method: 'get',
  path: '/api/dicts',
  reply: (ctx) => {
    if (!superOnly(ctx.token)) return fail(1003, '仅超管可管理字典')
    return ok(dictTypes.map((t) => {
      const items = itemsOf(t.dictCode)
      let pending = 0
      for (const it of items) {
        if (it.auditStatus === 'PENDING') pending++
      }
      return { ...t, itemCount: items.length, pendingCount: pending }
    }))
  }
})

registerMock({
  method: 'post',
  path: '/api/dicts',
  reply: (ctx) => {
    if (!superOnly(ctx.token)) return fail(1003, '仅超管可管理字典')
    if (!ctx.idemKey) return fail(1001, '缺少幂等键（X-Idempotency-Key）')
    if (usedIdemKeys.has(ctx.idemKey)) return fail(1005, '请勿重复提交')
    const dictCode = String(ctx.body.dictCode ?? '').trim()
    const dictName = String(ctx.body.dictName ?? '').trim()
    if (!dictCode || !dictName) return fail(1001, '编码与名称不能为空')
    for (const t of dictTypes) {
      if (t.dictCode === dictCode) return fail(1001, '字典编码已存在')
    }
    usedIdemKeys.add(ctx.idemKey)
    const t: MockDictType = { id: 'dt' + typeSeq++, dictCode, dictName, remark: String(ctx.body.remark ?? ''), preset: false }
    dictTypes.push(t)
    return ok({ id: t.id })
  }
})

registerMock({
  method: 'put',
  path: '/api/dicts/types/:id',
  reply: (ctx) => {
    if (!superOnly(ctx.token)) return fail(1003, '仅超管可管理字典')
    for (const t of dictTypes) {
      if (t.id === ctx.params.id) {
        if (ctx.body.dictName) t.dictName = String(ctx.body.dictName).trim()
        if (ctx.body.remark !== undefined) t.remark = String(ctx.body.remark)
        return ok(null)
      }
    }
    return fail(1004, '字典类型不存在')
  }
})

registerMock({
  method: 'delete',
  path: '/api/dicts/types/:id',
  reply: (ctx) => {
    if (!superOnly(ctx.token)) return fail(1003, '仅超管可管理字典')
    for (const t of dictTypes) {
      if (t.id !== ctx.params.id) continue
      if (t.preset) return fail(1006, '预设类型不可删除')
      if (itemsOf(t.dictCode).length > 0) return fail(1006, '该类型下仍有条目，先清空再删')
      dictTypes.splice(dictTypes.indexOf(t), 1)
      return ok(null)
    }
    return fail(1004, '字典类型不存在')
  }
})

// ── 条目管理 ──

registerMock({
  method: 'get',
  path: '/api/dicts/:code/items',
  reply: (ctx) => {
    if (!superOnly(ctx.token)) return fail(1003, '仅超管可管理字典')
    let items = itemsOf(ctx.params.code)
    if (items.length === 0 && !dictTypes.some((t) => t.dictCode === ctx.params.code)) return fail(1004, '字典类型不存在')
    const source = String(ctx.query.source ?? '')
    const status = String(ctx.query.status ?? '')
    const auditStatus = String(ctx.query.auditStatus ?? '')
    const keyword = String(ctx.query.keyword ?? '').trim()
    const out: DictItemAdmin[] = []
    for (const it of items) {
      if (source && it.source !== source) continue
      if (status && it.status !== status) continue
      if (auditStatus && it.auditStatus !== auditStatus) continue
      if (keyword && it.itemLabel.indexOf(keyword) < 0 && it.itemValue.indexOf(keyword) < 0) continue
      out.push({ id: it.id, itemValue: it.itemValue, itemLabel: it.itemLabel, sortNo: it.sortNo, source: it.source, status: it.status, auditStatus: it.auditStatus, createdByName: it.createdByName })
    }
    return ok(out)
  }
})

// SY06（POST /api/dicts/:code/items 超管加预置项）由 mock/dict.ts 的同名处理器按 body 形状分派承载，
// 此处不再重复注册（matchHandler 顺序匹配，先注册的 dict.ts 会命中）

registerMock({
  method: 'put',
  path: '/api/dicts/items/:id',
  reply: (ctx) => {
    if (!superOnly(ctx.token)) return fail(1003, '仅超管可管理字典')
    const label = String(ctx.body.itemLabel ?? '').trim()
    if (!label) return fail(1001, '名称不能为空')
    const code = locateItemType(ctx.params.id)
    if (!code) return fail(1004, '条目不存在')
    writeItem(code, ctx.params.id, { itemLabel: label, sortNo: ctx.body.sortNo !== undefined ? Number(ctx.body.sortNo) : undefined })
    return ok(null)
  }
})

registerMock({
  method: 'delete',
  path: '/api/dicts/items/:id',
  reply: (ctx) => {
    if (!superOnly(ctx.token)) return fail(1003, '仅超管可管理字典')
    const code = locateItemType(ctx.params.id)
    if (!code) return fail(1004, '条目不存在')
    removeItem(code, ctx.params.id)
    return ok(null)
  }
})

registerMock({
  method: 'put',
  path: '/api/dicts/items/:id/status',
  reply: (ctx) => {
    if (!superOnly(ctx.token)) return fail(1003, '仅超管可管理字典')
    const enabled = String(ctx.body.status ?? '') === 'ENABLED'
    const code = locateItemType(ctx.params.id)
    if (!code) return fail(1004, '条目不存在')
    writeItem(code, ctx.params.id, { status: enabled ? 'ENABLED' : 'DISABLED' })
    return ok(null)
  }
})

registerMock({
  method: 'put',
  path: '/api/dicts/items/:id/audit',
  reply: (ctx) => {
    if (!superOnly(ctx.token)) return fail(1003, '仅超管可审核')
    const auditStatus = String(ctx.body.auditStatus ?? '')
    if (auditStatus !== 'APPROVED' && auditStatus !== 'REJECTED') return fail(1001, '审核结论不合法')
    const code = locateItemType(ctx.params.id)
    if (!code) return fail(1004, '条目不存在')
    const items = itemsOf(code)
    for (const it of items) {
      if (it.id !== ctx.params.id) continue
      if (it.source !== 'USER' || it.auditStatus !== 'PENDING') return fail(1006, '该词条不在待审核状态')
      writeItem(code, it.id, { auditStatus: auditStatus as 'APPROVED' | 'REJECTED' })
      return ok(null)
    }
    return fail(1004, '条目不存在')
  }
})

// ── 系统参数 ──

registerMock({
  method: 'get',
  path: '/api/configs',
  reply: (ctx) => {
    if (!superOnly(ctx.token)) return fail(1003, '仅超管可查看系统参数')
    return ok(configs)
  }
})

registerMock({
  method: 'put',
  path: '/api/configs/:key',
  reply: (ctx) => {
    if (!superOnly(ctx.token)) return fail(1003, '仅超管可修改系统参数')
    for (const c of configs) {
      if (c.configKey !== ctx.params.key) continue
      if (!c.editable) return fail(1006, '该参数不可在线修改')
      c.configValue = String(ctx.body.configValue ?? '')
      return ok(null)
    }
    return fail(1004, '参数不存在')
  }
})
