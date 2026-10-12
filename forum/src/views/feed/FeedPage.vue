<script setup lang="ts">
/**
 * 技术交流（每日推送内容流）——与首页同一套行式语言
 * 视觉基准：.agentdocs/design-preview/forum-v2ex/feed.html（v3 样张，lzh 已过目）
 * 需求来源：PRD §3.12 技术交流（2026-10-08 拍板迁入论坛独立站）
 * 覆盖要求：六分类（feedCategories.ts 单一数据源）、来源标注（管理员录入/外部收录/AI 摘要）、
 *    收藏、订阅标签、管理员置顶/撤回；本期不做个性化推荐
 * TODO(API)：列表 GET /api/tech-posts（category/tag/page）、收藏 POST /api/tech-posts/{id}/collect——
 *    接口清单草案在 .agentdocs/plans/2026-10-08/技术交流-接口清单.md（待 lzh 确认）
 * ⚠️ 并发：收藏为写操作，需带 X-Idempotency-Key（规范 §6）；骨架阶段按钮仅演示态
 */
import { ref } from 'vue'
import { FEED_CATEGORIES, feedCategoryOf } from '@/config/feedCategories'
import { avatarOf } from '@/config/avatars'

/** 当前选中分类：'ALL' 为全部 */
const activeCat = ref('ALL')

interface FeedItem {
  id: string
  category: string
  title: string
  summary: string
  source: '管理员录入' | '外部收录' | 'AI 摘要'
  publishAt: string
  viewCount: number
  collectCount: number
  pinned: boolean
  tags: string[]
  collected: boolean
}

/** 静态演示数据（字段以接口清单为准；分类覆盖六类各至少一条） */
const feeds: FeedItem[] = [
  { id: '12', category: '电控', title: '本周必读：新手焊接贴片元件的五分钟检查法', summary: '烙铁温度、焊膏量、放大镜下的拉尖检查——上期培训后仍有三起虚焊返工，这篇流程图对照着过一遍，再上板子。', source: '管理员录入', publishAt: '10-08 08:00', viewCount: 412, collectCount: 38, pinned: true, tags: ['焊接', '工艺'], collected: false },
  { id: '11', category: 'AI', title: 'Function Calling 实践：让本地大模型安全地查数据库', summary: '工具调用的权限边界怎么划：令牌转发、只读视图、结果裁剪三步走。附我们平台 AI 助手的实现思路。', source: 'AI 摘要', publishAt: '10-08 07:30', viewCount: 287, collectCount: 21, pinned: false, tags: ['GLM', '权限'], collected: true },
  { id: '10', category: '建模', title: 'SolidWorks 装配体配合的三个层级：从能动到好改', summary: '配合关系决定模型可维护性。用我们底盘的实例讲清"重合—距离—高级配合"怎么选。', source: '外部收录', publishAt: '10-07 22:00', viewCount: 198, collectCount: 15, pinned: false, tags: ['CAD', '装配体'], collected: false },
  { id: '13', category: '编程开发', title: '平台后端骨架 Step3 预告：统一字典与接口幂等怎么落', summary: '下周骨架进公共服务层，先把字典 hook 和幂等键的前端约定讲清楚，A/B/C 三线照做即可。', source: '管理员录入', publishAt: '10-07 20:00', viewCount: 156, collectCount: 12, pinned: false, tags: ['SpringBoot', '规范'], collected: false },
  { id: '9', category: '电控', title: 'PID 整定实战参数表：M3508 速度环的一组安全起点', summary: 'Kp 0.8 / Ki 0.02 起步，附上期培训实测波形对照。别一上来就拉满比例项。', source: '管理员录入', publishAt: '10-07 18:30', viewCount: 356, collectCount: 44, pinned: false, tags: ['STM32', 'PID'], collected: false },
  { id: '8', category: '视频剪辑', title: '比赛宣传片的节奏模板：15 秒钩子 + 45 秒过程 + 15 秒结果', summary: '招新视频别再平铺直叙。拆了三支获奖队伍的宣传片，剪出可直接套用的节奏表。', source: 'AI 摘要', publishAt: '10-07 12:00', viewCount: 264, collectCount: 19, pinned: false, tags: ['宣传', '剪辑'], collected: false },
  { id: '14', category: '其他', title: '新成员电脑环境统一清单：系统镜像、驱动与常用软件包', summary: '不归属具体技术方向的公共事务推送也走这里：装环境这篇先按清单过一遍，少踩三天坑。', source: '管理员录入', publishAt: '10-06 09:00', viewCount: 203, collectCount: 9, pinned: false, tags: ['环境', '公共'], collected: false }
]

