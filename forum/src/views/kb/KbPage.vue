<script setup lang="ts">
/**
 * 知识库占位页（骨架阶段）
 * 知识库为阶段三模块（架构方案 §6），本站先留入口与版式
 * TODO(阶段三)：分类浏览 / 收藏 / 评论 / 管理员发布编辑置顶
 */
</script>

<template>
  <div class="empty-state">
    <div class="es-compass" aria-hidden="true"></div>
    <b>知识库 · 待开发</b>
    <p>教程、规范、AI 提示词库的沉淀区；优质推送与论坛精华帖经版主推荐后归档至此。</p>
    <p class="annot">※ 知识库为阶段三模块，接口与页面届时单独立项。</p>
  </div>
</template>

<style scoped>
.empty-state { margin-top: 40px; border: 1.5px dashed var(--vio-rule); background: var(--vio-sheet); padding: 56px 30px; text-align: center; }
.es-compass { width: 84px; height: 84px; margin: 0 auto 18px; border: 1px dashed var(--vio-ink-3); border-radius: 50%; position: relative; }
.es-compass::before { content: "+"; position: absolute; inset: 0; display: grid; place-items: center; font-family: var(--vio-mono); color: var(--vio-ink-3); font-size: 18px; }
.empty-state b { display: block; font-family: var(--vio-serif); font-size: 18px; letter-spacing: 0.1em; margin-bottom: 10px; }
.empty-state p { font-size: 13px; color: var(--vio-ink-2); }
.annot { margin-top: 10px; font-family: var(--vio-fangsong); font-size: 12px; color: var(--vio-ink-3); }
</style>
