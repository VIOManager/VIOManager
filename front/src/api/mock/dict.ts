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

/** SKILL 技能字典（内存数据，刷新还原）；预置项按 PRD §3.2：python/CAD/AIGC/视频剪辑/机械设计/其他
 *  导出供 mock/system.ts 管理侧共享同一份数据（/system 审核后人员页下拉同步变化） */
export const skillItems: DictItem[] = [
  { id: 'd1', label: 'python', source: 'PRESET', auditStatus: 'APPROVED' },
  { id: 'd2', label: 'CAD', source: 'PRESET', auditStatus: 'APPROVED' },
  { id: 'd3', label: 'AIGC', source: 'PRESET', auditStatus: 'APPROVED' },
  { id: 'd4', label: '视频剪辑', source: 'PRESET', auditStatus: 'APPROVED' },
  { id: 'd5', label: '机械设计', source: 'PRESET', auditStatus: 'APPROVED' },
  { id: 'd6', label: '其他', source: 'PRESET', auditStatus: 'APPROVED' },
  { id: 'd9', label: '嵌入式开发', source: 'USER', auditStatus: 'PENDING', createdByName: '王雨桐' },
  { id: 'd10', label: '结构设计', source: 'USER', auditStatus: 'APPROVED', createdByName: '李明轩' },
  { id: 'd11', label: '摄影', source: 'USER', auditStatus: 'REJECTED', createdByName: '陈子昂' }
]

/** 字典登记表：type → 词条数组（SKILL 技能标签；KB_CATEGORY 知识库分类——知识库迁回主站后 DR01 直接可用）
 *  导出供 mock/system.ts 管理侧共享同一份数据 */
export const DICTS: Record<string, DictItem[]> = {
  SKILL: skillItems,
  KB_CATEGORY: [
    { id: 'kbi1', label: 'Python 教程', source: 'PRESET', auditStatus: 'APPROVED' },
    { id: 'kbi2', label: '机械设计规范', source: 'PRESET', auditStatus: 'APPROVED' },
    { id: 'kbi3', label: '三维建模教程', source: 'PRESET', auditStatus: 'APPROVED' },
    { id: 'kbi4', label: 'AI 提示词库', source: 'PRESET', auditStatus: 'APPROVED' },
    { id: 'kbi5', label: '其他', source: 'PRESET', auditStatus: 'APPROVED' }
  ]
}

registerMock({
  method: 'get',
  path: '/api/dicts/:type',
  reply: (ctx) => {
    const items = DICTS[ctx.params.type]
    if (!items) return fail(1004, '字典类型不存在')
    // DR01 口径（系统配置清单 §2）：仅返回启用项（停用项不出现在业务下拉）
    const out: DictItem[] = []
    for (const it of items) {
      if ((it as { status?: string }).status === 'DISABLED') continue
      out.push(it)
    }
    return ok(out)
  }
})

// 本处理器承载两个契约：D02 用户补充（登录即可，USER+PENDING）与 SY06 超管加预置项（带 itemValue/itemLabel，PRESET+APPROVED）
registerMock({
  method: 'post',
  path: '/api/dicts/:type/items',
  reply: (ctx) => {
    const items = DICTS[ctx.params.type]
    if (!items) return fail(1004, '字典类型不存在')
    const me = findMockUserByToken(ctx.token)
    // SY06 超管形状：带 itemValue/itemLabel（系统配置清单 §4）
    const itemValue = String(ctx.body.itemValue ?? '').trim()
    if (itemValue) {
      if (!me || me.role !== 'SUPER_ADMIN') return fail(1003, '仅超管可管理字典')
      const itemLabel = String(ctx.body.itemLabel ?? '').trim()
      if (!itemLabel) return fail(1001, '值与名称不能为空')
      for (const it of items) {
        if (it.label === itemValue || it.label === itemLabel) return fail(1001, '该值已存在')
      }
      const item: DictItem = { id: 'd' + seq++, label: itemLabel, source: 'PRESET', auditStatus: 'APPROVED' }
      items.push(item)
      return ok({ id: item.id })
    }
    // D02 用户补充形状
    const label = String(ctx.body.label ?? '').trim()
    if (!label) return fail(1001, '参数不完整')
    for (const it of items) {
      if (it.label === label) return fail(1001, '该词条已存在')
    }
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
