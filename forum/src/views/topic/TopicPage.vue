<script setup lang="ts">
/**
 * 帖子详情：面包屑 + 主楼（左 73px 大头像）+ 回复楼层 + 回复框沉底 + 右栏
 * 视觉基准：.agentdocs/design-preview/forum-v2ex/topic.html（v3 样张，lzh 已过目）
 * 依据：《论坛V2EX风格重设计-优化方案》§5.3
 * TODO(API)：详情 GET /api/forum/topics/{id}、回复 GET /api/forum/topics/{id}/replies、
 *    点赞/回复写操作带 X-Idempotency-Key（规范 §6）
 * ⚠️ 并发：点赞走 Redis 原子判重（架构方案 §5.3），前端只发请求不做防重判断
 */
import { useRoute } from 'vue-router'
import { avatarOf } from '@/config/avatars'

const route = useRoute()
const topicId = route.params.id

/** 主楼静态演示数据（联调时替换为接口返回） */
const post = {
  title: '赛季电控培训第三期：STM32 HAL 库电机闭环控制速成',
  author: '赵天宇',
  board: '电控',
  publishAt: '2026-10-07 20:15',
  views: '1.2k',
  pinned: true,
  body: [
    '本周五晚 19:30 电控工作室开第三期培训，目标是把电机闭环从"听说过"讲到"能上手"。零基础可来，带电脑。',
    '大纲：编码器采样与定时器输入捕获 → 速度环 PID 现场整定 → 电流环与 M3508 实测波形 → 常见坑（采样噪声、积分饱和、看门狗复位）。',
    '报名在群里接龙，截止本周五 12:00。录屏和例程仓库会后更新到本帖附件。'
  ],
  attach: [
    { name: '培训录屏-第三期.mp4', size: '386 MB', downloads: 45 },
    { name: 'motor-pid-demo.zip', size: '2.1 MB', downloads: 63 }
  ],
  likeCount: 58,
  collectCount: 23
}

/** 楼层静态演示数据 */
const floors = [
  { no: 1, author: '陈子昂', group: '电控组', time: '10-07 20:41', text: '上一期听完把自家车的速度环整定时间从两天压到两小时，这期必到。问下：电流环会讲到 FOC 吗？', like: 12, quote: null as null | { to: string; floor: number; text: string } },
  { no: 2, author: '赵天宇', group: '电控组 · 版主', time: '10-07 20:58', text: '这期只到电流环概念和波形，FOC 放第四期单独讲，先把 PI 整定的手感练出来。', like: 8, quote: { to: '陈子昂', floor: 1, text: '问下：电流环会讲到 FOC 吗？' } },
  { no: 3, author: '李明轩', group: '建模组', time: '10-07 21:32', text: '机械组的能来旁听吗？我们底盘那边也想蹭一下。', like: 3, quote: null },
  { no: 4, author: '孙嘉仪', group: '电控组', time: '10-07 22:10', text: '可以，版主说过不限组。座位有限，早点来。', like: 5, quote: { to: '李明轩', floor: 3, text: '机械组的能来旁听吗？' } }
]

/** 侧栏本版热议 */
const boardHot = [
  { title: 'PCB 电源完整性 Layout 检查清单', author: '陈子昂' },
  { title: '步兵机器人功率限制策略复盘', author: '孙嘉仪' },
  { title: '示波器测纹波的正确姿势', author: '李明轩' }
]
</script>

