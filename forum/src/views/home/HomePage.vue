<script setup lang="ts">
/**
 * 论坛首页：版块 tab + 排序 + 主题列表 + 热帖榜右栏
 * 结构参照 Discourse 主题行解剖 + V2EX 热榜侧栏；设计稿 design-preview/forum-site/index.html
 * TODO(API)：列表 GET /api/forum/topics（board/sort/page），热榜 GET /api/forum/topics/hot——
 *    论坛服务接口清单待出（骨架阶段静态演示数据）
 * ⚠️ 注意：热度 = 点赞加权 × 时间衰减（架构方案 §5.3，Redis ZSET），前端只展示不算分
 */
import { ref } from 'vue'
import { BOARDS, boardName } from '@/config/boards'
import type { BoardCode, TopicItem } from '@/types'

const activeBoard = ref<BoardCode>('GENERAL')
const activeSort = ref<'latest' | 'hot' | 'pinned'>('latest')

/** 骨架阶段静态演示数据（字段对齐 TopicItem，联调时替换为接口返回） */
const topics: TopicItem[] = [
  { id: '331', board: 'EE', title: '赛季电控培训第三期：STM32 HAL 库电机闭环控制速成', author: '赵天宇', authorGroup: '电控组', tags: ['STM32', '电机控制', '培训'], replyCount: 24, viewCount: 1200, likeCount: 58, lastActiveAt: '10-07 20:15', pinned: true, hot: true },
  { id: '330', board: 'MODELING', title: '建模组十月招新面试题与评分标准公示', author: '陈子昂', authorGroup: '建模组', tags: ['招新', '公示'], replyCount: 31, viewCount: 986, likeCount: 47, lastActiveAt: '10-07 19:44', pinned: false, hot: false },
  { id: '329', board: 'EE', title: 'PCB 四层板电源完整性 Layout 检查清单分享', author: '陈子昂', authorGroup: '电控组', tags: ['PCB', '电源完整性'], replyCount: 12, viewCount: 643, likeCount: 31, lastActiveAt: '10-07 18:02', pinned: false, hot: false },
  { id: '328', board: 'EE', title: '请教：CAN 总线偶发丢帧，终端电阻已经核对过', author: '李明轩', authorGroup: '建模组', tags: ['CAN', '排障'], replyCount: 9, viewCount: 402, likeCount: 14, lastActiveAt: '10-07 15:47', pinned: false, hot: false },
  { id: '327', board: 'EE', title: 'RoboMaster 电控复盘：步兵机器人的功率限制策略', author: '孙嘉仪', authorGroup: '电控组', tags: ['RoboMaster', '功率控制'], replyCount: 18, viewCount: 866, likeCount: 42, lastActiveAt: '10-06 21:30', pinned: false, hot: false },
  { id: '326', board: 'AI', title: 'AI 组：本地部署 Qwen 做赛题摘要的踩坑记录', author: '王雨桐', authorGroup: 'AI 组', tags: ['Qwen', '本地部署'], replyCount: 15, viewCount: 594, likeCount: 36, lastActiveAt: '10-06 17:20', pinned: false, hot: false }
]

const hotRank = [
  { title: '电控培训第三期：电机闭环控制速成', heat: 926 },
  { title: '建模组十月招新面试题与评分标准', heat: 814 },
  { title: 'PCB 电源完整性 Layout 检查清单', heat: 690 },
  { title: '步兵机器人功率限制策略复盘', heat: 577 },
  { title: '本地部署 Qwen 做赛题摘要踩坑记录', heat: 498 }
]

function fmtViews(n: number): string {
  return n >= 1000 ? (n / 1000).toFixed(1) + 'k' : String(n)
}
</script>

