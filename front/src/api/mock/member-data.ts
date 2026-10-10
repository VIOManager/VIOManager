/**
 * 人员档案演示数据（mock/member.ts 的数据部分，拆出控制单文件行数）
 * 账号互通：admin↔李明 / dept01↔陈志远 / 2023319024↔王雨桐；李明轩为队长（演示 3003）
 */
import type { MemberListItem } from '@/api/member'

export interface MockMember extends MemberListItem {
  gender: string
  nation: string
  email: string
  idCard: string
  photoFileId: string
  signatureFileId: string
  createdAt: string
  /** 演示 3003：队长身份禁止删除 */
  isLeader?: boolean
  /** 绑定的登录账号（hasAccount=true 时有效） */
  loginName?: string
}

export const members: MockMember[] = [
  { id: 'm1', name: '李明', studentNo: 'T1001', college: '计算机与信息工程学院', major: '软件工程', className: '', phone: '13800000001', memberType: 'TEACHER', orgId: 'o1', orgName: '科创团队本部', hasAccount: true, idCardLast4: '0031', skills: ['python'], gender: '男', nation: '汉族', email: 'liming@usth.edu.cn', idCard: '230103198501010031', photoFileId: '', signatureFileId: '', createdAt: '2026-09-01 09:00:00', loginName: 'admin' },
  { id: 'm2', name: '陈志远', studentNo: 'T1002', college: '计算机与信息工程学院', major: '计算机科学与技术', className: '', phone: '13800000002', memberType: 'TEACHER', orgId: 'o11', orgName: '数媒协会', hasAccount: true, idCardLast4: '0217', skills: ['python', '视频剪辑'], gender: '男', nation: '汉族', email: 'chenzy@usth.edu.cn', idCard: '230103198802140217', photoFileId: '', signatureFileId: '', createdAt: '2026-09-01 09:00:00', loginName: 'dept01' },
  { id: 'm3', name: '赵天宇', studentNo: '2023319001', college: '电子与信息工程学院', major: '电子信息工程', className: '电信23-1班', phone: '13900000003', memberType: 'STUDENT', orgId: 'o12', orgName: '电控工作室', hasAccount: false, idCardLast4: '1042', skills: ['CAD', '机械设计'], gender: '男', nation: '汉族', email: 'zhaoty@stu.usth.edu.cn', idCard: '230106200503141042', photoFileId: '', signatureFileId: '', createdAt: '2026-09-02 10:00:00' },
  { id: 'm4', name: '陈子昂', studentNo: '2023319002', college: '计算机与信息工程学院', major: '软件工程', className: '软工23-2班', phone: '13900000004', memberType: 'STUDENT', orgId: 'o13', orgName: '建模组', hasAccount: false, idCardLast4: '2218', skills: ['python', 'AIGC'], gender: '男', nation: '汉族', email: 'chenza@stu.usth.edu.cn', idCard: '230106200505122218', photoFileId: '', signatureFileId: '', createdAt: '2026-09-02 10:00:00' },
  { id: 'm5', name: '李明轩', studentNo: '2023319003', college: '计算机与信息工程学院', major: '软件工程', className: '软工23-2班', phone: '13900000005', memberType: 'STUDENT', orgId: 'o11', orgName: '数媒协会', hasAccount: true, idCardLast4: '3390', skills: ['视频剪辑'], gender: '男', nation: '汉族', email: 'limx@stu.usth.edu.cn', idCard: '230106200508233390', photoFileId: '', signatureFileId: '', createdAt: '2026-09-03 14:00:00', isLeader: true, loginName: '2023319003' },
  { id: 'm6', name: '孙嘉仪', studentNo: '2023319004', college: '计算机与信息工程学院', major: '计算机科学与技术', className: '计科23-1班', phone: '13900000006', memberType: 'STUDENT', orgId: 'o111', orgName: '数媒二楼', hasAccount: false, idCardLast4: '5567', skills: ['AIGC'], gender: '女', nation: '汉族', email: 'sunjy@stu.usth.edu.cn', idCard: '230106200511085567', photoFileId: '', signatureFileId: '', createdAt: '2026-09-03 14:00:00' },
  { id: 'm7', name: '王雨桐', studentNo: '2023319024', college: '计算机与信息工程学院', major: '软件工程', className: '软工23-2班', phone: '13900000007', memberType: 'STUDENT', orgId: 'o11', orgName: '数媒协会', hasAccount: true, idCardLast4: '8821', skills: ['python'], gender: '女', nation: '满族', email: 'wangyt@stu.usth.edu.cn', idCard: '230106200603098821', photoFileId: '', signatureFileId: '', createdAt: '2026-09-05 16:00:00', loginName: '2023319024' },
  { id: 'm8', name: '周子墨', studentNo: '2023319005', college: '机械工程学院', major: '机械设计制造及其自动化', className: '机自23-1班', phone: '13900000008', memberType: 'STUDENT', orgId: 'o111', orgName: '数媒二楼', hasAccount: false, idCardLast4: '6634', skills: ['CAD'], gender: '男', nation: '汉族', email: 'zhouzm@stu.usth.edu.cn', idCard: '230106200507196634', photoFileId: '', signatureFileId: '', createdAt: '2026-09-05 16:00:00' }
]