<template>
  <div>
    <div class="crumb"><router-link to="/">首页</router-link> › <a href="#" @click.prevent>{{ post.board }}</a> › 主题</div>
    <div class="grid">
      <div>
        <!-- 主楼：V2EX 结构 = 左 73px 大头像 + 标题/meta -->
        <div class="box post">
          <div class="p-head">
            <img class="pava" :src="avatarOf(post.author, 73)" :alt="post.author">
            <div class="ph-main">
              <h1><span v-if="post.pinned" class="pin">置顶</span>{{ post.title }}</h1>
              <div class="p-meta"><b>{{ post.author }}</b> · <span class="node">{{ post.board }}</span> · {{ post.publishAt }} · 阅读 {{ post.views }}</div>
            </div>
          </div>
          <div class="p-body">
            <p v-for="(p, i) in post.body" :key="i">{{ p }}</p>
          </div>
          <div class="attach">
            <div v-for="a in post.attach" :key="a.name">
              <a href="#" @click.prevent>{{ a.name }}</a>
              <span class="gray">{{ a.size }} · 下载 {{ a.downloads }}</span>
            </div>
          </div>
          <div class="ops">
            <button type="button">👍 已点赞 {{ post.likeCount }}</button>
            <button type="button">⭐ 收藏 {{ post.collectCount }}</button>
            <button type="button">回复</button>
          </div>
        </div>

        <!-- 回复盒：灰底头行 + 楼层 -->
        <div class="box replies">
          <div class="r-head">24 条回复 · 按时间正序 <a href="#reply">添加新回复 ↓</a></div>
          <div v-for="f in floors" :key="f.no" class="floor">
            <img class="fava" :src="avatarOf(f.author, 48)" :alt="f.author">
            <div class="f-main">
              <div class="f-meta"><b>{{ f.author }}</b>{{ f.group }} · {{ f.time }}<span class="fno">#{{ f.no }}</span></div>
              <div v-if="f.quote" class="quote">@{{ f.quote.to }} #{{ f.quote.floor }}：{{ f.quote.text }}</div>
              <div class="f-text">{{ f.text }}</div>
              <div class="f-ops"><a href="#" @click.prevent>♥ {{ f.like }}</a><a href="#reply">回复</a></div>
            </div>
          </div>
        </div>

        <!-- 回复框沉底 -->
        <div class="box reply" id="reply">
          <h5>添加一条新回复</h5>
          <textarea placeholder="支持 Markdown；回复即留痕，删除需管理员操作"></textarea>
          <div class="r-foot">
            <i>※ 文明发言，排障类回复请尽量附现象、配置与波形</i>
            <button class="btn-post" type="button">回复</button>
          </div>
        </div>
      </div>

      <div class="side">
        <div class="box">
          <h5>关于作者</h5>
          <div class="author">
            <img :src="avatarOf(post.author, 48)" :alt="post.author">
            <div><b>{{ post.author }}</b><div class="gray">电控组 · 版主</div></div>
          </div>
          <div class="stat">
            <div><b>47</b>发帖</div>
            <div><b>812</b>获赞</div>
            <div><b>26</b>被归档</div>
          </div>
        </div>
        <div class="box">
          <h5>本版热议主题</h5>
          <ul class="hot">
            <li v-for="h in boardHot" :key="h.title">
              <img class="hava" :src="avatarOf(h.author, 24)" :alt="h.author">
              <a href="#" @click.prevent>{{ h.title }}</a>
            </li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: 1fr 270px; gap: 20px; align-items: start; }
.box { background: var(--box); border-radius: var(--radius-box); box-shadow: var(--shadow-box); }
.gray { font-size: 12px; color: var(--fade); }
.crumb { font-size: 12.5px; color: var(--fade); margin-bottom: 10px; }
.crumb a:hover { color: var(--ink); }

