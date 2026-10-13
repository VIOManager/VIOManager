<script setup lang="ts">
/**
 * 知识库页（/kb，三端，KB01–KB08 暂定接口）
 * 分类 tab 走字典 DR01（KB_CATEGORY，系统配置里维护）；置顶优先排序；管理员可发布
 * PRD §3.14：浏览/收藏/评论全角色；发布/编辑/置顶/删除仅管理员
 */
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiCreateKbItem, apiKbItems } from '@/api/kb'
import type { KbItem } from '@/api/kb'
import { apiDictItems } from '@/api/dict'
import { KB_SOURCE_LABEL } from '@/config/labels'
import { useUserStore } from '@/stores/user'
import KbDetailDrawer from './KbDetailDrawer.vue'

const user = useUserStore()
const isAdmin = user.role !== 'STUDENT'

const list = ref<KbItem[]>([])
const categories = ref<string[]>([])
const category = ref('')
const keyword = ref('')
const loading = ref(false)

const drawerVisible = ref(false)
const detailId = ref('')

async function load() {
  loading.value = true
  try {
    list.value = await apiKbItems({ category: category.value || undefined, keyword: keyword.value || undefined })
  } finally {
    loading.value = false
  }
}

function openDetail(id: string) {
  detailId.value = id
  drawerVisible.value = true
}

// ── 发布（管理员）──
const formVisible = ref(false)
const form = ref({ title: '', category: '', summary: '', content: '', sourceType: 'ADMIN_UPLOAD', linkUrl: '' })
const submitting = ref(false)

async function submit() {
  if (submitting.value) return
  if (!form.value.title.trim() || !form.value.category) {
    ElMessage.warning('标题与分类不能为空')
    return
  }
  submitting.value = true
  try {
    await apiCreateKbItem(form.value)
    ElMessage.success('已发布')
    formVisible.value = false
    form.value = { title: '', category: '', summary: '', content: '', sourceType: 'ADMIN_UPLOAD', linkUrl: '' }
    await load()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  const dict = await apiDictItems('KB_CATEGORY')
  categories.value = dict.map((d) => d.label)
  await load()
})
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>知识库</h3>
      <div class="dossier-meta mono">
        <span>教程 · 规范 · AI 提示词沉淀区</span>
      </div>
    </div>

    <div class="vtabs">
      <button class="vtab" :class="{ active: category === '' }" @click="category = ''; load()">全部</button>
      <button v-for="c in categories" :key="c" class="vtab" :class="{ active: category === c }" @click="category = c; load()">{{ c }}</button>
    </div>

    <div class="fbar">
      <input v-model="keyword" class="input-line" style="width: 200px" placeholder="搜索标题 / 摘要" @keyup.enter="load" />
      <button class="btn-line" @click="load">查询</button>
      <span style="flex: 1"></span>
      <button v-if="isAdmin" class="btn-ink" @click="formVisible = true">发布内容</button>
    </div>

    <div class="kb-grid">
      <div v-for="it in list" :key="it.id" class="kb-card" @click="openDetail(it.id)">
        <div class="kb-top">
          <span class="kb-cat">{{ it.category }}</span>
          <span v-if="it.pinned" class="kb-pin mono">置顶</span>
        </div>
        <h4>{{ it.title }}</h4>
        <p>{{ it.summary }}</p>
        <div class="kb-meta mono">
          <span>{{ KB_SOURCE_LABEL[it.sourceType] }}</span>
          <span>阅 {{ it.viewCount }} · 藏 {{ it.favoriteCount }} · 评 {{ it.commentCount }}</span>
        </div>
      </div>
    </div>
    <p v-if="!loading && list.length === 0" class="empty-note">该分类暂无内容</p>

    <el-dialog v-model="formVisible" title="发布知识库内容" width="520px">
      <div class="form-col">
        <label>标题</label>
        <input v-model="form.title" class="input-line" />
        <label>分类（系统配置字典维护）</label>
        <select v-model="form.category" class="input-line">
          <option value="">选择分类</option>
          <option v-for="c in categories" :key="c" :value="c">{{ c }}</option>
        </select>
        <label>摘要（列表卡片展示）</label>
        <textarea v-model="form.summary" class="input-line" rows="2"></textarea>
        <label>正文</label>
        <textarea v-model="form.content" class="input-line" rows="6"></textarea>
        <label>来源 / 外部链接（选填）</label>
        <div style="display: flex; gap: 8px">
          <select v-model="form.sourceType" class="input-line" style="width: 140px">
            <option v-for="(label, v) in KB_SOURCE_LABEL" :key="v" :value="v">{{ label }}</option>
          </select>
          <input v-model="form.linkUrl" class="input-line" style="flex: 1" placeholder="https://…（来源为外部链接时填）" />
        </div>
      </div>
      <template #footer>
        <button class="btn-line" @click="formVisible = false">取消</button>
        <button class="btn-ink" :disabled="submitting" @click="submit">发布</button>
      </template>
    </el-dialog>

    <KbDetailDrawer v-model="drawerVisible" :kb-id="detailId" @changed="load" />
  </div>
</template>

<style scoped>
.vtabs { display: flex; align-items: center; gap: 8px; margin-bottom: 14px; flex-wrap: wrap; }
.vtab {
  padding: 7px 18px; border: 1px solid var(--vio-rule); background: var(--vio-sheet);
  color: var(--vio-ink-2); font-size: 13px; cursor: pointer; transition: all 0.2s var(--vio-ease);
}
.vtab.active { border-color: var(--vio-ink); color: var(--vio-ink); font-weight: 700; box-shadow: inset 0 -2px 0 var(--vio-ink); }
.kb-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 14px; }
.kb-card { background: var(--vio-sheet); border: 1px solid var(--vio-rule); padding: 16px 18px; cursor: pointer; transition: border-color 0.2s var(--vio-ease); }
.kb-card:hover { border-color: var(--vio-blue-ink); }
.kb-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.kb-cat { font-size: 11px; color: var(--vio-blue-ink); border: 1px solid var(--vio-blue-ink); padding: 1px 8px; }
.kb-pin { font-size: 10px; color: var(--vio-seal); border: 1px solid var(--vio-seal); padding: 1px 6px; }
.kb-card h4 { font-size: 15px; color: var(--vio-ink); margin-bottom: 6px; font-weight: 700; }
.kb-card p { font-size: 12.5px; color: var(--vio-ink-2); line-height: 1.7; margin-bottom: 10px; }
.kb-meta { display: flex; justify-content: space-between; font-size: 11px; color: var(--vio-ink-3); }
.form-col { display: flex; flex-direction: column; gap: 6px; }
.form-col label { font-size: 12px; color: var(--vio-ink-3); margin-top: 6px; }
</style>
