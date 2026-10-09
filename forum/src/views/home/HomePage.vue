<script setup lang="ts">
/**
 * 论坛首页：频道行（版块 + 热门标签）并入主列盒 + 行式主题列表 + 右栏
 * 视觉基准：.agentdocs/design-preview/forum-v2ex/index.html（v3 样张，lzh 已过目）
 * 依据：《论坛V2EX风格重设计-优化方案》§3/§5.1/§5.2
 * TODO(API)：列表 GET /api/forum/topics（board/sort/page）、热议 GET /api/forum/topics/hot——
 *    论坛服务接口清单待出（现为静态演示数据，字段对齐 TopicItem）
 * ⚠️ 注意：热度 = 点赞加权 × 时间衰减（架构方案 §5.3，Redis ZSET），前端只展示不算分
 */
import { ref } from 'vue'
import { BOARDS, boardName } from '@/config/boards'
import { avatarOf } from '@/config/avatars'
import type { BoardCode, TopicItem } from '@/types'

const mainSite = import.meta.env.VITE_MAIN_SITE_URL ?? 'http://localhost:5174'

/** 当前选中版块：'ALL' 为"全部"（boards 之外的伪选项） */
const activeBoard = ref<BoardCode | 'ALL'>('ALL')
const activeSort = ref<'latest' | 'hot' | 'pinned'>('latest')
const SORTS = [
  { code: 'latest', name: '最新' },
  { code: 'hot', name: '最热' },
  { code: 'pinned', name: '置顶' }
] as const

/** 第二行热门标签；TODO(联调)：由标签接口下发 */
const hotTags = ['STM32', '电机控制', 'CAD', 'GLM', 'PCB', 'RoboMaster', '招新']

/** 静态演示数据（字段对齐 TopicItem，联调时替换为接口返回） */
const topics: TopicItem[] = [
  { id: '331', board: 'EE', title: '赛季电控培训第三期：STM32 HAL 库电机闭环控制速成', author: '赵天宇', authorGroup: '电控组', tags: ['STM32', '电机控制', '培训'], replyCount: 24, viewCount: 1200, likeCount: 58, lastActiveAt: '3 小时前', lastReplyBy: '李明轩', pinned: true, hot: true },
  { id: '330', board: 'MODELING', title: '建模组十月招新面试题与评分标准公示', author: '陈子昂', authorGroup: '建模组', tags: ['招新', '公示'], replyCount: 31, viewCount: 986, likeCount: 47, lastActiveAt: '5 小时前', lastReplyBy: '王雨桐', pinned: false, hot: false },
  { id: '329', board: 'EE', title: 'PCB 四层板电源完整性 Layout 检查清单分享', author: '陈子昂', authorGroup: '电控组', tags: ['PCB', '电源完整性'], replyCount: 12, viewCount: 643, likeCount: 31, lastActiveAt: '7 小时前', lastReplyBy: '孙嘉仪', pinned: false, hot: false },
  { id: '328', board: 'EE', title: '请教：CAN 总线偶发丢帧，终端电阻已经核对过', author: '李明轩', authorGroup: '建模组', tags: ['CAN', '排障'], replyCount: 9, viewCount: 402, likeCount: 14, lastActiveAt: '昨天 15:47', lastReplyBy: '赵天宇', pinned: false, hot: false },
  { id: '327', board: 'EE', title: 'RoboMaster 电控复盘：步兵机器人的功率限制策略', author: '孙嘉仪', authorGroup: '电控组', tags: ['RoboMaster', '功率控制'], replyCount: 18, viewCount: 866, likeCount: 42, lastActiveAt: '昨天 21:30', lastReplyBy: '陈子昂', pinned: false, hot: false },
  { id: '326', board: 'AI', title: 'AI 组：本地部署 Qwen 做赛题摘要的踩坑记录', author: '王雨桐', authorGroup: 'AI 组', tags: ['Qwen', '本地部署'], replyCount: 15, viewCount: 594, likeCount: 36, lastActiveAt: '2 天前', lastReplyBy: '赵天宇', pinned: false, hot: false },
  { id: '325', board: 'GENERAL', title: '工作室新到三台示波器，使用登记和注意事项', author: '周子墨', authorGroup: '综合', tags: ['设备', '登记'], replyCount: 7, viewCount: 310, likeCount: 11, lastActiveAt: '2 天前', lastReplyBy: '李明轩', pinned: false, hot: false }
]

/** 今日热议（侧栏）：头像按作者固定映射 */
const hotRank = [
  { title: '电控培训第三期：电机闭环控制速成', author: '赵天宇' },
  { title: '建模组十月招新面试题与评分标准', author: '陈子昂' },
  { title: 'PCB 电源完整性 Layout 检查清单', author: '陈子昂' },
  { title: '步兵机器人功率限制策略复盘', author: '孙嘉仪' },
  { title: '本地部署 Qwen 做赛题摘要踩坑记录', author: '王雨桐' }
]
</script>

