/**
 * 文件服务 mock（骨架 Step 8 文件服务的前端替身）
 * ⚠️ 演示规则：文件名含 virus → 1010 安全扫描拒绝；缺 X-Idempotency-Key → 1001（规范 §6 幂等强制）
 * TODO(联调)：文件服务就绪后删除本文件
 */
import { fail, ok, registerMock } from './registry'

let seq = 1

registerMock({
  method: 'post',
  path: '/api/files',
  reply: (ctx) => {
    if (!ctx.idemKey) return fail(1001, '缺少幂等键 X-Idempotency-Key')
    // 上传的 FormData 由适配器原样透传，文件在 file 字段
    const fd = ctx.body as unknown as FormData
    const file = fd && typeof fd.get === 'function' ? (fd.get('file') as File | null) : null
    const name = file ? file.name : 'unknown.bin'
    if (name.toLowerCase().indexOf('virus') >= 0) return fail(1010, '文件安全扫描未通过，请更换文件')
    return ok({ fileId: 'f' + seq++, fileName: name, scanStatus: 'CLEAN' })
  }
})
