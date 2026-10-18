/**
 * 组织管理接口（A 线 O01–O06）
 * 对应清单：docs/api/A线-基础数据-接口清单.md §2
 * TODO(联调)：O02/O03 的 leaderName 当前直接传姓名；人员选择器落地后改传 managerId
 */
import { get, postIdem, putIdem, delIdem } from './http'

/** 组织树节点（O01）。2026-10-17 联调对齐：后端 status 是数字（1 启用 0 停用）、节点带 parentId */
export interface OrgNode {
  id: string
  /** 上级组织 ID，根节点为 "0"；编辑弹窗回填当前上级用（mock 嵌套数据无此字段） */
  parentId?: string
  name: string
  orgType: string
  isSubOrg: boolean
  managerName: string
  leaderName: string
  sortNo: number
  /** 1 启用 0 停用（后端 VO Integer） */
  status: number
  level: number
  children: OrgNode[]
}

/** 学院-专业静态映射项（O06，后端内置生成不查库） */
export interface CollegeItem {
  college: string
  majors: string[]
}

/** 组织新建/编辑表单（2026-10-17 对齐后端 OrgSaveDTO：orgType 必填；code/managerId/remark 可空暂不收） */
export interface OrgForm {
  parentId: string
  name: string
  orgType: string
  leaderName: string
  sortNo: number
}

export function apiOrgTree(): Promise<OrgNode[]> {
  return get('/api/orgs/tree')
}

/**
 * 组织树静默版（2026-10-16 联调降级）：A 线后端尚未实现 /api/orgs/tree（返回 1004），
 * 辅助场景（筛选下拉/绩效/排班）调它不该弹"资源不存在"吓人——静默失败回空数组，
 * 组织管理页（主数据）仍走 apiOrgTree 保留报错。A 线上线后两者行为一致。
 */
export async function apiOrgTreeSafe(): Promise<OrgNode[]> {
  try {
    return await get<OrgNode[]>('/api/orgs/tree', undefined, { silent: true })
  } catch {
    return []
  }
}

export function apiOrgDetail(id: string): Promise<OrgNode> {
  return get('/api/orgs/' + id)
}

/** 2026-10-17 联调对齐（A 线 org 后端上线）：写操作带 X-Idempotency-Key（ORG_CREATE/UPDATE/DELETE 均 @Idempotent） */
export function apiCreateOrg(body: OrgForm): Promise<OrgNode> {
  return postIdem('/api/orgs', body, crypto.randomUUID())
}

/** 编辑；改上级由后端校验防循环引用（3006） */
export function apiUpdateOrg(id: string, body: OrgForm): Promise<OrgNode> {
  return putIdem('/api/orgs/' + id, body, crypto.randomUUID())
}

/** 有子组织拒绝删除（3007）；删除后该组织人员 org_id 置空 */
export function apiDeleteOrg(id: string): Promise<null> {
  return delIdem('/api/orgs/' + id, crypto.randomUUID())
}

export function apiColleges(): Promise<CollegeItem[]> {
  return get('/api/orgs/colleges')
}
