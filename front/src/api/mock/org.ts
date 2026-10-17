/**
 * 组织管理 mock（A 线 O01–O06）
 * 数据范围：部门管理员仅见/仅可管理本部门子树（scope.ts，模拟后端 DataScope）
 * 关键校验：3006 上级不能选自身或子孙；3007 有子组织拒删；根组织不可删；越权操作 1003
 * TODO(联调)：后端组织接口就绪后删除本文件
 */
import { fail, ok, registerMock } from './registry'
import { findNode, findParent, isDescendant, tree } from './org-data'
import { deptScopeOrgIds, deptScopeRoot, orgInScope } from './scope'
import type { CollegeItem, OrgNode } from '@/api/org'

let seq = 20

/** 13 学院 39 专业的演示子集（真实全量由后端内置生成） */
const colleges: CollegeItem[] = [
  { college: '计算机与信息工程学院', majors: ['软件工程', '计算机科学与技术', '物联网工程'] },
  { college: '电子与信息工程学院', majors: ['电子信息工程', '通信工程'] },
  { college: '机械工程学院', majors: ['机械设计制造及其自动化', '车辆工程'] },
  { college: '建筑工程学院', majors: ['土木工程', '建筑学'] },
  { college: '理学院', majors: ['数学与应用数学', '应用物理学'] }
]

// ⚠️ 注册顺序：/tree 与 /colleges 必须先于 /:id，否则会被 :id 段吃掉
registerMock({
  method: 'get',
  path: '/api/orgs/tree',
  reply: (ctx) => {
    // 部门管理员：以自己所在组织为根的子树
    const root = deptScopeRoot(ctx.token)
    return ok(root ? [root] : tree)
  }
})
registerMock({ method: 'get', path: '/api/orgs/colleges', reply: () => ok(colleges) })

registerMock({
  method: 'get',
  path: '/api/orgs/:id',
  reply: (ctx) => {
    const scope = deptScopeOrgIds(ctx.token)
    const node = findNode(tree, ctx.params.id)
    if (!node) return fail(1004, '组织不存在')
    if (!orgInScope(node.id, scope)) return fail(1003, '无权查看本部门以外的组织')
    return ok(node)
  }
})

registerMock({
  method: 'post',
  path: '/api/orgs',
  reply: (ctx) => {
    const scope = deptScopeOrgIds(ctx.token)
    const name = String(ctx.body.name ?? '').trim()
    if (!name) return fail(1001, '组织名称不能为空')
    const parentId = String(ctx.body.parentId ?? '')
    const parent = parentId ? findNode(tree, parentId) : null
    if (parentId && !parent) return fail(1004, '上级组织不存在')
    if (parent && !orgInScope(parent.id, scope)) return fail(1003, '只能在本人部门范围内创建组织')
    const node: OrgNode = {
      id: 'o' + seq++,
      name,
      orgType: String(ctx.body.orgType ?? 'SUB_ORG'),
      isSubOrg: true,
      managerName: String(ctx.body.leaderName ?? ''),
      leaderName: String(ctx.body.leaderName ?? ''),
      sortNo: Number(ctx.body.sortNo ?? 99),
      status: 1,
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
    const scope = deptScopeOrgIds(ctx.token)
    const node = findNode(tree, ctx.params.id)
    if (!node) return fail(1004, '组织不存在')
    if (!orgInScope(node.id, scope)) return fail(1003, '无权管理本部门以外的组织')
    const newParentId = String(ctx.body.parentId ?? '')
    if (newParentId && newParentId !== (findParent(tree, node.id, null)?.id ?? '')) {
      // 换上级：防循环校验（3006）
      if (newParentId === node.id || isDescendant(node, newParentId)) return fail(3006, '上级组织不能选自身或其子孙')
      const newParent = findNode(tree, newParentId)
      if (!newParent) return fail(1004, '上级组织不存在')
      if (!orgInScope(newParent.id, scope)) return fail(1003, '上级组织超出本人部门范围')
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
    const scope = deptScopeOrgIds(ctx.token)
    const node = findNode(tree, ctx.params.id)
    if (!node) return fail(1004, '组织不存在')
    if (!orgInScope(node.id, scope)) return fail(1003, '无权删除本部门以外的组织')
    if (node.orgType === 'ROOT') return fail(1003, '根组织不允许删除')
    if (node.children.length > 0) return fail(3007, '组织下有子组织，禁止删除')
    const parent = findParent(tree, node.id, null)
    const siblings = parent ? parent.children : tree
    siblings.splice(siblings.indexOf(node), 1)
    return ok(null)
  }
})