/** 订阅标签（on = 已订阅选中态）；TODO(联调)：由我的订阅接口下发 */
const subTags = [
  { name: 'STM32', on: true },
  { name: '电机控制', on: false },
  { name: 'CAD', on: true },
  { name: 'GLM', on: false },
  { name: '视频剪辑', on: true },
  { name: 'PCB', on: false },
  { name: 'RoboMaster', on: false }
]

/** 侧栏今日热议 */
const hotRank = [
  { title: '电控培训第三期：电机闭环控制速成', author: '赵天宇' },
  { title: '建模组十月招新面试题与评分标准', author: '陈子昂' },
  { title: 'PCB 电源完整性 Layout 检查清单', author: '陈子昂' }
]
</script>

<template>
  <div class="grid">
    <div class="box">
      <!-- 频道行第一行：七分类 tab + 右端来源说明 -->
      <div class="tabs">
        <a href="#" :class="{ on: activeCat === 'ALL' }" @click.prevent="activeCat = 'ALL'">全部</a>
        <a
          v-for="c in FEED_CATEGORIES"
          :key="c.code"
          href="#"
          :class="{ on: activeCat === c.code }"
          @click.prevent="activeCat = c.code"
        >{{ c.name }}</a>
        <span class="right">管理员录入 / 外部收录 / AI 摘要</span>
      </div>
      <!-- 频道行第二行：订阅标签 -->
      <div class="tabs2">
        <b>订阅标签</b>
        <a v-for="t in subTags" :key="t.name" href="#" :class="{ 'sub-on': t.on }" @click.prevent>{{ t.name }}</a>
      </div>

      <!-- 推送行：左 48px 分类色块字 + 标题/摘要单行截断/meta + 右收藏数胶囊 -->
      <div v-for="f in feeds" :key="f.id" class="cell">
        <span class="cat" :class="feedCategoryOf(f.category).classKey">{{ feedCategoryOf(f.category).short }}</span>
        <div class="mid">
          <div class="f-title">
            <span v-if="f.pinned" class="pin">置顶</span>
            <a href="#" @click.prevent>{{ f.title }}</a>
          </div>
          <div class="f-sum">{{ f.summary }}</div>
          <div class="f-meta">
            <span class="node">{{ f.category }}</span>{{ f.source }} · {{ f.publishAt }} · 阅读 {{ f.viewCount }} · {{ f.tags.join(' · ') }}
          </div>
        </div>
        <span class="collect" :class="{ on: f.collected }">{{ f.collected ? '已收藏' : '⭐' }} {{ f.collectCount }}</span>
      </div>
    </div>

    <div class="side">
      <div class="box">
        <h5>今日热议主题</h5>
        <ul class="hot">
          <li v-for="h in hotRank" :key="h.title">
            <img class="hava" :src="avatarOf(h.author, 24)" :alt="h.author">
            <router-link to="/topic/331">{{ h.title }}</router-link>
          </li>
        </ul>
      </div>
      <div class="box">
        <h5>本周推送</h5>
        <div class="stat">
          <div><span>推送</span><b>23</b></div>
          <div><span>被收藏最多</span><b>9</b></div>
          <div><span>已归档知识库</span><b>4</b></div>
        </div>
      </div>
      <div class="box">
        <h5>知识库入口</h5>
        <p class="tip">优质推送与论坛精华帖经版主推荐后归档至知识库。</p>
        <router-link class="plain" to="/kb">进入知识库</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: 1fr 270px; gap: 20px; align-items: start; }
