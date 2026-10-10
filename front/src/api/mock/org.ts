/**
 * 组织管理 mock（A 线 O01–O06）
 * ⚠️ 演示数据：本部 + 三个子组织（数媒协会下挂数媒二楼，用于演示多级树与 3006 防循环）
 * 关键校验：3006 上级不能选自身或子孙；3007 有子组织拒删；根组织不可删
 * TODO(联调)：后端组织接口就绪后删除本文件
 */
import { fail, ok, registerMock } from './registry'
import type { CollegeItem, OrgNode } from '@/api/org'

let seq = 20

const tree: OrgNode[] = [
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

/** 供其他 mock 模块按 id 反查组织（人员档案回填 orgName 用） */
export function findOrgNodeById(id: string): OrgNode | null {
  return findNode(tree, id)
}

function findNode(list: OrgNode[], id: string): OrgNode | null {
  for (const n of list) {
    if (n.id === id) return n
    const hit = findNode(n.children, id)
    if (hit) return hit
  }
  return null
}

function findParent(list: OrgNode[], id: string, parent: OrgNode | null): OrgNode | null {
  for (const n of list) {
    if (n.id === id) return parent
    const hit = findParent(n.children, id, n)
    if (hit) return hit
  }
  return null
}

/** 判断 id 是否为 ancestor 的子孙节点（3006 防循环用） */
function isDescendant(ancestor: OrgNode, id: string): boolean {
  for (const c of ancestor.children) {
    if (c.id === id) return true
    if (isDescendant(c, id)) return true
  }
  return false
}

/** 13 学院 39 专业的演示子集（真实全量由后端内置生成） */
const colleges: CollegeItem[] = [
  { college: '计算机与信息工程学院', majors: ['软件工程', '计算机科学与技术', '物联网工程'] },
  { college: '电子与信息工程学院', majors: ['电子信息工程', '通信工程'] },
  { college: '机械工程学院', majors: ['机械设计制造及其自动化', '车辆工程'] },
  { college: '建筑工程学院', majors: ['土木工程', '建筑学'] },
  { college: '理学院', majors: ['数学与应用数学', '应用物理学'] }
]

// ⚠️ 注册顺序：/tree 与 /colleges 必须先于 /:id，否则会被 :id 段吃掉
registerMock({ method: 'get', path: '/api/orgs/tree', reply: () => ok(tree) })
registerMock({ method: 'get', path: '/api/orgs/colleges', reply: () => ok(colleges) })

registerMock({
  method: 'get',
  path: '/api/orgs/:id',
  reply: (ctx) => {
    const node = findNode(tree, ctx.params.id)
    return node ? ok(node) : fail(1004, '组织不存在')
  }
})

registerMock({
  method: 'post',
  path: '/api/orgs',
  reply: (ctx) => {
    const name = String(ctx.body.name ?? '').trim()
    if (!name) return fail(1001, '组织名称不能为空')
    const parentId = String(ctx.body.parentId ?? '')
    const parent = parentId ? findNode(tree, parentId) : null
    if (parentId && !parent) return fail(1004, '上级组织不存在')
    const node: OrgNode = {
      id: 'o' + seq++,
      name,
      orgType: 'DEPARTMENT',
      isSubOrg: true,
      managerName: String(ctx.body.leaderName ?? ''),
      leaderName: String(ctx.body.leaderName ?? ''),
      sortNo: Number(ctx.body.sortNo ?? 99),
      status: 'ENABLED',
      level: parent ? parent.level + 1 : 1,
      children: []
    }
    if (parent) parent.children.push(node)
    else tree.push(node)
    return ok(node)
  }
})

registerMock({
  method: 'put',
  path: '/api/orgs/:id',
  reply: (ctx) => {
    const node = findNode(tree, ctx.params.id)
    if (!node) return fail(1004, '组织不存在')
    const newParentId = String(ctx.body.parentId ?? '')
    if (newParentId && newParentId !== (findParent(tree, node.id, null)?.id ?? '')) {
      // 换上级：防循环校验（3006）
      if (newParentId === node.id || isDescendant(node, newParentId)) return fail(3006, '上级组织不能选自身或其子孙')
      const newParent = findNode(tree, newParentId)
      if (!newParent) return fail(1004, '上级组织不存在')
      const oldParent = findParent(tree, node.id, null)
      const oldSiblings = oldParent ? oldParent.children : tree
      oldSiblings.splice(oldSiblings.indexOf(node), 1)
      newParent.children.push(node)
      node.level = newParent.level + 1
    }
    const name = String(ctx.body.name ?? '').trim()
    if (name) node.name = name
    node.leaderName = String(ctx.body.leaderName ?? node.leaderName)
    node.managerName = node.leaderName
    node.sortNo = Number(ctx.body.sortNo ?? node.sortNo)
    return ok(node)
  }
})

registerMock({
  method: 'delete',
  path: '/api/orgs/:id',
  reply: (ctx) => {
    const node = findNode(tree, ctx.params.id)
    if (!node) return fail(1004, '组织不存在')
    if (node.orgType === 'ROOT') return fail(1003, '根组织不允许删除')
    if (node.children.length > 0) return fail(3007, '组织下有子组织，禁止删除')
    const parent = findParent(tree, node.id, null)
    const siblings = parent ? parent.children : tree
    siblings.splice(siblings.indexOf(node), 1)
    return ok(null)
  }
})
