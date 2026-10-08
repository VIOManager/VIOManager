<script setup lang="ts">
/**
 * 帖子详情：主楼 + 楼层回复（回复某人带引用条）+ 快速回复框
 * 设计稿 design-preview/forum-site/topic.html
 * TODO(API)：详情 GET /api/forum/topics/{id}、回复 GET /api/forum/topics/{id}/replies、
 *    点赞/回复写操作带 X-Idempotency-Key（规范 §6）
 * ⚠️ 并发：点赞走 Redis 原子判重（架构方案 §5.3），前端只发请求不做防重判断
 */
import { useRoute } from 'vue-router'

const route = useRoute()
const topicId = route.params.id

const floors = [
  { no: 1, author: '陈子昂', group: '电控组', time: '10-07 20:41', text: '上一期听完把自家车的速度环整定时间从两天压到两小时，这期必到。问下：电流环会讲到 FOC 吗？', like: 12, quote: null as null | { to: string; floor: number; text: string } },
  { no: 2, author: '赵天宇', group: '电控组 · 版主', time: '10-07 20:58', text: '这期只到电流环概念和波形，FOC 放第四期单独讲，先把 PI 整定的手感练出来。', like: 8, quote: { to: '陈子昂', floor: 1, text: '问下：电流环会讲到 FOC 吗？' } },
  { no: 3, author: '李明轩', group: '建模组', time: '10-07 21:32', text: '机械组的能来旁听吗？我们底盘那边也想蹭一下。', like: 3, quote: null },
  { no: 4, author: '孙嘉仪', group: '电控组', time: '10-07 22:10', text: '可以，版主说过不限组。座位有限，早点来。', like: 5, quote: { to: '李明轩', floor: 3, text: '机械组的能来旁听吗？' } }
]
</script>

<template>
  <div class="topic-grid">
    <div>
      <div class="crumb-bar"><router-link to="/">← 返回</router-link> / 电控版块 / 帖子详情 <span class="mono">· TOPIC-{{ topicId }}</span></div>

      <article class="post-main">
        <div class="pm-head">
          <h1><span class="pintag">置顶</span>赛季电控培训第三期：STM32 HAL 库电机闭环控制速成</h1>
          <div class="pm-meta">
            <i class="ava">赵</i><b>赵天宇</b> · 电控组 · 版主
            <span class="mono">发布于 2026-10-07 20:15</span>
            <span class="mono">阅读 1.2k</span>
          </div>
        </div>
        <div class="pm-body">
          <p>本周五晚 19:30 电控工作室开第三期培训，目标是把电机闭环从"听说过"讲到"能上手"。零基础可来，带电脑。</p>
          <p>大纲：编码器采样与定时器输入捕获 → 速度环 PID 现场整定 → 电流环与 M3508 实测波形 → 常见坑（采样噪声、积分饱和、看门狗复位）。</p>
          <p>报名在群里接龙，截止本周五 12:00。录屏和例程仓库会后更新到本帖附件。</p>
        </div>
        <div class="attach">
          <h6>附件（2）</h6>
          <div><span>培训录屏-第三期.mp4</span><span class="mono">386 MB · 下载 45</span></div>
          <div><span>motor-pid-demo.zip</span><span class="mono">2.1 MB · 下载 63</span></div>
        </div>
        <div class="pm-actions">
          <button class="act liked" type="button">已点赞 <b class="mono">58</b></button>
          <button class="act" type="button">收藏 <b class="mono">23</b></button>
          <button class="act" type="button">回复</button>
        </div>
      </article>

      <div class="section-head">
        <h2>回复<i>24 FLOORS</i></h2>
        <p>按时间正序 · 回复某人自动带引用</p>
      </div>

      <div v-for="f in floors" :key="f.no" class="floor">
        <div class="fno"><div class="fava">{{ f.author.slice(0, 1) }}</div>#{{ f.no }}</div>
        <div class="f-body">
          <div class="f-meta"><b>{{ f.author }}</b>{{ f.group }} · <span class="mono">{{ f.time }}</span></div>
          <div v-if="f.quote" class="quote"><i>@{{ f.quote.to }} #{{ f.quote.floor }}</i>{{ f.quote.text }}</div>
          <p>{{ f.text }}</p>
          <div class="f-ops"><a href="#" @click.prevent>点赞 {{ f.like }}</a><a href="#" @click.prevent>回复</a></div>
        </div>
      </div>

      <div class="reply-box">
        <h5>写下你的回复</h5>
        <textarea placeholder="支持 Markdown；回复即留痕，删除需管理员操作"></textarea>
        <div class="rb-foot">
          <i>※ 文明发言，排障类回复请尽量附现象、配置与波形</i>
          <button class="btn-ink" type="button">提交回复</button>
        </div>
      </div>
    </div>

    <aside class="rail">
      <div class="rail-card">
        <h5>关于作者</h5>
        <div class="author-row">
          <i class="ava big">赵</i>
          <div><b>赵天宇</b><div class="fs dim">电控组 · 版主</div></div>
        </div>
        <div class="stat-grid">
          <div><b>47</b><span>发帖</span></div>
          <div><b>812</b><span>获赞</span></div>
          <div><b>26</b><span>被归档</span></div>
        </div>
      </div>
      <div class="rail-card">
        <h5>本版热帖</h5>
        <ol class="hotrank">
          <li><b>1</b><span>PCB 电源完整性 Layout 检查清单</span><em>690</em></li>
          <li><b>2</b><span>步兵机器人功率限制策略复盘</span><em>577</em></li>
          <li><b>3</b><span>示波器测纹波的正确姿势</span><em>441</em></li>
        </ol>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.topic-grid { display: grid; grid-template-columns: 1fr 300px; gap: 26px; }
