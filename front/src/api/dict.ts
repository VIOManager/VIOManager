/**
 * 字典接口（A 线 D01–D03：字典读取 / 用户补充 / 管理员审核）
 * 对应清单：docs/api/A线-基础数据-接口清单.md §3 技能字典
 * 红线（方案 §5）：页面禁止硬编码枚举翻译，一律走 useDict
 */
import { get, post } from './http'

export type DictSource = 'PRESET' | 'USER'
export type DictAuditStatus = 'APPROVED' | 'PENDING' | 'REJECTED'

export interface DictItem {
  id: string
  label: string
  source: DictSource
  auditStatus: DictAuditStatus
  /** 用户补充项的补充人姓名（审核页展示用） */
  createdByName?: string
}

export function apiDictItems(type: string): Promise<DictItem[]> {
  return get('/api/dicts/' + type)
}

/** 用户补充新字典项（入库为 PENDING 待审核，审核通过前不可被选用） */
export function apiAddDictItem(type: string, label: string): Promise<DictItem> {
  return post('/api/dicts/' + type + '/items', { label })
}

/** 管理员审核用户补充项：pass=true 通过入库，false 驳回 */
export function apiAuditDictItem(itemId: string, pass: boolean): Promise<null> {
  return post('/api/dicts/items/' + itemId + '/audit', { pass })
}
