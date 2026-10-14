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
    // bizType 为后端 FileController 必填（业务来源），缺参对齐 1001
    const bizType = fd && typeof fd.get === 'function' ? String(fd.get('bizType') ?? '') : ''
    if (!bizType) return fail(1001, '参数校验失败：缺少必填参数 bizType')
    if (name.toLowerCase().indexOf('virus') >= 0) return fail(1010, '文件安全扫描未通过，请更换文件')
    return ok({ fileId: 'f' + seq++, originalName: name, scanStatus: 'CLEAN' })
  }
})

/** 文件下载（blob 请求由 mock 适配器统一包成演示文件流，真实文件名由前端兜底传入） */
registerMock({
  method: 'get',
  path: '/api/files/:fileId/download',
  reply: (ctx) => ok({ fileId: ctx.params.fileId, note: 'mock 演示文件内容' })
})
