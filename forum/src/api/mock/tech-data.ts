/**
 * 技术交流推送内存种子数据（mock 层）
 * 契约：《.agentdocs/plans/2026-10-08/技术交流-接口清单.md》T01 列表项字段（草案待 lzh 确认）
 * 分类用 FEED_CATEGORIES 的 code（字典值），渲染时 config 侧翻译
 */
export interface MockTechPost {
  id: string
  title: string
  summary: string
  /** 分类字典值（EE/MODELING/AI/VIDEO/DEV/OTHER） */
  category: string
  /** 内容形态：ARTICLE 图文 / VIDEO 视频 / LINK 外链 */
  contentType: 'ARTICLE' | 'VIDEO' | 'LINK'
  /** 来源：ADMIN 管理员录入 / RSS 外部收录 / AI 摘要 */
  sourceType: 'ADMIN' | 'RSS' | 'AI'
  /** 来源名（外部收录的公众号名 / AI 摘要的模型名） */
  sourceName: string
  pinned: boolean
  publishAt: string
  viewCount: number
  collectCount: number
  collected: boolean
  tags: string[]
  /** ARTICLE/VIDEO 的正文段落；LINK 的外链地址 */
  content: string[]
  linkUrl: string
  /** 逻辑删标记（撤回后 T01/T02 不再可见） */
  withdrawn: boolean
}

export const techPosts: MockTechPost[] = [
  {
    id: '14', title: '本周必读：新手焊接贴片元件的五分钟检查法',
    summary: '烙铁温度、焊膏量、放大镜下的拉尖检查——上期培训后仍有三起虚焊返工，这篇流程图对照着过一遍，再上板子。',
    category: 'EE', contentType: 'ARTICLE', sourceType: 'ADMIN', sourceName: '电控组',
    pinned: true, publishAt: '10-08 08:00', viewCount: 412, collectCount: 38, collected: false,
    tags: ['焊接', '工艺'],
    content: [
      '上期培训后工作室出现三起贴片虚焊返工，都出在"看一眼觉得没问题"。把检查压成五分钟固定流程：烙铁温度确认、焊膏量目检、放大镜下扫拉尖与立碑。',
      '流程图见正文附图，建议打印贴在焊接工位。'
    ],
    linkUrl: '', withdrawn: false
  },
  {
    id: '13', title: 'Function Calling 实践：让本地大模型安全地查数据库',
    summary: '工具调用的权限边界怎么划：令牌转发、只读视图、结果裁剪三步走。附我们平台 AI 助手的实现思路。',
    category: 'AI', contentType: 'ARTICLE', sourceType: 'AI', sourceName: 'GLM 摘要',
    pinned: false, publishAt: '10-08 07:30', viewCount: 287, collectCount: 21, collected: true,
    tags: ['GLM', '权限'],
    content: [
      '让大模型碰数据库，核心不是能力是边界。三步走：一、不给原始凭据，转发短时效令牌；二、模型只挂只读视图；三、结果在返回前裁剪掉非白名单字段。',
      '我们平台 AI 助手的工具调用按这套实现，附关键代码段。'
    ],
    linkUrl: '', withdrawn: false
  },
  {
    id: '12', title: 'SolidWorks 装配体配合的三个层级：从能动到好改',
    summary: '配合关系决定模型可维护性。用我们底盘的实例讲清"重合—距离—高级配合"怎么选。',
    category: 'MODELING', contentType: 'ARTICLE', sourceType: 'RSS', sourceName: '机械设计公众号',
    pinned: false, publishAt: '10-07 22:00', viewCount: 198, collectCount: 15, collected: false,
    tags: ['CAD', '装配体'],
    content: [
      '能动只是及格线，好改才是目标。配合分三层：重合配合快速但死板、距离配合留出调整余地、高级配合（宽度/齿轮）表达真实机构意图。',
      '用我们底盘悬挂的实例对比三种写法在后期改型时的成本差异。'
    ],
    linkUrl: '', withdrawn: false
  },
  {
    id: '11', title: '平台后端骨架 Step3 预告：统一字典与接口幂等怎么落',
    summary: '下周骨架进公共服务层，先把字典 hook 和幂等键的前端约定讲清楚，A/B/C 三线照做即可。',
    category: 'DEV', contentType: 'ARTICLE', sourceType: 'ADMIN', sourceName: '平台组',
    pinned: false, publishAt: '10-07 20:00', viewCount: 156, collectCount: 12, collected: false,
    tags: ['SpringBoot', '规范'],
    content: [
      '字典统一走 DR01 一个入口，前端枚举下拉禁止硬编码；幂等键由前端生成 UUID、随 X-Idempotency-Key 头上送，重复键 1005 拦截不回放。',
      '三线接入示例代码下周随骨架一起发。'
    ],
    linkUrl: '', withdrawn: false
  },
  {
    id: '10', title: 'PID 整定实战参数表：M3508 速度环的一组安全起点',
    summary: 'Kp 0.8 / Ki 0.02 起步，附上期培训实测波形对照。别一上来就拉满比例项。',
    category: 'EE', contentType: 'VIDEO', sourceType: 'ADMIN', sourceName: '电控组',
    pinned: false, publishAt: '10-07 18:30', viewCount: 356, collectCount: 44, collected: false,
    tags: ['STM32', 'PID'],
    content: ['录屏 12 分钟：从 Kp 扫到积分饱和，每一步的波形截图与参数对照都在时间轴里。'],
    linkUrl: '', withdrawn: false
  },
  {
    id: '9', title: '比赛宣传片的节奏模板：15 秒钩子 + 45 秒过程 + 15 秒结果',
    summary: '招新视频别再平铺直叙。拆了三支获奖队伍的宣传片，剪出可直接套用的节奏表。',
    category: 'VIDEO', contentType: 'ARTICLE', sourceType: 'AI', sourceName: 'GLM 摘要',
    pinned: false, publishAt: '10-07 12:00', viewCount: 264, collectCount: 19, collected: false,
    tags: ['宣传', '剪辑'],
    content: [
      '拆了三支获奖宣传片，节奏惊人一致：15 秒钩子放最炸的画面、45 秒过程讲人做事、15 秒结果收身份认同。',
      '附时间轴模板，直接往里填素材就能用。'
    ],
    linkUrl: '', withdrawn: false
  },
  {
    id: '8', title: '新成员电脑环境统一清单：系统镜像、驱动与常用软件包',
    summary: '不归属具体技术方向的公共事务推送也走这里：装环境这篇先按清单过一遍，少踩三天坑。',
    category: 'OTHER', contentType: 'LINK', sourceType: 'ADMIN', sourceName: '平台组',
    pinned: false, publishAt: '10-06 09:00', viewCount: 203, collectCount: 9, collected: false,
    tags: ['环境', '公共'],
    content: [],
    linkUrl: 'https://example.vio.internal/env-checklist',
    withdrawn: false
  }
]

/** 按 id 找可见推送（撤回的对外等于不存在） */
export function findTechPost(id: string): MockTechPost | null {
  for (let i = 0; i < techPosts.length; i++) {
    if (techPosts[i].id === id && !techPosts[i].withdrawn) return techPosts[i]
  }
  return null
}

/** 新推送自增 id（种子最大 14） */
let nextTechId = 15
export function allocTechId(): string {
  const id = String(nextTechId)
  nextTechId++
  return id
}

/** 订阅标签字典（T04）：预设标签 + 管理员扩展，演示期固定 */
export const techTags: string[] = ['STM32', '电机控制', 'CAD', 'GLM', 'PCB', 'RoboMaster', '焊接', '视频剪辑', 'SpringBoot']
