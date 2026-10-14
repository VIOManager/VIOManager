/**
 * 系统配置接口（SY01–SY10 字典管理 + CF01/CF02 系统参数）
 * 契约源：docs/api/系统配置-接口清单.md（2026-10-13 入库 main；录入 Apifox 后以 Apifox 为准）
 * 超管独占（非超管 1003）；字典读取 DR01 在 @/api/dict（全角色）
 * 用户补充词条审核流：PENDING → APPROVED/REJECTED（SY10）；停用优先于删除（SY08 不做引用强校验）
 * 写操作挂审计；创建/审核类带幂等头（规范 §6）
 */
import { del, get, post, postIdem, put } from './http'

export interface DictType {
  id: string
  dictCode: string
  dictName: string
  remark: string
  itemCount: number
  pendingCount: number
  /** 预设类型（建库种子）不可删 */
  preset: boolean
}

export interface DictItemAdmin {
  id: string
  itemValue: string
  itemLabel: string
  sortNo: number
  source: 'PRESET' | 'USER'
  status: 'ENABLED' | 'DISABLED'
  auditStatus: 'PENDING' | 'APPROVED' | 'REJECTED'
  createdByName: string
}

export interface SysConfig {
  configKey: string
  configValue: string
  configName: string
  editable: boolean
  remark: string
}

// ── 字典类型 ──

export function apiDictTypes(): Promise<DictType[]> {
  return get('/api/dicts')
}

export function apiCreateDictType(body: { dictCode: string; dictName: string; remark?: string }): Promise<{ id: string }> {
  return postIdem('/api/dicts', body, crypto.randomUUID())
}

/** SY03 编辑名称/说明（dictCode 创建后不可改） */
export function apiUpdateDictType(id: string, body: { dictName: string; remark?: string }): Promise<null> {
  return put('/api/dicts/types/' + id, body)
}

/** SY04 删除：预设或仍有条目 → 1006 */
export function apiDeleteDictType(id: string): Promise<null> {
  return del('/api/dicts/types/' + id)
}

// ── 字典条目 ──

export function apiDictAdminItems(code: string, q?: { source?: string; status?: string; auditStatus?: string; keyword?: string }): Promise<DictItemAdmin[]> {
  return get('/api/dicts/' + code + '/items', (q ?? {}) as Record<string, unknown>)
}

/** SY06 新增预置条目（直接 APPROVED）；value 重复 → 1001 */
export function apiCreateDictItem(code: string, body: { itemValue: string; itemLabel: string; sortNo?: number }): Promise<{ id: string }> {
  return postIdem('/api/dicts/' + code + '/items', body, crypto.randomUUID())
}

export function apiUpdateDictItem(id: string, body: { itemLabel: string; sortNo?: number }): Promise<null> {
  return put('/api/dicts/items/' + id, body)
}

export function apiDeleteDictItem(id: string): Promise<null> {
  return del('/api/dicts/items/' + id)
}

/** SY09 启用/停用（停用项不再出现在 DR01） */
export function apiToggleDictItem(id: string, enabled: boolean): Promise<null> {
  return put('/api/dicts/items/' + id + '/status', { status: enabled ? 'ENABLED' : 'DISABLED' })
}

/** SY10 审核用户补充词条（comment 选填） */
export function apiAuditDictItemAdmin(id: string, auditStatus: 'APPROVED' | 'REJECTED', comment?: string): Promise<null> {
  return put('/api/dicts/items/' + id + '/audit', { auditStatus, comment })
}

// ── 系统参数（种子维护，不提供增删）──

export function apiConfigs(): Promise<SysConfig[]> {
  return get('/api/configs')
}

/** CF02 修改参数值（editable=0 → 1006） */
export function apiUpdateConfig(key: string, value: string): Promise<null> {
  return put('/api/configs/' + key, { configValue: value })
}
