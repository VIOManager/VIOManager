/**
 * 论坛帖子内存种子数据（mock 层）
 * 来源：HomePage/TopicPage 骨架阶段的静态演示数据整体迁入，字段含义不变
 * ⚠️ 内存态：刷新页面恢复种子（与主站 mock 同口径，演示同标签页内进行）
 * TODO(联调-论坛清单)：论坛服务接口清单尚未落盘，字段按页面展示需要预设，
 *    清单出稿后以清单为准调整——错误码段、路径前缀 /api/forum 均待登记
 */
import type { BoardCode } from '@/types'

export interface MockAttach {
  name: string
  size: string
  downloads: number
}

export interface MockTopic {
  id: string
  board: BoardCode
  title: string
  author: string
  authorGroup: string
  tags: string[]
  /** 回帖数（与 forum-replies 楼层条数保持一致，回帖时同步 +1） */
  replyCount: number
  viewCount: number
  likeCount: number
  /** 当前登录人点赞态（演示用；真实服务按登录人判重） */
  liked: boolean
  /** 当前登录人收藏态（同上） */
  collected: boolean
  publishAt: string
  /** 最后活跃时间（展示字符串，如"3 小时前"；真实接口应为 ISO 时间） */
  lastActiveAt: string
  lastReplyBy: string
  pinned: boolean
  hot: boolean
  /** 正文段落（发帖表单按空行切段） */
  body: string[]
  attach: MockAttach[]
}

/** 当前时间展示串（新发帖/新回帖用），格式对齐种子数据的 '10-13 16:05' */
export function nowText(): string {
  const d = new Date()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return mm + '-' + dd + ' ' + hh + ':' + mi
}

