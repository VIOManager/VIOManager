/**
 * 赛事演示数据（mock/contest.ts 的数据部分，拆出控制单文件行数）
 * 模板 2 个 + 比赛 3 个（含赛道覆盖示例）+ 待处理池 2 条
 */
import type { ContestTemplate, PoolItem } from '@/api/contest'
import type { ContestStatus, RulesMap } from '@/api/contest'

export interface MockTrack {
  id: string
  name: string
  code: string
  description: string
  overrideRules: RulesMap
  teamCount: number
}

export interface MockContest {
  id: string
  name: string
  semesterId: string
  semesterName: string
  templateId: string
  templateName: string
  status: ContestStatus
  signupStart: string
  signupEnd: string
  contestStart: string
  contestEnd: string
  contact: string
  description: string
  overrideRules: RulesMap
  tracks: MockTrack[]
  teamCount: number
}

export const templates: ContestTemplate[] = [
  {
    id: 'tp1', name: '全国大学生电子设计竞赛', code: 'EDC', category: '教育部A类',
    description: '四天三夜硬件综合赛，校内选拔走本模板',
    defaultRules: { memberLimit: 3, prefillLimit: 2, allowRepeatJoin: false, crossTeamLeader: false, maxAdvisorCount: 2, crossTrackLimit: true },
    enabled: true, refCount: 2
  },
  {
    id: 'tp2', name: '蓝桥杯全国软件和信息技术专业人才大赛', code: 'LANQIAO', category: '信息类',
    description: '个人赛为主，团体赛道另行配置',
    defaultRules: { memberLimit: 1, prefillLimit: 0, allowRepeatJoin: true, crossTeamLeader: false, maxAdvisorCount: 1, crossTrackLimit: false },
    enabled: true, refCount: 0
  }
]

export const contests: MockContest[] = [
  {
    id: 'c1', name: '2026 全国大学生电子设计竞赛（校内选拔赛）', semesterId: 's2', semesterName: '2026秋季学期',
    templateId: 'tp1', templateName: '全国大学生电子设计竞赛', status: 'REGISTERING',
    signupStart: '2026-10-01', signupEnd: '2026-11-15', contestStart: '2026-12-01', contestEnd: '2026-12-04',
    contact: '陈志远 13800000002', description: '校内选拔赛，优秀队伍推荐省赛。',
    overrideRules: { memberLimit: 3 },
    tracks: [
      { id: 'tr1', name: '硬件赛道', code: 'HW', description: '电路设计制作', overrideRules: {}, teamCount: 8 },
      { id: 'tr2', name: '软件赛道', code: 'SW', description: '嵌入式软件与上位机', overrideRules: { memberLimit: 2 }, teamCount: 4 }
    ],
    teamCount: 12
  },
  {
    id: 'c2', name: '2026 蓝桥杯校内赛', semesterId: 's2', semesterName: '2026秋季学期',
    templateId: 'tp2', templateName: '蓝桥杯全国软件和信息技术专业人才大赛', status: 'DRAFT',
    signupStart: '', signupEnd: '', contestStart: '', contestEnd: '',
    contact: '', description: '草稿：信息未补全，用于演示发布前检查。',
    overrideRules: {}, tracks: [], teamCount: 0
  },
  {
    id: 'c3', name: '2026 春季电子设计校赛', semesterId: 's1', semesterName: '2026春季学期',
    templateId: 'tp1', templateName: '全国大学生电子设计竞赛', status: 'FINISHED',
    signupStart: '2026-03-10', signupEnd: '2026-04-01', contestStart: '2026-04-15', contestEnd: '2026-04-18',
    contact: '李明 13800000001', description: '上学期已完赛。',
    overrideRules: {}, tracks: [
      { id: 'tr3', name: '硬件赛道', code: 'HW', description: '', overrideRules: {}, teamCount: 10 }
    ],
    teamCount: 10
  }
]

export const poolItems: PoolItem[] = [
  {
    id: 'p1', title: '关于举办 2027 年 RoboMaster 机甲大师校内选拔的通知', source: '教务处 RSS',
    fetchedAt: '2026-10-08 09:12:00',
    content: '各学院：现启动 2027 年 RoboMaster 校内选拔报名，预计 2026 年 11 月报名，每队 3-5 人……',
    clues: { signupTime: '2026-11', memberLimit: 5, organizer: '教务处' },
    status: 'PENDING'
  },
  {
    id: 'p2', title: '2026 年“挑战杯”课外学术科技作品竞赛预通知', source: '手工登记',
    fetchedAt: '2026-10-06 15:40:00',
    content: '拟于下学期启动校赛，作品要求详见附件……',
    clues: { organizer: '校团委' },
    status: 'PENDING'
  }
]