/* 主楼 */
.post { padding: 18px 20px 14px; margin-bottom: 18px; }
.p-head { display: flex; gap: 14px; }
.pava { width: 73px; height: 73px; flex: none; border-radius: var(--radius-ava); object-fit: cover; }
.ph-main { flex: 1; min-width: 0; }
.post h1 { font-size: 21px; color: var(--ink); line-height: 1.3; font-weight: 500; }
.pin { font-size: 11px; color: var(--fade); border: 1px solid #DDD; border-radius: 3px; padding: 0 5px; margin-right: 6px; vertical-align: 2px; font-weight: 400; }
.p-meta { margin-top: 8px; font-size: 12px; color: var(--fade); }
.node { background: #F5F5F5; color: var(--fade); border-radius: 10px; padding: 1px 8px; }
.p-meta b { color: var(--meta); }
.p-body { margin-top: 16px; font-size: 14px; line-height: 1.7; color: var(--ink); }
.p-body p { margin-bottom: 10px; }
.attach { margin-top: 14px; background: var(--box-soft); border-radius: var(--radius-box); padding: 10px 14px; font-size: 12.5px; }
.attach div { display: flex; justify-content: space-between; padding: 3px 0; }
.attach a:hover { color: var(--ink); }
.ops { margin-top: 16px; display: flex; gap: 8px; }
.ops button { background: #F5F5F5; border: 1px solid var(--line); color: var(--body); font-size: 12.5px; padding: 5px 14px; border-radius: var(--radius-box); cursor: pointer; }
.ops button:hover { background: #EBEBEB; }

/* 楼层 */
.replies { margin-bottom: 18px; }
.r-head { padding: 9px 14px; border-bottom: 1px solid var(--line); font-size: 12.5px; color: var(--fade); background: var(--box-soft); border-radius: var(--radius-box) var(--radius-box) 0 0; display: flex; }
.r-head a { margin-left: auto; }
.r-head a:hover { color: var(--ink); }
.floor { display: flex; gap: 12px; padding: 13px 14px; border-bottom: 1px solid var(--line-soft); }
.floor:last-child { border-bottom: none; }
.fava { width: 48px; height: 48px; flex: none; border-radius: var(--radius-ava); object-fit: cover; }
.f-main { flex: 1; min-width: 0; }
.f-meta { font-size: 12px; color: var(--fade); margin-bottom: 4px; display: flex; align-items: baseline; }
.f-meta b { color: var(--meta); margin-right: 8px; font-size: 13px; }
.fno { margin-left: auto; color: #D0D0D0; font-size: 12px; }
.quote { background: var(--box-soft); border-left: 2px solid var(--line); padding: 6px 10px; font-size: 12.5px; color: var(--fade); border-radius: 2px; margin-bottom: 6px; }
.f-text { font-size: 14px; color: var(--ink); line-height: 1.7; }
.f-ops { margin-top: 5px; font-size: 12px; }
.f-ops a { margin-right: 12px; color: #B0B0B0; }
.f-ops a:hover { color: var(--ink); }

/* 回复框 */
.reply { padding: 14px 18px 16px; }
.reply h5 { font-size: 13px; color: var(--body); margin-bottom: 10px; font-weight: 600; }
.reply textarea { width: 100%; height: 110px; border: 1px solid var(--line); border-radius: var(--radius-box); padding: 10px; font-size: 13px; line-height: 1.7; font-family: inherit; resize: vertical; background: var(--box-soft); color: var(--ink); }
.reply textarea:focus { outline: none; border-color: #CCC; background: var(--box); }
.r-foot { display: flex; align-items: center; margin-top: 10px; }
.r-foot i { font-style: normal; font-size: 12px; color: var(--fade); flex: 1; }
.btn-post { font-size: 13px; color: var(--ink); background: var(--box); border: 1px solid #CCC; border-radius: var(--radius-box); padding: 5px 14px; cursor: pointer; }
.btn-post:hover { border-color: #999; }

/* 右栏 */
.side .box { padding: 12px 14px; margin-bottom: 18px; }
.side h5 { font-size: 12.5px; color: var(--fade); font-weight: 400; border-bottom: 1px solid var(--line-soft); padding-bottom: 8px; margin-bottom: 8px; }
.author { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; }
.author img { width: 48px; height: 48px; border-radius: var(--radius-ava); object-fit: cover; }
.author b { color: var(--ink); font-size: 14px; }
.stat { display: flex; font-size: 12px; color: var(--fade); }
.stat div { flex: 1; text-align: center; }
.stat b { display: block; font-size: 16px; color: var(--body); font-weight: 600; }
.hot { margin: 0; padding: 0; }
.hot li { list-style: none; display: flex; gap: 9px; align-items: flex-start; padding: 7px 0; border-bottom: 1px solid #F4F4F4; }
.hot li:last-child { border: none; }
.hava { width: 24px; height: 24px; flex: none; border-radius: var(--radius-ava); object-fit: cover; margin-top: 1px; }
.hot a { font-size: 12.5px; color: var(--body); line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.hot a:hover { color: #000; }
</style>