<template>
  <div class="home-grid">
    <div>
      <div class="section-head">
        <h2>综合讨论<i>LATEST TOPICS</i></h2>
        <p>本周新增 32 帖 · 全站成员 186 人</p>
      </div>

      <div class="board-tabs">
        <div class="tabs">
          <a
            v-for="b in BOARDS"
            :key="b.code"
            href="#"
            :class="{ active: activeBoard === b.code }"
            @click.prevent="activeBoard = b.code"
          >{{ b.name }}</a>
        </div>
        <div class="sort">
          <a href="#" :class="{ active: activeSort === 'latest' }" @click.prevent="activeSort = 'latest'">最新</a>
          <a href="#" :class="{ active: activeSort === 'hot' }" @click.prevent="activeSort = 'hot'">最热</a>
          <a href="#" :class="{ active: activeSort === 'pinned' }" @click.prevent="activeSort = 'pinned'">置顶</a>
        </div>
      </div>

      <div
        v-for="(t, i) in topics"
        :key="t.id"
        class="topic-row"
        :class="{ pin: t.pinned }"
      >
        <span class="idx">{{ ['①', '②', '③', '④', '⑤', '⑥', '⑦', '⑧'][i] ?? '·' }}</span>
        <div class="t-main">
          <div class="t-title">
            <span v-if="t.pinned" class="pintag">置顶</span>
            <router-link :to="`/topic/${t.id}`">{{ t.title }}</router-link>
            <span class="board-stamp">{{ boardName(t.board) }}</span>
          </div>
          <div class="t-meta">
            <i class="ava">{{ t.author.slice(0, 1) }}</i>{{ t.author }} · {{ t.authorGroup }}
            <span class="chips"><i v-for="tag in t.tags" :key="tag">{{ tag }}</i></span>
          </div>
        </div>
        <div class="t-nums">
          <span :class="{ hot: t.hot }"><b>{{ t.replyCount }}</b>回复</span>
          <span><b>{{ fmtViews(t.viewCount) }}</b>浏览</span>
          <span :class="{ hot: t.hot }"><b>{{ t.likeCount }}</b>点赞</span>
        </div>
        <span class="t-active mono">{{ t.lastActiveAt }}</span>
      </div>

      <div class="list-foot">
        <span class="mono">共 86 帖 · 第 1 / 13 页</span>
        <span class="fs">热度 = 点赞加权 × 时间衰减</span>
      </div>
    </div>

    <aside class="rail">
      <div class="rail-card">
        <h5>本周热帖榜<i>TOP 5</i></h5>
        <ol class="hotrank">
          <li v-for="(h, i) in hotRank" :key="h.title">
            <b>{{ i + 1 }}</b><span>{{ h.title }}</span><em>{{ h.heat }}</em>
          </li>
        </ol>
      </div>
      <div class="rail-card">
        <h5>社区状态</h5>
        <div class="stat-grid">
          <div><b>23</b><span>当前在线</span></div>
          <div><b>32</b><span>本周新帖</span></div>
          <div><b>186</b><span>成员</span></div>
        </div>
      </div>
      <div class="rail-card">
        <h5>会审公约</h5>
        <p class="tip">排障记录、培训笔记、复盘总结都可发帖；优质帖由版主推荐归档进知识库。禁止与赛事无关的水帖。</p>
        <button class="btn-ink" type="button">发布新帖</button>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.home-grid { display: grid; grid-template-columns: 1fr 300px; gap: 26px; }
