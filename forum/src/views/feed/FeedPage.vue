<script setup lang="ts">
/**
 * 技术交流（每日推送内容流）——本页是组长指定的首发模块
 * 需求来源：PRD §3.12 技术交流（2026-10-08 拍板迁入论坛独立站）
 * 覆盖要求：分类（电控/建模/AI/视频剪辑，可扩展）、内容形态（视频/文章/教程链接）、
 *    推送来源标注（管理员录入/外部 RSS / AI 摘要）、收藏、标签筛选、管理员置顶/撤回、
 *    本期不做个性化推荐，按订阅标签展示
 * TODO(API)：列表 GET /api/tech-posts（category/tag/page）、收藏 POST /api/tech-posts/{id}/collect——
 *    接口清单草案在 .agentdocs/plans/2026-10-08/技术交流-接口清单.md（待组长确认）
 * ⚠️ 并发：收藏为写操作，需带 X-Idempotency-Key（规范 §6）；骨架阶段按钮仅演示态
 */
import { ref } from 'vue'

type FeedCategory = 'ALL' | 'EE' | 'MODELING' | 'AI' | 'VIDEO'
const activeCat = ref<FeedCategory>('ALL')

interface FeedItem {
  id: string
  category: string
  title: string
  summary: string
  source: '管理员录入' | '外部收录' | 'AI 摘要'
  sourceNote: string
  publishAt: string
  viewCount: number
  collectCount: number
  pinned: boolean
  tags: string[]
  collected: boolean
}

/** 骨架阶段静态演示数据（字段以接口清单为准） */
const feeds: FeedItem[] = [
  { id: '12', category: '电控', title: '本周必读：新手焊接贴片元件的五分钟检查法', summary: '烙铁温度、焊膏量、放大镜下的拉尖检查——上期培训后仍有三起虚焊返工，这篇流程图对照着过一遍，再上板子。', source: '管理员录入', sourceNote: '已置顶', publishAt: '2026-10-08 08:00', viewCount: 412, collectCount: 38, pinned: true, tags: ['焊接', '工艺'], collected: false },
  { id: '11', category: 'AI', title: 'Function Calling 实践：让本地大模型安全地查数据库', summary: '工具调用的权限边界怎么划：令牌转发、只读视图、结果裁剪三步走。附我们平台 AI 助手的实现思路。', source: 'AI 摘要', sourceNote: '来源 外部公开内容', publishAt: '2026-10-08 07:30', viewCount: 287, collectCount: 21, pinned: false, tags: ['GLM', '权限'], collected: true },
  { id: '10', category: '建模', title: 'SolidWorks 装配体配合的三个层级：从能动到好改', summary: '配合关系决定模型可维护性。用我们底盘的实例讲清"重合—距离—高级配合"怎么选。', source: '外部收录', sourceNote: '公众号链接', publishAt: '2026-10-07 22:00', viewCount: 198, collectCount: 15, pinned: false, tags: ['CAD', '装配体'], collected: false },
  { id: '9', category: '电控', title: 'PID 整定实战参数表：M3508 速度环的一组安全起点', summary: 'Kp 0.8 / Ki 0.02 起步，附上期培训实测波形对照。别一上来就拉满比例项。', source: '管理员录入', sourceNote: '', publishAt: '2026-10-07 18:30', viewCount: 356, collectCount: 44, pinned: false, tags: ['STM32', 'PID'], collected: false },
  { id: '8', category: '视频剪辑', title: '比赛宣传片的节奏模板：15 秒钩子 + 45 秒过程 + 15 秒结果', summary: '招新视频别再平铺直叙。拆了三支获奖队伍的宣传片，剪出可直接套用的节奏表。', source: 'AI 摘要', sourceNote: '来源 外部公开内容', publishAt: '2026-10-07 12:00', viewCount: 264, collectCount: 19, pinned: false, tags: ['宣传', '剪辑'], collected: false }
]

const cats: { code: FeedCategory; name: string }[] = [
  { code: 'ALL', name: '全部' },
  { code: 'EE', name: '电控' },
  { code: 'MODELING', name: '建模' },
  { code: 'AI', name: 'AI' },
  { code: 'VIDEO', name: '视频剪辑' }
]

const subTags = ['STM32', '电机控制', 'CAD', 'GLM', '视频剪辑', 'PCB', '招新宣传', 'RoboMaster']
</script>

<template>
  <div class="feed-grid">
    <div>
      <div class="section-head">
        <h2>技术交流<i>DAILY FEED</i></h2>
        <p>每日自动推送 · 管理员录入 / 外部收录 / AI 摘要</p>
      </div>
      <p class="head-note">※ 按订阅标签展示；管理员可置顶与撤回。收藏进入个人档案，优质内容归档知识库。</p>

      <div class="cat-tabs">
        <a
          v-for="c in cats"
          :key="c.code"
          href="#"
          :class="{ active: activeCat === c.code }"
          @click.prevent="activeCat = c.code"
        >{{ c.name }}</a>
      </div>

      <article v-for="f in feeds" :key="f.id" class="feed-card" :class="{ pinned: f.pinned }">
        <div class="fc-head">
          <span class="fc-cat">{{ f.category }}</span>
          <span class="fc-src mono">{{ f.source }}<template v-if="f.sourceNote"> · {{ f.sourceNote }}</template></span>
          <span class="fc-time mono">{{ f.publishAt }}</span>
        </div>
        <h3><span v-if="f.pinned" class="pintag">置顶</span>{{ f.title }}</h3>
        <p>{{ f.summary }}</p>
        <div class="fc-foot">
          <span class="mono">阅读 {{ f.viewCount }}</span>
          <a href="#" :class="{ collected: f.collected }" @click.prevent>
            {{ f.collected ? '已收藏' : '收藏' }} {{ f.collectCount }}
          </a>
          <span class="mono">标签：{{ f.tags.join(' · ') }}</span>
        </div>
      </article>

      <div class="list-foot">
        <span class="mono">今日已推送 5 条 · 历史 214 条</span>
        <span class="fs">按订阅标签展示，本期不做个性化推荐</span>
      </div>
    </div>

    <aside class="rail">
      <div class="rail-card">
        <h5>订阅标签</h5>
        <div class="tag-cloud">
          <a v-for="(t, i) in subTags" :key="t" href="#" :class="{ on: i % 2 === 0 }" @click.prevent>{{ t }}</a>
        </div>
      </div>
      <div class="rail-card">
        <h5>知识库入口</h5>
        <p class="tip">教程、规范、提示词库的沉淀区。优质推送与论坛精华帖经版主推荐后归档至此。</p>
        <router-link class="btn-plain" to="/kb">进入知识库</router-link>
      </div>
      <div class="rail-card">
        <h5>本周推送</h5>
        <div class="stat-grid">
          <div><b>23</b><span>推送</span></div>
          <div><b>9</b><span>被收藏最多</span></div>
          <div><b>4</b><span>已归档</span></div>
        </div>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.feed-grid { display: grid; grid-template-columns: 1fr 300px; gap: 26px; }
