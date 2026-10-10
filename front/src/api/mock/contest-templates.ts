/**
 * 比赛模板 mock（B 线 T01–T05）
 * ⚠️ 校验：4012 模板被比赛引用拒删；默认规则变更不影响已创建比赛（快照语义由后端保证）
 * TODO(联调)：后端模板接口就绪后删除本文件
 */
import { fail, ok, registerMock } from './registry'
import { templates } from './contest-data'
import type { RulesMap } from '@/api/contest'

let seq = 10

// ── 模板 T01–T05 ──
registerMock({ method: 'get', path: '/api/contest-templates', reply: () => ok(templates) })

registerMock({
  method: 'post',
  path: '/api/contest-templates',
  reply: (ctx) => {
    const name = String(ctx.body.name ?? '').trim()
    if (!name) return fail(1001, '模板名称不能为空')
    const t = {
      id: 'tp' + seq++, name, code: String(ctx.body.code ?? ''), category: String(ctx.body.category ?? ''),
      description: String(ctx.body.description ?? ''),
      defaultRules: (ctx.body.defaultRules as RulesMap) ?? {},
      enabled: true, refCount: 0
    }
    templates.push(t)
    return ok(t)
  }
})

registerMock({
  method: 'put',
  path: '/api/contest-templates/:id',
  reply: (ctx) => {
    for (const t of templates) {
      if (t.id !== ctx.params.id) continue
      t.name = String(ctx.body.name ?? t.name)
      t.code = String(ctx.body.code ?? t.code)
      t.category = String(ctx.body.category ?? t.category)
      t.description = String(ctx.body.description ?? t.description)
      t.defaultRules = (ctx.body.defaultRules as RulesMap) ?? t.defaultRules
      return ok(t)
    }
    return fail(1004, '模板不存在')
  }
})

registerMock({
  method: 'delete',
  path: '/api/contest-templates/:id',
  reply: (ctx) => {
    for (const t of templates) {
      if (t.id !== ctx.params.id) continue
      if (t.refCount > 0) return fail(4012, '模板已被比赛引用，禁止删除')
      templates.splice(templates.indexOf(t), 1)
      return ok(null)
    }
    return fail(1004, '模板不存在')
  }
})