.box { background: var(--box); border-radius: var(--radius-box); box-shadow: var(--shadow-box); }

/* 频道行 */
.tabs { padding: 10px 14px 8px; border-bottom: 1px solid var(--line); }
.tabs a { font-size: 13px; color: var(--body); padding: 3px 9px; border-radius: 4px; margin-right: 2px; }
.tabs a.on { background: var(--tab-dark); color: #FFF; }
.tabs a:not(.on):hover { background: #F2F2F2; color: #000; }
.tabs .right { float: right; font-size: 12px; color: var(--fade); padding-top: 4px; }
.tabs2 { padding: 8px 14px; border-bottom: 1px solid var(--line); font-size: 12px; color: var(--fade); }
.tabs2 b { font-weight: 400; margin-right: 10px; }
.tabs2 a { color: var(--fade); margin-right: 12px; }
.tabs2 a.sub-on { color: var(--ink); font-weight: 600; }
.tabs2 a:hover { color: var(--ink); }

/* 推送行 */
.cell { display: flex; gap: 13px; padding: 11px 14px; border-bottom: 1px solid var(--line); }
.cell:last-child { border-bottom: none; }
.cat { width: 48px; height: 48px; flex: none; border-radius: var(--radius-ava); display: grid; place-items: center; font-size: 15px; font-weight: 600; }
/* 六分类浅底深字配色（classKey 与 feedCategories.ts 一一对应） */
.k-ee { background: #DCE7F5; color: #3D5A80; }
.k-mo { background: #E2EFDA; color: #4F6F4A; }
.k-ai { background: #E8E2F5; color: #5A4A8A; }
.k-vd { background: #F5D9E0; color: #8A3D5A; }
.k-dev { background: #DDEFF0; color: #3D6E80; }
.k-etc { background: #EFEFEF; color: #666; }
.mid { flex: 1; min-width: 0; }
.f-title { font-size: 16px; line-height: 1.3; font-weight: 400; }
.f-title a { color: var(--ink); }
.f-title a:hover { color: #000; text-decoration: underline; }
.pin { font-size: 11px; color: var(--fade); border: 1px solid #DDD; border-radius: 3px; padding: 0 5px; margin-right: 6px; vertical-align: 1px; }
.f-sum { font-size: 13px; color: #888; margin-top: 3px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.f-meta { margin-top: 4px; font-size: 12px; color: var(--fade); }
.node { background: #F5F5F5; color: var(--fade); border-radius: 10px; padding: 1px 8px; margin-right: 8px; }
.collect { flex: none; align-self: center; background: #F5F5F5; color: var(--meta); font-size: 12px; border-radius: 10px; padding: 1px 9px; }
.collect.on { background: var(--pill); color: #FFF; }

/* 右栏 */
.side .box { padding: 12px 14px; margin-bottom: 18px; }
.side h5 { font-size: 12.5px; color: var(--fade); font-weight: 400; border-bottom: 1px solid var(--line-soft); padding-bottom: 8px; margin-bottom: 4px; }
.hot { margin: 0; padding: 0; }
.hot li { list-style: none; display: flex; gap: 9px; align-items: flex-start; padding: 8px 0; border-bottom: 1px solid #F4F4F4; }
.hot li:last-child { border: none; padding-bottom: 2px; }
.hava { width: 24px; height: 24px; flex: none; border-radius: var(--radius-ava); object-fit: cover; margin-top: 1px; }
.hot a { font-size: 12.5px; color: var(--body); line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.hot a:hover { color: #000; }
.stat div { display: flex; justify-content: space-between; padding: 4px 0; border-bottom: 1px solid #F7F7F7; font-size: 12.5px; color: var(--fade); }
.stat div:last-child { border: none; }
.stat b { color: var(--body); font-weight: 600; }
.tip { font-size: 12.5px; color: #888; line-height: 1.8; }
.plain { display: inline-block; margin-top: 8px; font-size: 12.5px; border: 1px solid var(--line); border-radius: var(--radius-box); padding: 4px 12px; background: var(--box-soft); color: var(--body); }
.plain:hover { border-color: #CCC; color: #000; }
</style>
