/**
 * 论坛楼层内存种子数据（mock 层，与 forum-data.ts 的 replyCount 一一对应）
 * 楼层 quote 用 quoteReplyId 指向被引楼层（渲染时再拼"某人 #N"），不存冗余文本
 */
export interface MockReply {
  id: string
  topicId: string
  /** 楼层号：帖内从 1 连续自增（新回帖 = 当前最大楼 +1） */
  floorNo: number
  author: string
  authorGroup: string
  /** 展示字符串（如 '10-07 20:41'） */
  publishAt: string
  text: string
  likeCount: number
  liked: boolean
  /** 引用回复：指向本帖被引楼层 id；普通回复为 null */
  quoteReplyId: string | null
  /** V4 二级评论：指向本帖一级楼层 id；一级为 null/缺省（两级封顶） */
  parentReplyId?: string | null
  /** NORMAL / HIDDEN（F14 管理端软删；缺省 NORMAL。HIDDEN 学生侧显示占位文本） */
  status?: string
  /** 管理员编辑标记（F13 覆盖原文后置 true 透出） */
  edited?: boolean
}

/** 全部楼层（按帖分组有序，floorNo 连续） */
export const replies: MockReply[] = [
  { id: '331-1', topicId: '331', floorNo: 1, author: '陈子昂', authorGroup: '电控组', publishAt: '10-07 20:41', text: '上一期听完把自家车的速度环整定时间从两天压到两小时，这期必到。问下：电流环会讲到 FOC 吗？', likeCount: 12, liked: false, quoteReplyId: null },
  { id: '331-2', topicId: '331', floorNo: 2, author: '赵天宇', authorGroup: '电控组 · 版主', publishAt: '10-07 20:58', text: '这期只到电流环概念和波形，FOC 放第四期单独讲，先把 PI 整定的手感练出来。', likeCount: 8, liked: false, quoteReplyId: '331-1' },
  { id: '331-3', topicId: '331', floorNo: 3, author: '李明轩', authorGroup: '建模组', publishAt: '10-07 21:32', text: '机械组的能来旁听吗？我们底盘那边也想蹭一下。', likeCount: 3, liked: false, quoteReplyId: null },
  { id: '331-4', topicId: '331', floorNo: 4, author: '孙嘉仪', authorGroup: '电控组', publishAt: '10-07 22:10', text: '可以，版主说过不限组。座位有限，早点来。', likeCount: 5, liked: false, quoteReplyId: '331-3' },
  { id: '331-5', topicId: '331', floorNo: 5, author: '周子墨', authorGroup: '综合', publishAt: '10-08 09:15', text: '登记一下：培训室投影仪已检修，HDMI 线换了新的。周五直接用。', likeCount: 4, liked: false, quoteReplyId: null },
  { id: '331-6', topicId: '331', floorNo: 6, author: '李明轩', authorGroup: '建模组', publishAt: '10-08 12:02', text: '接龙 +1，带两个机械组的同学。', likeCount: 2, liked: false, quoteReplyId: null },

  { id: '330-1', topicId: '330', floorNo: 1, author: '王雨桐', authorGroup: 'AI 组', publishAt: '10-06 11:20', text: '评分标准里的"软件基础"建议写明考什么软件，去年有人以为考 Office。', likeCount: 6, liked: false, quoteReplyId: null },
  { id: '330-2', topicId: '330', floorNo: 2, author: '陈子昂', authorGroup: '建模组', publishAt: '10-06 11:35', text: '已补充：SolidWorks 任一版本的基础操作，出题范围是草绘与装配。', likeCount: 9, liked: false, quoteReplyId: '330-1' },
  { id: '330-3', topicId: '330', floorNo: 3, author: '赵天宇', authorGroup: '电控组 · 版主', publishAt: '10-06 14:00', text: '电控组参照这份格式出了自己的面试题，晚点也公示。', likeCount: 3, liked: false, quoteReplyId: null },

  { id: '329-1', topicId: '329', floorNo: 1, author: '孙嘉仪', authorGroup: '电控组', publishAt: '10-05 22:30', text: '去耦电容那条"先大后小、近远搭配"太好用了，上次按这个改完纹波直接减半。', likeCount: 7, liked: false, quoteReplyId: null },
  { id: '329-2', topicId: '329', floorNo: 2, author: '陈子昂', authorGroup: '电控组', publishAt: '10-06 08:50', text: '补充一条：电源层分割后的跨分割走线也检查下，比过孔残桩更隐蔽。', likeCount: 5, liked: false, quoteReplyId: null },

  { id: '328-1', topicId: '328', floorNo: 1, author: '赵天宇', authorGroup: '电控组 · 版主', publishAt: '10-05 16:40', text: '跑动才丢、静止不复现，优先查接插件振动松脱和屏蔽层接地，示波器挂在 CAN_H 对地看共模噪声。', likeCount: 10, liked: false, quoteReplyId: null },
  { id: '328-2', topicId: '328', floorNo: 2, author: '李明轩', authorGroup: '建模组', publishAt: '10-05 17:05', text: '接插件重新压接了端子，问题还在。共模噪声明天借示波器量。', likeCount: 2, liked: false, quoteReplyId: '328-1' },
  { id: '328-3', topicId: '328', floorNo: 3, author: '赵天宇', authorGroup: '电控组 · 版主', publishAt: '10-06 09:30', text: '再加一条：确认电机端和主控端两处终端电阻是 120Ω 并联成 60Ω，只测单端会误判。', likeCount: 8, liked: false, quoteReplyId: null },

  { id: '327-1', topicId: '327', floorNo: 1, author: '陈子昂', authorGroup: '电控组', publishAt: '10-04 22:40', text: '缓冲能量留 20% 这个结论和我们底盘实测一致，去年就是全当库用，最后 30 秒被限速打得抬不起头。', likeCount: 11, liked: false, quoteReplyId: null },
  { id: '327-2', topicId: '327', floorNo: 2, author: '赵天宇', authorGroup: '电控组 · 版主', publishAt: '10-05 10:00', text: '把三档限速的切换阈值也整理进复盘附录了，需要的回帖留邮箱。', likeCount: 6, liked: false, quoteReplyId: null },

  { id: '326-1', topicId: '326', floorNo: 1, author: '赵天宇', authorGroup: '电控组 · 版主', publishAt: '10-04 13:00', text: '问下摘要的输入是整份 PDF 还是逐段喂？我们想把裁判系统规则手册也这么处理。', likeCount: 3, liked: false, quoteReplyId: null },
  { id: '326-2', topicId: '326', floorNo: 2, author: '王雨桐', authorGroup: 'AI 组', publishAt: '10-04 14:10', text: '整份喂就行，超过上下文再逐段。规则手册那种带附录的，先转纯文本能省一半 token。', likeCount: 5, liked: false, quoteReplyId: '326-1' },

  { id: '325-1', topicId: '325', floorNo: 1, author: '赵天宇', authorGroup: '电控组 · 版主', publishAt: '10-03 11:00', text: '登记本建议顺手续一根 BNC 线，上回找半天。', likeCount: 4, liked: false, quoteReplyId: null },
  { id: '325-2', topicId: '325', floorNo: 2, author: '李明轩', authorGroup: '建模组', publishAt: '10-03 15:20', text: '已登记使用两次，探头都归位了，机器状态良好。', likeCount: 2, liked: false, quoteReplyId: null },

  { id: '324-1', topicId: '324', floorNo: 1, author: '周子墨', authorGroup: '综合', publishAt: '10-02 20:30', text: '试了一周，风险那段提醒得比我自己写的全，模板好用。', likeCount: 4, liked: false, quoteReplyId: null },
  { id: '324-2', topicId: '324', floorNo: 2, author: '王雨桐', authorGroup: 'AI 组', publishAt: '10-02 21:00', text: '附件在主楼置顶回复里，自取。下版本加"本周排班冲突"检查。', likeCount: 3, liked: false, quoteReplyId: '324-1' },

  { id: '323-1', topicId: '323', floorNo: 1, author: '陈子昂', authorGroup: '建模组', publishAt: '10-02 15:40', text: 'PETG 换着试，壁厚加到 4；还不行就把受力方向转 45 度摆放打印。断在层间多半是方向问题。', likeCount: 8, liked: false, quoteReplyId: null },
  { id: '323-2', topicId: '323', floorNo: 2, author: '李明轩', authorGroup: '建模组', publishAt: '10-02 16:10', text: 'PETG 拉了两根测试件，层间确实好很多，感谢。', likeCount: 3, liked: false, quoteReplyId: '323-1' },

  { id: '322-1', topicId: '322', floorNo: 1, author: '王雨桐', authorGroup: 'AI 组', publishAt: '10-01 19:40', text: '票据扫描件第三张有点糊，能重拍一下吗。', likeCount: 1, liked: false, quoteReplyId: null },

  { id: '321-1', topicId: '321', floorNo: 1, author: '赵天宇', authorGroup: '电控组 · 版主', publishAt: '10-01 15:00', text: 'C12 那个坑我踩过一模一样的，补一句：先量基准电压再焊驱动 IC，能少拆一次。', likeCount: 6, liked: false, quoteReplyId: null },

  { id: '320-1', topicId: '320', floorNo: 1, author: '李明轩', authorGroup: '建模组', publishAt: '09-30 10:30', text: '模板装上了，旧图纸的标题栏能批量替换吗？', likeCount: 2, liked: false, quoteReplyId: null }
]

/** 某帖全部楼层（按楼层号正序） */
export function repliesOf(topicId: string): MockReply[] {
  const list: MockReply[] = []
  for (let i = 0; i < replies.length; i++) {
    if (replies[i].topicId === topicId) list.push(replies[i])
  }
  return list
}

/** 按 id 找楼层 */
export function findReply(id: string): MockReply | null {
  for (let i = 0; i < replies.length; i++) {
    if (replies[i].id === id) return replies[i]
  }
  return null
}

/** 新楼层 id 规则：帖id-楼层号（如 331-7） */
export function allocReplyId(topicId: string, floorNo: number): string {
  return topicId + '-' + floorNo
}