.section-head { display: flex; align-items: baseline; gap: 16px; padding-bottom: 12px; border-bottom: 1.5px solid var(--vio-ink); margin-bottom: 4px; }
.section-head h2 { font-family: var(--vio-serif); font-size: 23px; letter-spacing: 0.16em; font-weight: 700; }
.section-head h2 i { font-style: normal; font-family: var(--vio-mono); font-size: 10px; color: var(--vio-ink-3); letter-spacing: 0.18em; margin-left: 10px; }
.section-head p { font-size: 12px; color: var(--vio-ink-3); font-family: var(--vio-fangsong); letter-spacing: 0.08em; flex: 1; }
.board-tabs { display: flex; justify-content: space-between; align-items: center; margin: 14px 0 8px; }
.tabs { display: flex; gap: 22px; }
.tabs a { font-size: 13.5px; color: var(--vio-ink-2); letter-spacing: 0.12em; padding-bottom: 4px; border-bottom: 2px solid transparent; }
.tabs a.active { color: var(--vio-blue-ink); font-weight: 700; border-bottom-color: var(--vio-blue-ink); }
.sort { display: flex; font-family: var(--vio-mono); font-size: 11.5px; }
.sort a { padding: 4px 12px; border: 1px solid var(--vio-rule); color: var(--vio-ink-3); margin-left: -1px; background: var(--vio-sheet); }
.sort a.active { color: var(--vio-paper); background: var(--vio-ink); border-color: var(--vio-ink); }
.topic-row { display: flex; align-items: center; gap: 16px; padding: 14px 10px; border-bottom: 1px solid var(--vio-rule-soft); transition: background 0.2s var(--vio-ease); }
.topic-row:hover { background: var(--vio-wash); }
.topic-row .idx { width: 30px; height: 30px; flex: none; display: grid; place-items: center; border: 1.5px solid var(--vio-rule); border-radius: 50%; font-size: 13px; color: var(--vio-ink-3); }
.topic-row.pin .idx { border-color: var(--vio-seal); color: var(--vio-seal); }
.t-main { flex: 1; min-width: 0; }
.t-title { font-size: 15px; font-weight: 600; line-height: 1.5; }
.t-title a { color: var(--vio-ink); }
.t-title a:hover { color: var(--vio-blue-ink); text-decoration: underline; }
.pintag { font-size: 10.5px; color: var(--vio-seal); border: 1px solid var(--vio-seal); padding: 0 7px; margin-right: 8px; letter-spacing: 0.1em; vertical-align: 2px; }
.board-stamp { font-size: 11px; color: var(--vio-blue-ink); background: var(--vio-wash); padding: 1px 8px; margin-left: 8px; letter-spacing: 0.08em; vertical-align: 2px; }
.t-meta { margin-top: 5px; font-size: 12px; color: var(--vio-ink-3); display: flex; align-items: center; gap: 7px; flex-wrap: wrap; }
.ava { display: inline-grid; place-items: center; width: 20px; height: 20px; border-radius: 50%; background: var(--vio-wash); color: var(--vio-blue-ink); font-size: 10px; font-weight: 700; font-style: normal; font-family: var(--vio-serif); border: 1px solid var(--vio-rule); }
.chips { display: inline-flex; gap: 5px; margin-left: 4px; }
.chips i { font-style: normal; font-size: 11px; color: #3D5AB8; background: var(--vio-wash); padding: 0 7px; border-radius: 2px; }
.t-nums { display: flex; gap: 20px; white-space: nowrap; }
.t-nums span { font-size: 10.5px; color: var(--vio-ink-3); text-align: center; letter-spacing: 0.06em; }
.t-nums b { display: block; font-family: var(--vio-mono); font-size: 15px; color: var(--vio-ink); font-weight: 600; }
.t-nums .hot b { color: var(--vio-seal); }
.t-active { width: 90px; text-align: right; font-size: 11px; color: var(--vio-ink-3); }
.list-foot { display: flex; justify-content: space-between; align-items: center; padding: 16px 10px 4px; font-size: 12px; color: var(--vio-ink-3); }
.rail { display: flex; flex-direction: column; gap: 16px; }
.rail-card { background: var(--vio-sheet); border: 1px solid var(--vio-rule); padding: 16px 18px; }
.rail-card h5 { font-family: var(--vio-serif); font-size: 14.5px; letter-spacing: 0.16em; font-weight: 700; padding-bottom: 9px; border-bottom: 1px solid var(--vio-ink); margin-bottom: 6px; display: flex; justify-content: space-between; align-items: baseline; }
.rail-card h5 i { font-style: normal; font-family: var(--vio-mono); font-size: 9.5px; color: var(--vio-ink-3); letter-spacing: 0.14em; }
.hotrank { list-style: none; }
.hotrank li { display: flex; align-items: baseline; gap: 10px; padding: 8px 0; border-bottom: 1px dotted var(--vio-rule); font-size: 12.5px; }
.hotrank li:last-child { border-bottom: none; }
.hotrank b { font-family: var(--vio-mono); font-size: 12px; color: var(--vio-ink-3); width: 15px; flex: none; }
.hotrank li:first-child b { color: var(--vio-seal); font-size: 14px; }
.hotrank span { flex: 1; color: var(--vio-ink-2); line-height: 1.55; }
.hotrank em { font-style: normal; font-family: var(--vio-mono); color: var(--vio-blue-ink); font-size: 12px; font-weight: 700; }
.stat-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; text-align: center; }
.stat-grid div { border-right: 1px solid var(--vio-rule-soft); padding: 4px 0; }
.stat-grid div:last-child { border-right: none; }
.stat-grid b { display: block; font-family: var(--vio-disp-latin); font-size: 21px; color: var(--vio-blue-ink); font-variant-numeric: tabular-nums; }
.stat-grid span { font-size: 10.5px; color: var(--vio-ink-3); }
.tip { font-family: var(--vio-fangsong); font-size: 12.5px; color: var(--vio-ink-2); line-height: 2; margin-bottom: 12px; }
.btn-ink { padding: 8px 22px; background: var(--vio-ink); color: var(--vio-paper); border: none; font-size: 13px; letter-spacing: 0.18em; text-indent: 0.18em; font-family: var(--vio-serif); font-weight: 700; transition: background 0.25s var(--vio-ease); }
.btn-ink:hover { background: var(--vio-blue-ink); }
</style>
