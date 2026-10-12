<script setup lang="ts">
/**
 * 知识库占位页（骨架阶段）：白盒 + 一行说明 + 阶段三提示（V2EX 范式，废图签装饰）
 * 依据：《论坛V2EX风格重设计-优化方案》§5.5
 * TODO(阶段三)：分类浏览 / 收藏 / 评论 / 管理员发布编辑置顶（架构方案 §6）
 */
</script>

<template>
  <div class="box empty">
    <b>知识库 · 待开发</b>
    <p>教程、规范、AI 提示词库的沉淀区；优质推送与论坛精华帖经版主推荐后归档至此。</p>
    <p class="annot">※ 知识库为阶段三模块，接口与页面届时单独立项。</p>
  </div>
</template>

<style scoped>
.empty { background: var(--box); border-radius: var(--radius-box); box-shadow: var(--shadow-box); padding: 56px 30px; text-align: center; }
.empty b { display: block; font-size: 16px; color: var(--ink); font-weight: 600; margin-bottom: 10px; }
.empty p { font-size: 13px; color: var(--body); }
.annot { margin-top: 10px; font-size: 12px; color: var(--fade); }
</style>
