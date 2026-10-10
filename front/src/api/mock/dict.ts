/**
 * 字典模块 mock（A 线 D01–D03）
 * ⚠️ 演示数据：SKILL 预置 8 项 + 用户补充 3 条（待审/已通过/已驳回各一），
 *    对应 FS3 验收点"下拉含用户补充项""审核后状态变更"
 * 暂定错误码：1003 无权限操作；1004 目标不存在（公共段，待骨架清单拍板）
 * TODO(联调)：后端字典接口就绪后删除本文件
 */
import { fail, ok, registerMock } from './registry'
import { findMockUserByToken } from './auth'
import type { DictItem } from '@/api/dict'

let seq = 100

/** SKILL 技能字典（内存数据，刷新还原） */
const skillItems: DictItem[] = [
  { id: 'd1', label: '电控', source: 'PRESET', auditStatus: 'APPROVED' },
  { id: 'd2', label: '建模', source: 'PRESET', auditStatus: 'APPROVED' },
  { id: 'd3', label: 'AI', source: 'PRESET', auditStatus: 'APPROVED' },
  { id: 'd4', label: '视频剪辑', source: 'PRESET', auditStatus: 'APPROVED' },
  { id: 'd5', label: '编程开发', source: 'PRESET', auditStatus: 'APPROVED' },
  { id: 'd6', label: '机械设计', source: 'PRESET', auditStatus: 'APPROVED' },
  { id: 'd7', label: '文案策划', source: 'PRESET', auditStatus: 'APPROVED' },
  { id: 'd8', label: '视觉设计', source: 'PRESET', auditStatus: 'APPROVED' },
  { id: 'd9', label: '嵌入式开发', source: 'USER', auditStatus: 'PENDING', createdByName: '王雨桐' },
  { id: 'd10', label: '结构设计', source: 'USER', auditStatus: 'APPROVED', createdByName: '李明轩' },
  { id: 'd11', label: '摄影', source: 'USER', auditStatus: 'REJECTED', createdByName: '陈子昂' }
]

/** 字典登记表：type → 词条数组（目前仅 SKILL，后续类型在此追加） */
const DICTS: Record<string, DictItem[]> = { SKILL: skillItems }

registerMock({
  method: 'get',
  path: '/api/dicts/:type',
  reply: (ctx) => {
    const items = DICTS[ctx.params.type]
    if (!items) return fail(1004, '字典类型不存在')
    return ok(items)
  }
})

registerMock({
  method: 'post',
  path: '/api/dicts/:type/items',
  reply: (ctx) => {
    const items = DICTS[ctx.params.type]
    if (!items) return fail(1004, '字典类型不存在')
    const label = String(ctx.body.label ?? '').trim()
    if (!label) return fail(1001, '参数不完整')
    for (const it of items) {
      if (it.label === label) return fail(1001, '该词条已存在')
    }
    const me = findMockUserByToken(ctx.token)
    const item: DictItem = {
      id: 'd' + seq++,
      label,
      source: 'USER',
      auditStatus: 'PENDING',
      createdByName: me ? me.realName : '未知'
    }
    items.push(item)
    return ok(item)
  }
})

registerMock({
  method: 'post',
  path: '/api/dicts/items/:id/audit',
  reply: (ctx) => {
    const me = findMockUserByToken(ctx.token)
    if (!me || me.role === 'STUDENT') return fail(1003, '无权限操作')
    const pass = ctx.body.pass === true
    for (const type of Object.keys(DICTS)) {
      for (const it of DICTS[type]) {
        if (it.id === ctx.params.id) {
          if (it.source !== 'USER' || it.auditStatus !== 'PENDING') return fail(1001, '该词条不在待审核状态')
          it.auditStatus = pass ? 'APPROVED' : 'REJECTED'
          return ok(null)
        }
      }
    }
    return fail(1004, '词条不存在')
  }
})
