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
import { skillItems } from './dict'
import { MATERIAL_TYPES } from '@/config/labels'
import type { DictItemAdmin, SysConfig } from '@/api/system'

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

const kbItems: MockItem[] = [
  { id: 'kbi1', dictCode: 'KB_CATEGORY', itemValue: 'TRAINING', itemLabel: '培训笔记', sortNo: 1, source: 'PRESET', status: 'ENABLED', auditStatus: 'APPROVED', createdByName: '' },
  { id: 'kbi2', dictCode: 'KB_CATEGORY', itemValue: 'TROUBLESHOOT', itemLabel: '排障记录', sortNo: 2, source: 'PRESET', status: 'ENABLED', auditStatus: 'APPROVED', createdByName: '' },
  { id: 'kbi3', dictCode: 'KB_CATEGORY', itemValue: 'RETRO', itemLabel: '复盘总结', sortNo: 3, source: 'PRESET', status: 'ENABLED', auditStatus: 'APPROVED', createdByName: '' },
  { id: 'kbi4', dictCode: 'KB_CATEGORY', itemValue: 'RULES', itemLabel: '规章制度', sortNo: 4, source: 'PRESET', status: 'ENABLED', auditStatus: 'APPROVED', createdByName: '' }
]

/** SKILL 条目适配为管理视图（itemValue=label 小写化，status 默认启用；旧结构无 status 视为 ENABLED） */
function skillAdminView(): MockItem[] {
  return skillItems.map((s, i) => ({
    id: s.id, dictCode: 'SKILL', itemValue: s.label, itemLabel: s.label, sortNo: i + 1,
    source: s.source, status: (s as { status?: 'ENABLED' | 'DISABLED' }).status ?? 'ENABLED',
    auditStatus: s.auditStatus, createdByName: s.createdByName ?? ''
  }))
}

/** 按类型取条目数组（SKILL 返回适配视图，其余返回原数组） */
function itemsOf(dictCode: string): MockItem[] {
  if (dictCode === 'SKILL') return skillAdminView()
  if (dictCode === 'MATERIAL_TYPE') return materialItems
  if (dictCode === 'KB_CATEGORY') return kbItems
  return []
}

/** 任意类型下按 id 找条目（返回可写引用；SKILL 回源 skillItems） */
function findItemAnywhere(id: string): MockItem | null {
  for (const s of skillItems) {
    if (s.id === id) {
      const ext = s as { status?: 'ENABLED' | 'DISABLED' }
      return {
        id: s.id, dictCode: 'SKILL', itemValue: s.label, itemLabel: s.label, sortNo: 0,
        source: s.source, status: ext.status ?? 'ENABLED', auditStatus: s.auditStatus, createdByName: s.createdByName ?? ''
      }
    }
  }
  for (const it of materialItems) {
    if (it.id === id) return it
  }
  for (const it of kbItems) {
    if (it.id === id) return it
  }
  return null
}

/** SKILL 条目写回（审核/启停直接改 skillItems 原对象） */
function writeSkill(id: string, patch: Partial<{ auditStatus: 'PENDING' | 'APPROVED' | 'REJECTED'; status: 'ENABLED' | 'DISABLED' }>): boolean {
  for (const s of skillItems) {
    if (s.id === id) {
      if (patch.auditStatus) s.auditStatus = patch.auditStatus
      if (patch.status) (s as { status?: string }).status = patch.status
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
    // SKILL 回源改 label；其余改数组项
    for (const s of skillItems) {
      if (s.id === ctx.params.id) {
        s.label = label
        return ok(null)
      }
    }
    for (const arr of [materialItems, kbItems]) {
      for (const it of arr) {
        if (it.id === ctx.params.id) {
          it.itemLabel = label
          if (ctx.body.sortNo !== undefined) it.sortNo = Number(ctx.body.sortNo)
          return ok(null)
        }
      }
    }
    return fail(1004, '条目不存在')
  }
})

registerMock({
  method: 'delete',
  path: '/api/dicts/items/:id',
  reply: (ctx) => {
    if (!superOnly(ctx.token)) return fail(1003, '仅超管可管理字典')
    for (let i = skillItems.length - 1; i >= 0; i--) {
      if (skillItems[i].id === ctx.params.id) {
        skillItems.splice(i, 1)
        return ok(null)
      }
    }
    for (const arr of [materialItems, kbItems]) {
      for (let i = arr.length - 1; i >= 0; i--) {
        if (arr[i].id === ctx.params.id) {
          arr.splice(i, 1)
          return ok(null)
        }
      }
    }
    return fail(1004, '条目不存在')
  }
})

registerMock({
  method: 'put',
  path: '/api/dicts/items/:id/status',
  reply: (ctx) => {
    if (!superOnly(ctx.token)) return fail(1003, '仅超管可管理字典')
    const enabled = String(ctx.body.status ?? '') === 'ENABLED'
    if (writeSkill(ctx.params.id, { status: enabled ? 'ENABLED' : 'DISABLED' })) return ok(null)
    for (const arr of [materialItems, kbItems]) {
      for (const it of arr) {
        if (it.id === ctx.params.id) {
          it.status = enabled ? 'ENABLED' : 'DISABLED'
          return ok(null)
        }
      }
    }
    return fail(1004, '条目不存在')
  }
})

registerMock({
  method: 'put',
  path: '/api/dicts/items/:id/audit',
  reply: (ctx) => {
    if (!superOnly(ctx.token)) return fail(1003, '仅超管可审核')
    const auditStatus = String(ctx.body.auditStatus ?? '')
    if (auditStatus !== 'APPROVED' && auditStatus !== 'REJECTED') return fail(1001, '审核结论不合法')
    const item = findItemAnywhere(ctx.params.id)
    if (!item) return fail(1004, '条目不存在')
    if (item.source !== 'USER' || item.auditStatus !== 'PENDING') return fail(1006, '该词条不在待审核状态')
    writeSkill(ctx.params.id, { auditStatus: auditStatus as 'APPROVED' })
    return ok(null)
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