.section-head { display: flex; align-items: baseline; gap: 16px; padding-bottom: 12px; border-bottom: 1.5px solid var(--vio-ink); }
.section-head h2 { font-family: var(--vio-serif); font-size: 23px; letter-spacing: 0.16em; font-weight: 700; }
.section-head h2 i { font-style: normal; font-family: var(--vio-mono); font-size: 10px; color: var(--vio-ink-3); letter-spacing: 0.18em; margin-left: 10px; }
.section-head p { font-size: 12px; color: var(--vio-ink-3); font-family: var(--vio-fangsong); letter-spacing: 0.08em; flex: 1; }
.head-note { font-family: var(--vio-fangsong); font-size: 12.5px; color: var(--vio-ink-3); margin: 10px 0 6px; }
.cat-tabs { display: flex; gap: 22px; margin: 8px 0 14px; }
.cat-tabs a { font-size: 13.5px; color: var(--vio-ink-2); letter-spacing: 0.12em; padding-bottom: 4px; border-bottom: 2px solid transparent; }
.cat-tabs a.active { color: var(--vio-blue-ink); font-weight: 700; border-bottom-color: var(--vio-blue-ink); }
.feed-card { background: var(--vio-sheet); border: 1px solid var(--vio-rule); padding: 18px 22px; margin-bottom: 12px; transition: border-color 0.25s var(--vio-ease); }
.feed-card:hover { border-color: var(--vio-blue-ink); }
.feed-card.pinned { border-left: 3px solid var(--vio-seal); }
.fc-head { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.fc-cat { font-size: 11px; color: var(--vio-blue-ink); background: var(--vio-wash); padding: 2px 9px; letter-spacing: 0.1em; }
.fc-src { font-size: 10.5px; color: var(--vio-ink-3); letter-spacing: 0.08em; }
.fc-time { margin-left: auto; font-size: 11px; color: var(--vio-ink-3); }
.feed-card h3 { font-size: 16px; font-weight: 600; margin-bottom: 7px; }
.pintag { font-size: 10.5px; color: var(--vio-seal); border: 1px solid var(--vio-seal); padding: 0 7px; margin-right: 8px; letter-spacing: 0.1em; vertical-align: 2px; }
.feed-card p { font-size: 13px; color: var(--vio-ink-2); line-height: 1.9; margin-bottom: 10px; }
.fc-foot { display: flex; gap: 18px; font-size: 12px; color: var(--vio-ink-3); }
.fc-foot a { color: var(--vio-ink-3); }
.fc-foot a:hover, .fc-foot a.collected { color: var(--vio-seal); }
.list-foot { display: flex; justify-content: space-between; align-items: center; padding: 16px 10px 4px; font-size: 12px; color: var(--vio-ink-3); }
.rail { display: flex; flex-direction: column; gap: 16px; }
.rail-card { background: var(--vio-sheet); border: 1px solid var(--vio-rule); padding: 16px 18px; }
.rail-card h5 { font-family: var(--vio-serif); font-size: 14.5px; letter-spacing: 0.16em; font-weight: 700; padding-bottom: 9px; border-bottom: 1px solid var(--vio-ink); margin-bottom: 6px; }
.tag-cloud { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.tag-cloud a { font-size: 12px; color: var(--vio-ink-2); border: 1px solid var(--vio-rule); padding: 3px 12px; background: var(--vio-paper); }
.tag-cloud a.on { color: var(--vio-paper); background: var(--vio-ink); border-color: var(--vio-ink); }
.tip { font-family: var(--vio-fangsong); font-size: 12.5px; color: var(--vio-ink-2); line-height: 2; margin-bottom: 12px; }
.btn-plain { display: inline-block; padding: 7px 16px; border: 1px solid var(--vio-rule); color: var(--vio-ink-2); font-size: 13px; transition: all 0.25s var(--vio-ease); }
.btn-plain:hover { border-color: var(--vio-ink); color: var(--vio-ink); }
.stat-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; text-align: center; }
.stat-grid div { border-right: 1px solid var(--vio-rule-soft); padding: 4px 0; }
.stat-grid div:last-child { border-right: none; }
.stat-grid b { display: block; font-family: var(--vio-disp-latin); font-size: 21px; color: var(--vio-blue-ink); font-variant-numeric: tabular-nums; }
.stat-grid span { font-size: 10.5px; color: var(--vio-ink-3); }
</style>