<template>
  <div class="grid">
    <!-- 主列白盒：频道两行在盒内，行下即主题列表（不另起盒子） -->
    <div class="box">
      <div class="tabs">
        <a href="#" :class="{ on: activeBoard === 'ALL' }" @click.prevent="activeBoard = 'ALL'">全部</a>
        <a
          v-for="b in BOARDS"
          :key="b.code"
          href="#"
          :class="{ on: activeBoard === b.code }"
          @click.prevent="activeBoard = b.code"
        >{{ b.name }}</a>
        <span class="right">
          <a
            v-for="s in SORTS"
            :key="s.code"
            href="#"
            :class="{ on: activeSort === s.code }"
            @click.prevent="activeSort = s.code"
          >{{ s.name }}</a>
        </span>
      </div>
      <div class="tabs2">
        <a v-for="t in hotTags" :key="t" href="#" @click.prevent>{{ t }}</a>
      </div>

      <!-- 主题行：48px 真人头像 + 标题（400 字重 1.3 行高）+ meta + 回复数胶囊 -->
      <div v-for="t in topics" :key="t.id" class="cell">
        <img class="ava" :src="avatarOf(t.author, 48)" :alt="t.author">
        <div class="mid">
          <div class="t-title">
            <span v-if="t.pinned" class="pin">置顶</span>
            <router-link :to="`/topic/${t.id}`">{{ t.title }}</router-link>
          </div>
          <div class="t-meta">
            <span class="node">{{ boardName(t.board) }}</span><b>{{ t.author }}</b> · {{ t.lastActiveAt }} · 最后回复来自 <b>{{ t.lastReplyBy }}</b>
          </div>
        </div>
        <span class="pill">{{ t.replyCount }}</span>
      </div>
    </div>

    <div class="side">
      <div class="box brand-box">
        <div class="slogan">VIO 论坛 = 团队的技术集散地</div>
        <div class="sub">培训笔记、排障记录、复盘总结都发在这里</div>
        <button class="btn-post" type="button">创作新主题</button>
        <a class="back" :href="mainSite">返回主站 →</a>
      </div>
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
        <h5>社区运行状况</h5>
        <div class="stat">
          <div><span>注册成员</span><b>186</b></div>
          <div><span>主题</span><b>86</b></div>
          <div><span>回复</span><b>512</b></div>
        </div>
      </div>
      <div class="box">
        <h5>最近热门版块</h5>
        <div class="nodes">
          <a v-for="b in BOARDS" :key="b.code" href="#" @click.prevent>{{ b.name }}</a>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: 1fr 270px; gap: 20px; align-items: start; }
.box { background: var(--box); border-radius: var(--radius-box); box-shadow: var(--shadow-box); }

/* 频道行：第一行版块 + 排序，第二行热门标签 */
.tabs { padding: 10px 14px 8px; border-bottom: 1px solid var(--line); }
.tabs a { font-size: 13px; color: var(--body); padding: 3px 9px; border-radius: 4px; margin-right: 2px; }
.tabs a.on { background: var(--tab-dark); color: #FFF; }
.tabs a:not(.on):hover { background: #F2F2F2; color: #000; }
.tabs .right { float: right; }
.tabs .right a { color: var(--fade); font-size: 12.5px; }
.tabs .right a:hover { color: var(--ink); }
.tabs2 { padding: 8px 14px; border-bottom: 1px solid var(--line); font-size: 12.5px; }
.tabs2 a { color: var(--fade); margin-right: 14px; }
.tabs2 a:hover { color: var(--ink); }

/* 主题行：行高约 66px，行间 1px 浅线，无卡片感 */
.cell { display: flex; align-items: center; gap: 13px; padding: 10px 14px; border-bottom: 1px solid var(--line); }
.cell:last-child { border-bottom: none; }
.ava { width: 48px; height: 48px; flex: none; border-radius: var(--radius-ava); object-fit: cover; }
.mid { flex: 1; min-width: 0; }
.t-title { font-size: 16px; line-height: 1.3; font-weight: 400; }
.t-title a { color: var(--ink); }
.t-title a:hover { color: #000; text-decoration: underline; }
.pin { font-size: 11px; color: var(--fade); border: 1px solid #DDD; border-radius: 3px; padding: 0 5px; margin-right: 6px; vertical-align: 1px; }
.t-meta { margin-top: 5px; font-size: 12px; color: var(--fade); }
.node { background: #F5F5F5; color: var(--fade); border-radius: 10px; padding: 1px 8px; margin-right: 8px; }
.node:hover { background: #EBEBEB; color: var(--body); }
.t-meta b { color: var(--meta); font-weight: 600; }
.pill { flex: none; background: var(--pill); color: #FFF; font-size: 12px; border-radius: 10px; padding: 1px 9px; margin-right: 4px; }

/* 右栏 */
.side .box { padding: 12px 14px; margin-bottom: 18px; }
.brand-box .slogan { font-size: 14px; color: var(--ink); font-weight: 600; }
.brand-box .sub { font-size: 12.5px; color: var(--fade); margin: 4px 0 12px; }
.btn-post { font-size: 13px; color: var(--ink); background: var(--box); border: 1px solid #CCC; border-radius: var(--radius-box); padding: 5px 14px; cursor: pointer; }
.btn-post:hover { border-color: #999; }
.brand-box .back { font-size: 12px; color: var(--fade); margin-left: 10px; }
.brand-box .back:hover { color: var(--ink); }
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
.nodes a { display: inline-block; font-size: 12px; background: #F5F5F5; color: var(--meta); border-radius: 10px; padding: 2px 10px; margin: 0 6px 6px 0; }
.nodes a:hover { background: #EBEBEB; color: var(--ink); }
</style>
