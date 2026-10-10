/**
 * 版块配置（论坛站唯一版块数据源）
 * 对应：架构方案 §5.1 版块由管理员维护；骨架阶段内置四块
 * TODO(联调)：改由论坛服务版块接口下发，前端不硬编码
 */
import type { BoardCode } from '@/types'

export interface Board {
  code: BoardCode
  name: string
  desc: string
}

export const BOARDS: Board[] = [
  { code: 'GENERAL', name: '综合', desc: '公告、招新与跨组讨论' },
  { code: 'EE', name: '电控', desc: '电控组技术讨论' },
  { code: 'MODELING', name: '建模', desc: '建模与结构设计' },
  { code: 'AI', name: 'AI', desc: 'AI 组技术讨论' }
]

export function boardName(code: BoardCode): string {
  const b = BOARDS.find((x) => x.code === code)
  return b ? b.name : '综合'
}
