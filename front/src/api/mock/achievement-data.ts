/**
 * 成果演示数据（mock/achievement.ts 的数据部分）
 * a1/a2 已通过（历史赛季，佐证已传）；a3 待审批（与审批种子 ap-demo3 对应，演示审批流与 §5 联动）
 */
import type { AchievementStatus, AchievementType } from '@/api/achievement'

export interface MockAchievement {
  id: string
  teamId: string
  type: AchievementType
  name: string
  level: string
  achievedAt: string
  description: string
  status: AchievementStatus
  submittedBy: string
  evidenceFileId: string
  evidenceFileName: string
  createdAt: string
}

let seq = 10
export function nextAchId(): string {
  seq += 1
  return 'a' + seq
}

export const achievements: MockAchievement[] = [
  {
    id: 'a1', teamId: 't2', type: 'AWARD', name: '校园闲置交易小程序', level: '省级一等奖', achievedAt: '2026-05-20',
    description: '第十五届全国大学生计算机设计大赛黑龙江省赛区一等奖，作品为校园闲置物品交易平台。',
    status: 'APPROVED', submittedBy: '孙嘉仪', evidenceFileId: 'f-mock-201', evidenceFileName: '省级一等奖证书.pdf', createdAt: '2026-05-21 10:00:00'
  },
  {
    id: 'a2', teamId: 't3', type: 'AWARD', name: '宿舍智能门锁', level: '校级二等奖', achievedAt: '2026-04-15',
    description: '2026 春季电子设计校赛二等奖，基于 RFID 与密码双认证的宿舍门锁系统。',
    status: 'APPROVED', submittedBy: '周子墨', evidenceFileId: 'f-mock-202', evidenceFileName: '校级二等奖证书.pdf', createdAt: '2026-04-16 09:00:00'
  },
  {
    id: 'a3', teamId: 't1', type: 'RATING', name: '智能循迹小车', level: '校级优秀', achievedAt: '2026-10-08',
    description: '校内电子设计训练评级作品，全地形循迹与速度自适应控制。',
    status: 'PENDING', submittedBy: '李明轩', evidenceFileId: '', evidenceFileName: '', createdAt: '2026-10-08 19:30:00'
  }
]
