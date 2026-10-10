/**
 * 赛队演示数据（mock/team.ts 的数据部分，拆出控制单文件行数）
 * 三支队伍：t1 电波突击队（报名中比赛 c1 硬件赛道，含待确认成员王雨桐）、t2 像素工坊、c3 极光智创（已结束比赛）
 */
export interface MockTeamMember {
  memberId: string
  name: string
  role: 'LEADER' | 'MEMBER'
  status: 'UNCONFIRMED' | 'CONFIRMED' | 'REJECTED' | 'EXITED' | 'REMOVED'
  college: string
  className: string
  phone: string
  idCardLast4: string
}

export interface MockTeam {
  id: string
  name: string
  projectName: string
  contestId: string
  contestName: string
  trackId: string
  trackName: string
  leaderId: string
  leaderName: string
  status: 'ACTIVE' | 'DISSOLVED'
  materialStatus: string
  materialCompleteRate: number
  members: MockTeamMember[]
  advisors: { memberId: string; name: string; college: string }[]
  createdAt: string
  /** 演示 4009：有在途材料/成果/待审批时禁止解散 */
  hasBlocking?: boolean
}

export const teams: MockTeam[] = [
  {
    id: 't1', name: '电波突击队', projectName: '智能循迹小车', contestId: 'c1',
    contestName: '2026 全国大学生电子设计竞赛（校内选拔赛）', trackId: 'tr1', trackName: '硬件赛道',
    leaderId: 'm5', leaderName: '李明轩', status: 'ACTIVE', materialStatus: '待补 1 项', materialCompleteRate: 75,
    members: [
      { memberId: 'm5', name: '李明轩', role: 'LEADER', status: 'CONFIRMED', college: '计算机与信息工程学院', className: '软工23-2班', phone: '13900000005', idCardLast4: '3390' },
      { memberId: 'm3', name: '赵天宇', role: 'MEMBER', status: 'CONFIRMED', college: '电子与信息工程学院', className: '电信23-1班', phone: '13900000003', idCardLast4: '1042' },
      { memberId: 'm7', name: '王雨桐', role: 'MEMBER', status: 'UNCONFIRMED', college: '计算机与信息工程学院', className: '软工23-2班', phone: '13900000007', idCardLast4: '8821' }
    ],
    advisors: [{ memberId: 'm1', name: '李明', college: '计算机与信息工程学院' }],
    createdAt: '2026-10-05 19:20:00',
    hasBlocking: true
  },
  {
    id: 't2', name: '像素工坊', projectName: '校园闲置交易小程序', contestId: 'c1',
    contestName: '2026 全国大学生电子设计竞赛（校内选拔赛）', trackId: 'tr2', trackName: '软件赛道',
    leaderId: 'm6', leaderName: '孙嘉仪', status: 'ACTIVE', materialStatus: '齐全', materialCompleteRate: 100,
    members: [
      { memberId: 'm6', name: '孙嘉仪', role: 'LEADER', status: 'CONFIRMED', college: '计算机与信息工程学院', className: '计科23-1班', phone: '13900000006', idCardLast4: '5567' },
      { memberId: 'm4', name: '陈子昂', role: 'MEMBER', status: 'CONFIRMED', college: '计算机与信息工程学院', className: '软工23-2班', phone: '13900000004', idCardLast4: '2218' }
    ],
    advisors: [],
    createdAt: '2026-10-06 10:02:00'
  },
  {
    id: 't3', name: '极光智创', projectName: '宿舍智能门锁', contestId: 'c3',
    contestName: '2026 春季电子设计校赛', trackId: 'tr3', trackName: '硬件赛道',
    leaderId: 'm8', leaderName: '周子墨', status: 'ACTIVE', materialStatus: '齐全', materialCompleteRate: 100,
    members: [
      { memberId: 'm8', name: '周子墨', role: 'LEADER', status: 'CONFIRMED', college: '机械工程学院', className: '机自23-1班', phone: '13900000008', idCardLast4: '6634' }
    ],
    advisors: [{ memberId: 'm2', name: '陈志远', college: '计算机与信息工程学院' }],
    createdAt: '2026-03-12 09:00:00'
  }
]
