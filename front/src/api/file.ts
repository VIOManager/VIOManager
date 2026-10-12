/**
 * 文件服务接口（骨架文件服务的前端出口：上传返回 fileId + 扫描状态）
 * 对应方案：《前端ABC模块页面开发-优化方案》FS4；规范 §6：写操作携带幂等键
 * ⚠️ 清单：组长骨架文件服务接口清单未录入 Apifox，路径与字段为暂定稿，以 Apifox 为准
 */
import { upload } from './http'

export interface UploadResp {
  fileId: string
  fileName: string
  /** 安全扫描状态：CLEAN 通过 / REJECTED 拒绝（1010 走错误码通道，不进响应体） */
  scanStatus: 'CLEAN' | 'REJECTED'
}

/** 上传单个文件；idempotencyKey 由调用方（UploadField）在每次选择时生成 */
export function apiUploadFile(file: File, idempotencyKey: string): Promise<UploadResp> {
  return upload('/api/files', file, idempotencyKey)
}
