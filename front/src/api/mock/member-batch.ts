/**
 * 人员批量删除 mock（M05B，2026-10-24 拍板"删除等操作要批量"）
 * 整批拒绝口径：任一 id 不可删 → 全批不动，message 列出不可删名单与原因（导错重来的场景要全清，半删更乱）
 * 保护分支复用 M05 单删：3003 队长拒删（李明轩）/ 3004 当前登录账号；另加 3006 超上限 / 1003 越权
 * TODO(联调)：后端 M05B 落地后删除本文件（方案：后端 .agentdocs/plans/2026-10-24/人员批量能力-方案 §二）
 */
import { fail, ok, registerMock } from './registry'
import { meAsMember } from './member'
import { deptScopeOrgIds, orgInScope } from './scope'
import { members } from './member-data'
import type { MockMember } from './member-data'

/** 单次批量上限（防误选全表） */
const MAX_BATCH = 200

/** 按 id 找成员 */
function findById(id: string): MockMember | null {
  for (const m of members) {
    if (m.id === id) return m
  }
  return null
}

registerMock({
  method: 'delete',
  path: '/api/members',
  reply: (ctx) => {
    const ids = Array.isArray(ctx.body.ids) ? (ctx.body.ids as string[]) : []
    if (ids.length === 0) return fail(1001, '未选择任何人员')
    if (ids.length > MAX_BATCH) return fail(3006, '单次批量删除上限 ' + MAX_BATCH + ' 人')
    // 第一步：全部找得到且都在数据范围内，否则整批拒绝
    const scope = deptScopeOrgIds(ctx.token)
    const targets: MockMember[] = []
    for (const id of ids) {
      const m = findById(id)
      if (!m) return fail(1004, '人员不存在：' + id)
      if (!orgInScope(m.orgId, scope)) return fail(1003, '无权删除本部门以外的人员')
      targets.push(m)
    }
    // 第二步：逐个套 M05 规则（队长 3003 / 当前登录 3004），有不可删的列名单整批拒绝
    const me = meAsMember(ctx.token)
    const blocked: string[] = []
    let leaderBlocked = false
    for (const m of targets) {
      if (m.isLeader) {
        blocked.push(m.name + '：队长，需先解散队伍')
        leaderBlocked = true
      } else if (me && me.id === m.id) {
        blocked.push(m.name + '：当前登录账号')
      }
    }
    if (blocked.length > 0) {
      return fail(leaderBlocked ? 3003 : 3004, '整批未删除，不可删——' + blocked.join('；'))
    }
    // 第三步：全批可删才动手（普通队员的队伍关系清理由后端保证，mock 直接移除）
    for (const m of targets) {
      members.splice(members.indexOf(m), 1)
    }
    return ok(null)
  }
})
