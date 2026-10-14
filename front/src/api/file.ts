/**
 * 文件服务接口（骨架文件服务的唯一前端出口：上传返回 fileId + 扫描状态）
 * 对应方案：《前端ABC模块页面开发-优化方案》FS4；规范 §6：写操作携带幂等键
 * 2026-10-14 对齐后端 FileController / 骨架清单 F01：
 *   ① 上传必填 bizType（业务来源，后端用于对象存储目录分层与审计），各业务传大写蛇形标识
 *     （KB_FILE / ACHIEVEMENT_EVIDENCE / MATERIAL_SUBMIT / MEMBER_PHOTO / MEMBER_SIGNATURE /
 *      PERFORMANCE_WEEKLY / PROJECT_FILE）
 *   ② 响应字段名为 originalName（此前前端误写 fileName，联调会拿到 undefined）
 * TODO(联调)：bizId（先传后关联的反向场景）目前无调用方，需要时再加
 */
import { upload } from './http'

export interface UploadResp {
  fileId: string
  /** 病毒检测结果：CLEAN 通过 / INFECTED 命中 / UNAVAILABLE 扫描服务不可用（命中走 1010 错误码通道） */
  scanStatus: 'CLEAN' | 'INFECTED' | 'UNAVAILABLE'
  /** 原始文件名（骨架清单 F01 字段名） */
  originalName: string
}

/** 上传单个文件；idempotencyKey 由调用方（UploadField）在每次选择时生成 */
export function apiUploadFile(file: File, idempotencyKey: string, bizType: string): Promise<UploadResp> {
  return upload('/api/files', file, idempotencyKey, { bizType })
}