/** 帖子主贴（含演示楼层 6 条的培训帖 331，其余为列表种子） */
export const topics: MockTopic[] = [
  {
    id: '331', board: 'EE', title: '赛季电控培训第三期：STM32 HAL 库电机闭环控制速成',
    author: '赵天宇', authorGroup: '电控组 · 版主', tags: ['STM32', '电机控制', '培训'],
    replyCount: 6, viewCount: 1200, likeCount: 58, liked: true, collected: false,
    publishAt: '10-07 20:15', lastActiveAt: '3 小时前', lastReplyBy: '李明轩', pinned: true, hot: true,
    body: [
      '本周五晚 19:30 电控工作室开第三期培训，目标是把电机闭环从"听说过"讲到"能上手"。零基础可来，带电脑。',
      '大纲：编码器采样与定时器输入捕获 → 速度环 PID 现场整定 → 电流环与 M3508 实测波形 → 常见坑（采样噪声、积分饱和、看门狗复位）。',
      '报名在群里接龙，截止本周五 12:00。录屏和例程仓库会后更新到本帖附件。'
    ],
    attach: [
      { name: '培训录屏-第三期.mp4', size: '386 MB', downloads: 45 },
      { name: 'motor-pid-demo.zip', size: '2.1 MB', downloads: 63 }
    ]
  },
  {
    id: '330', board: 'MODELING', title: '建模组十月招新面试题与评分标准公示',
    author: '陈子昂', authorGroup: '建模组', tags: ['招新', '公示'],
    replyCount: 4, viewCount: 986, likeCount: 47, liked: false, collected: false,
    publishAt: '10-06 10:00', lastActiveAt: '1 小时前', lastReplyBy: '演示成员', pinned: false, hot: true,
    body: [
      '十月招新面试题与评分标准现予公示，面试时间 10 月 18 日全天，地点机械工作室。',
      '评分维度：空间想象 40 分、软件基础 30 分、动手习惯 30 分。面试题库与评分细则见附件链接，欢迎监督。'
    ],
    attach: []
  },
  {
    id: '329', board: 'EE', title: 'PCB 四层板电源完整性 Layout 检查清单分享',
    author: '陈子昂', authorGroup: '电控组', tags: ['PCB', '电源完整性'],
    replyCount: 2, viewCount: 643, likeCount: 31, liked: false, collected: false,
    publishAt: '10-05 21:40', lastActiveAt: '7 小时前', lastReplyBy: '孙嘉仪', pinned: false, hot: true,
    body: [
      '把四层板叠层、去耦电容摆放、过孔残桩三块各整理成一张检查清单，画完板子对着过一遍，翻车率明显下降。',
      '清单是内部整理版，有补充的直接回帖，下版本更新进知识库。'
    ],
    attach: []
  },
  {
    id: '328', board: 'EE', title: '请教：CAN 总线偶发丢帧，终端电阻已经核对过',
    author: '李明轩', authorGroup: '建模组', tags: ['CAN', '排障'],
    replyCount: 3, viewCount: 402, likeCount: 14, liked: false, collected: false,
    publishAt: '10-05 16:20', lastActiveAt: '昨天 15:47', lastReplyBy: '赵天宇', pinned: false, hot: false,
    body: [
      '现象：底盘跑动中 CAN 偶发丢一帧电机反馈，频率大约几分钟一次，静止时不复现。',
      '终端电阻 120Ω 已核对、波特率一致、线束远离动力线。求排查思路，有示波器可用。'
    ],
    attach: []
  },
  {
    id: '327', board: 'EE', title: 'RoboMaster 电控复盘：步兵机器人的功率限制策略',
    author: '孙嘉仪', authorGroup: '电控组', tags: ['RoboMaster', '功率控制'],
    replyCount: 2, viewCount: 866, likeCount: 42, liked: true, collected: true,
    publishAt: '10-04 22:10', lastActiveAt: '昨天 21:30', lastReplyBy: '陈子昂', pinned: false, hot: true,
    body: [
      '赛季结束把步兵的功率限制策略复盘了一遍：缓冲能量模型、三档限速、裁判系统超功率惩罚规避。',
      '结论是缓冲能量别当库用，留 20% 给突发加速，其余按底盘速度动态分配。'
    ],
    attach: []
  },
  {
    id: '326', board: 'AI', title: 'AI 组：本地部署 Qwen 做赛题摘要的踩坑记录',
    author: '王雨桐', authorGroup: 'AI 组', tags: ['Qwen', '本地部署'],
    replyCount: 2, viewCount: 594, likeCount: 36, liked: false, collected: false,
    publishAt: '10-04 11:00', lastActiveAt: '2 天前', lastReplyBy: '赵天宇', pinned: false, hot: false,
    body: [
      '在工作室的 4090 主机上部署 Qwen 做历年赛题摘要，记录显存分配、上下文长度、批处理的坑。',
      '最有价值的一条：赛题 PDF 先转纯文本再摘要，直接喂 PDF 会把规则附录截断。'
    ],
    attach: []
  },
  {
    id: '325', board: 'GENERAL', title: '工作室新到三台示波器，使用登记和注意事项',
    author: '周子墨', authorGroup: '综合', tags: ['设备', '登记'],
    replyCount: 2, viewCount: 310, likeCount: 11, liked: false, collected: false,
    publishAt: '10-03 09:30', lastActiveAt: '2 天前', lastReplyBy: '李明轩', pinned: false, hot: false,
    body: [
      '新到三台示波器已上架，使用前在登记本签名，探头归位再走。',
      '注意事项：禁止用探头直接量动力电池，量程先确认再接。'
    ],
    attach: []
  },
  {
    id: '324', board: 'AI', title: 'GLM 写周报的提示词模板（内测一周，反馈不错）',
    author: '王雨桐', authorGroup: 'AI 组', tags: ['GLM', '提示词'],
    replyCount: 2, viewCount: 275, likeCount: 19, liked: false, collected: true,
    publishAt: '10-02 20:00', lastActiveAt: '3 天前', lastReplyBy: '周子墨', pinned: false, hot: false,
    body: [
      '把周报生成做成了提示词模板：喂本周提交记录和排班备注，产出分工作/学习/风险三段的结构化周报。',
      '模板放二楼附件，用着不顺手的直接回帖，我迭代。'
    ],
    attach: []
  },
  {
    id: '323', board: 'MODELING', title: '底盘悬挂的打印件又断了，求耐打的材料方案',
    author: '李明轩', authorGroup: '建模组', tags: ['3D打印', '材料'],
    replyCount: 2, viewCount: 342, likeCount: 9, liked: false, collected: false,
    publishAt: '10-02 15:10', lastActiveAt: '3 天前', lastReplyBy: '陈子昂', pinned: false, hot: false,
    body: [
      'PLA 打印的悬挂臂在测试里断了两根，层间开裂明显。求耐冲击的材料和打印参数方案。',
      '预算有限，PA-CF 就先不冲了。'
    ],
    attach: []
  },
  {
    id: '322', board: 'GENERAL', title: '十月经费公示与耗材采买清单（已执行）',
    author: '周子墨', authorGroup: '综合', tags: ['公示', '经费'],
    replyCount: 1, viewCount: 198, likeCount: 7, liked: false, collected: false,
    publishAt: '10-01 19:00', lastActiveAt: '4 天前', lastReplyBy: '王雨桐', pinned: false, hot: false,
    body: [
      '九月下旬至十月上旬的耗材采买已执行完毕，明细与票据扫描件公示，有疑问本周内提出。',
      '大头是电控的 MOS 管与接插件补货，其次是建模的打印耗材。'
    ],
    attach: []
  },
  {
    id: '321', board: 'EE', title: '电机驱动板 V3 打样回来了，焊接顺序踩坑提醒',
    author: '孙嘉仪', authorGroup: '电控组', tags: ['驱动板', '焊接'],
    replyCount: 1, viewCount: 260, likeCount: 13, liked: false, collected: false,
    publishAt: '10-01 14:20', lastActiveAt: '5 天前', lastReplyBy: '赵天宇', pinned: false, hot: false,
    body: [
      'V3 驱动板到货，先焊电源部分并空载测压，再焊驱动 IC，最后上 MOS。',
      '提醒：基准电压脚旁边那个 0402 的 C12 别漏焊，漏了上电直接过流，别问我怎么知道的。'
    ],
    attach: []
  },
  {
    id: '320', board: 'MODELING', title: 'SolidWorks 工程图模板统一（附图框与标题栏）',
    author: '陈子昂', authorGroup: '建模组', tags: ['CAD', '规范'],
    replyCount: 1, viewCount: 187, likeCount: 8, liked: false, collected: false,
    publishAt: '09-30 10:00', lastActiveAt: '6 天前', lastReplyBy: '李明轩', pinned: false, hot: false,
    body: [
      '工程图模板统一为 a3 图框 + 修改版标题栏，今后出图都用这套，旧图纸不强制迁移。',
      '模板在附件，解压放到模板目录即可。'
    ],
    attach: [{ name: 'sw-template-a3.zip', size: '480 KB', downloads: 21 }]
  },
  {
    id: '319', board: 'AI', title: '招新宣讲的 AI 演示环节：现场跑一个目标检测',
    author: '王雨桐', authorGroup: 'AI 组', tags: ['招新', '演示'],
    replyCount: 0, viewCount: 156, likeCount: 5, liked: false, collected: false,
    publishAt: '09-29 18:30', lastActiveAt: '一周前', lastReplyBy: '王雨桐', pinned: false, hot: false,
    body: [
      '招新宣讲想现场跑一个目标检测演示，摄像头拍观众席实时框人脸/手机，模型用轻量的即可。',
      '求一个断网也能跑的方案，教室网络不能指望。'
    ],
    attach: []
  },
  {
    id: '332', board: 'GENERAL', title: '新人报到：数媒方向，想找视觉类的项目跟组',
    author: '演示成员', authorGroup: '综合', tags: ['新人', '数媒'],
    replyCount: 0, viewCount: 23, likeCount: 2, liked: false, collected: false,
    publishAt: '10-09 10:05', lastActiveAt: '10-09 10:05', lastReplyBy: '演示成员', pinned: false, hot: false,
    body: [
      '十月新进的数媒方向成员，之前剪过两年短片、碰过一点 AE 跟踪，想在比赛里做视觉类的内容输出。',
      '想请教：视觉组的项目周期一般多长？现在进组来得及赶上本赛季的宣传物料吗？'
    ],
    attach: []
  }
]

/** 按 id 找帖子；找不到返回 null（处理器据此抛 1004） */
export function findTopic(id: string): MockTopic | null {
  for (let i = 0; i < topics.length; i++) {
    if (topics[i].id === id) return topics[i]
  }
  return null
}

/** 新帖自增 id（种子最大 331，新帖从 332 起） */
let nextTopicId = 332
export function allocTopicId(): string {
  const id = String(nextTopicId)
  nextTopicId++
  return id
}