.crumb-bar { font-size: 12.5px; color: var(--vio-ink-3); margin-bottom: 14px; }
.post-main { background: var(--vio-sheet); border: 1px solid var(--vio-ink); box-shadow: 5px 5px 0 rgba(29, 58, 143, 0.12); }
.pm-head { padding: 22px 26px 16px; border-bottom: 1px solid var(--vio-ink); }
.pm-head h1 { font-family: var(--vio-serif); font-size: 24px; letter-spacing: 0.04em; line-height: 1.5; font-weight: 700; }
.pintag { font-size: 11px; color: var(--vio-seal); border: 1.5px solid var(--vio-seal); padding: 1px 9px; margin-right: 10px; letter-spacing: 0.12em; vertical-align: 3px; }
.pm-meta { display: flex; align-items: center; gap: 10px; margin-top: 12px; font-size: 12.5px; color: var(--vio-ink-3); }
.pm-meta b { color: var(--vio-ink); }
.ava { display: inline-grid; place-items: center; width: 28px; height: 28px; border-radius: 50%; background: var(--vio-wash); color: var(--vio-blue-ink); font-size: 12px; font-weight: 700; font-style: normal; font-family: var(--vio-serif); border: 1px solid var(--vio-rule); }
.ava.big { width: 40px; height: 40px; font-size: 15px; }
.pm-body { padding: 20px 26px; font-size: 14.5px; line-height: 2.05; }
.pm-body p { margin-bottom: 12px; }
.attach { margin: 0 26px 20px; border: 1px dashed var(--vio-rule); padding: 12px 16px; }
.attach h6 { font-family: var(--vio-fangsong); font-size: 12px; color: var(--vio-ink-3); letter-spacing: 0.12em; margin-bottom: 8px; }
.attach div { display: flex; justify-content: space-between; font-family: var(--vio-mono); font-size: 12px; color: var(--vio-ink-2); padding: 4px 0; }
.pm-actions { display: flex; gap: 12px; padding: 14px 26px 18px; border-top: 1px solid var(--vio-rule-soft); }
.act { display: inline-flex; align-items: center; gap: 7px; font-size: 12.5px; color: var(--vio-ink-2); border: 1px solid var(--vio-rule); background: none; padding: 6px 16px; border-radius: 0; transition: all 0.2s var(--vio-ease); }
.act:hover { border-color: var(--vio-seal); color: var(--vio-seal); }
.act.liked { color: var(--vio-seal); border-color: var(--vio-seal); background: #FBEFED; }
.section-head { display: flex; align-items: baseline; gap: 16px; padding-bottom: 12px; border-bottom: 1.5px solid var(--vio-ink); margin: 26px 0 4px; }
.section-head h2 { font-family: var(--vio-serif); font-size: 18px; letter-spacing: 0.16em; font-weight: 700; }
.section-head h2 i { font-style: normal; font-family: var(--vio-mono); font-size: 10px; color: var(--vio-ink-3); letter-spacing: 0.18em; margin-left: 10px; }
.section-head p { font-size: 12px; color: var(--vio-ink-3); font-family: var(--vio-fangsong); flex: 1; }
.floor { display: grid; grid-template-columns: 44px 1fr; gap: 14px; padding: 18px 6px; border-bottom: 1px solid var(--vio-rule-soft); }
.fno { font-family: var(--vio-mono); font-size: 11px; color: var(--vio-ink-3); text-align: center; }
.fava { width: 36px; height: 36px; margin: 0 auto 6px; border-radius: 50%; background: var(--vio-wash); color: var(--vio-blue-ink); display: grid; place-items: center; font-size: 12px; font-weight: 700; font-family: var(--vio-serif); border: 1px solid var(--vio-rule); }
.f-meta { font-size: 12px; color: var(--vio-ink-3); margin-bottom: 7px; }
.f-meta b { color: var(--vio-ink); font-size: 13px; margin-right: 8px; }
.quote { border-left: 3px solid var(--vio-rule); background: var(--vio-wash); padding: 7px 12px; font-size: 12.5px; color: var(--vio-ink-2); margin-bottom: 8px; }
.quote i { font-style: normal; font-family: var(--vio-mono); font-size: 11px; color: var(--vio-blue-ink); margin-right: 6px; }
.f-body p { font-size: 13.5px; line-height: 1.95; }
.f-ops { margin-top: 8px; display: flex; gap: 14px; font-size: 12px; }
.f-ops a { color: var(--vio-ink-3); font-family: var(--vio-mono); }
.f-ops a:hover { color: var(--vio-seal); }
.reply-box { margin-top: 20px; background: var(--vio-sheet); border: 1px solid var(--vio-rule); padding: 18px 22px; }
.reply-box h5 { font-family: var(--vio-serif); font-size: 15px; letter-spacing: 0.14em; margin-bottom: 12px; }
.reply-box textarea { width: 100%; border: 1px solid var(--vio-rule); padding: 10px 12px; font-size: 13.5px; font-family: var(--vio-body); resize: vertical; min-height: 84px; border-radius: 0; }
.reply-box textarea:focus { outline: none; border-color: var(--vio-blue-ink); }
.rb-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 10px; }
.rb-foot i { font-style: normal; font-family: var(--vio-fangsong); font-size: 11.5px; color: var(--vio-ink-3); }
.btn-ink { padding: 8px 22px; background: var(--vio-ink); color: var(--vio-paper); border: none; font-size: 13px; letter-spacing: 0.18em; text-indent: 0.18em; font-family: var(--vio-serif); font-weight: 700; transition: background 0.25s var(--vio-ease); }
.btn-ink:hover { background: var(--vio-blue-ink); }
.rail { display: flex; flex-direction: column; gap: 16px; }
.rail-card { background: var(--vio-sheet); border: 1px solid var(--vio-rule); padding: 16px 18px; }
.rail-card h5 { font-family: var(--vio-serif); font-size: 14.5px; letter-spacing: 0.16em; font-weight: 700; padding-bottom: 9px; border-bottom: 1px solid var(--vio-ink); margin-bottom: 10px; }
.author-row { display: flex; gap: 12px; align-items: center; margin-bottom: 12px; }
.dim { font-size: 11.5px; color: var(--vio-ink-3); }
.stat-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; text-align: center; }
.stat-grid div { border-right: 1px solid var(--vio-rule-soft); padding: 4px 0; }
.stat-grid div:last-child { border-right: none; }
.stat-grid b { display: block; font-family: var(--vio-disp-latin); font-size: 21px; color: var(--vio-blue-ink); font-variant-numeric: tabular-nums; }
.stat-grid span { font-size: 10.5px; color: var(--vio-ink-3); }
.hotrank { list-style: none; }
.hotrank li { display: flex; align-items: baseline; gap: 10px; padding: 8px 0; border-bottom: 1px dotted var(--vio-rule); font-size: 12.5px; }
.hotrank li:last-child { border-bottom: none; }
.hotrank b { font-family: var(--vio-mono); font-size: 12px; color: var(--vio-ink-3); width: 15px; flex: none; }
.hotrank li:first-child b { color: var(--vio-seal); }
.hotrank span { flex: 1; color: var(--vio-ink-2); line-height: 1.55; }
.hotrank em { font-style: normal; font-family: var(--vio-mono); color: var(--vio-blue-ink); font-size: 12px; font-weight: 700; }
</style>
