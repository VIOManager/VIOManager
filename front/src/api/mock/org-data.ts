/**
 * 组织树数据与查找（从 mock/org.ts 拆出，供 scope.ts 等模块安全引用避免循环依赖）
 * 演示结构：本部 → 数媒协会（→ 数媒二楼）/ 电控工作室 / 建模组
 */
import type { OrgNode } from '@/api/org'

export const tree: OrgNode[] = [
  {
    id: 'o1',
    name: '科创团队本部',
    orgType: 'ROOT',
    isSubOrg: false,
    managerName: '李明',
    leaderName: '李明',
    sortNo: 1,
    status: 'ENABLED',
    level: 1,
    children: [
      {
        id: 'o11', name: '数媒协会', orgType: 'DEPARTMENT', isSubOrg: true, managerName: '陈志远', leaderName: '陈志远',
        sortNo: 1, status: 'ENABLED', level: 2,
        children: [
          { id: 'o111', name: '数媒二楼', orgType: 'DEPARTMENT', isSubOrg: true, managerName: '周子墨', leaderName: '周子墨', sortNo: 1, status: 'ENABLED', level: 3, children: [] }
        ]
      },
      { id: 'o12', name: '电控工作室', orgType: 'DEPARTMENT', isSubOrg: true, managerName: '赵天宇', leaderName: '赵天宇', sortNo: 2, status: 'ENABLED', level: 2, children: [] },
      { id: 'o13', name: '建模组', orgType: 'DEPARTMENT', isSubOrg: true, managerName: '陈子昂', leaderName: '陈子昂', sortNo: 3, status: 'ENABLED', level: 2, children: [] }
    ]
  }
]

export function findNode(list: OrgNode[], id: string): OrgNode | null {
  for (const n of list) {
    if (n.id === id) return n
    const hit = findNode(n.children, id)
    if (hit) return hit
  }
  return null
}

export function findParent(list: OrgNode[], id: string, parent: OrgNode | null): OrgNode | null {
  for (const n of list) {
    if (n.id === id) return parent
    const hit = findParent(n.children, id, n)
    if (hit) return hit
  }
  return null
}

/** 判断 id 是否为 ancestor 的子孙节点（3006 防循环用） */
export function isDescendant(ancestor: OrgNode, id: string): boolean {
  for (const c of ancestor.children) {
    if (c.id === id) return true
    if (isDescendant(c, id)) return true
  }
  return false
}

/** 供其他 mock 模块按 id 反查组织（人员档案回填 orgName 用） */
export function findOrgNodeById(id: string): OrgNode | null {
  return findNode(tree, id)
}
