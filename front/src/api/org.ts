/**
 * 组织管理接口（A 线 O01–O06）
 * 对应清单：docs/api/A线-基础数据-接口清单.md §2
 * TODO(联调)：O02/O03 的 leaderName 当前直接传姓名；人员选择器落地后改传 managerId
 */
import { del, get, post, put } from './http'

/** 组织树节点（O01） */
export interface OrgNode {
  id: string
  name: string
  orgType: string
  isSubOrg: boolean
  managerName: string
  leaderName: string
  sortNo: number
  status: 'ENABLED' | 'DISABLED'
  level: number
  children: OrgNode[]
}

/** 学院-专业静态映射项（O06，后端内置生成不查库） */
export interface CollegeItem {
  college: string
  majors: string[]
}

/** 组织新建/编辑表单 */
export interface OrgForm {
  parentId: string
  name: string
  leaderName: string
  sortNo: number
}

export function apiOrgTree(): Promise<OrgNode[]> {
  return get('/api/orgs/tree')
}

export function apiOrgDetail(id: string): Promise<OrgNode> {
  return get('/api/orgs/' + id)
}

export function apiCreateOrg(body: OrgForm): Promise<OrgNode> {
  return post('/api/orgs', body)
}

/** 编辑；改上级由后端校验防循环引用（3006） */
export function apiUpdateOrg(id: string, body: OrgForm): Promise<OrgNode> {
  return put('/api/orgs/' + id, body)
}

/** 有子组织拒绝删除（3007）；删除后该组织人员 org_id 置空 */
export function apiDeleteOrg(id: string): Promise<null> {
  return del('/api/orgs/' + id)
}

export function apiColleges(): Promise<CollegeItem[]> {
  return get('/api/orgs/colleges')
}
