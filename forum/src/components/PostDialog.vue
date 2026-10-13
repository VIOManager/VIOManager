<script setup lang="ts">
/**
 * 发帖弹窗（全站唯一入口，ForumLayout 顶栏"创作新主题"唤起）
 * TODO(联调-论坛清单)：F06 POST /api/forum/topics，路径与错误码为前端预设
 * ⚠️ 防重复提交（规范 §6 幂等约定的表单侧落地）：
 *    1 UI 层——点击"发布"立即 submitting=true 置灰按钮，连点无效；
 *    2 契约层——X-Idempotency-Key 一张表单一个键：弹窗打开时生成，
 *      网络失败重试继续用同一个键（第一次若已到服务器，重发会被 1005 拦下不产生重复帖），
 *      只有成功发布或重新打开表单才换新键（再发下一帖是新内容）
 */
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { BOARDS } from '@/config/boards'
import { useUserStore } from '@/stores/user'
import { apiCreateTopic } from '@/api/forum'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'created', id: string): void }>()

const user = useUserStore()

const board = ref('EE')
const title = ref('')
const bodyText = ref('')
const tagsText = ref('')
const pinned = ref(false)
const submitting = ref(false)

/**
 * ⚠️ 幂等键：弹窗每次打开新生成；提交失败保留（重试同键防重）；成功后随关闭作废
 */
const formKey = ref('')

function open() {
  board.value = 'EE'
  title.value = ''
  bodyText.value = ''
  tagsText.value = ''
  pinned.value = false
  submitting.value = false
  formKey.value = crypto.randomUUID()
}

watch(
  () => props.modelValue,
  (v) => {
    if (v) open()
  }
)

function close() {
  if (submitting.value) return
  emit('update:modelValue', false)
}

async function submit() {
  if (submitting.value) return
  if (!title.value.trim() || !bodyText.value.trim()) {
    ElMessage.warning('标题和正文都要填')
    return
  }
  // 第一步：立即置灰按钮，拦住手抖连点
  submitting.value = true
  try {
    // 第二步：带 formKey 提交——失败重试复用同键，断网回来不产生重复帖
    const tags: string[] = []
    const parts = tagsText.value.split(/[,，]/)
    for (let i = 0; i < parts.length; i++) {
      if (parts[i].trim()) tags.push(parts[i].trim())
    }
    const res = await apiCreateTopic(
      { board: board.value, title: title.value.trim(), bodyText: bodyText.value.trim(), tags, pinned: pinned.value },
      formKey.value
    )
    ElMessage.success('发布成功')
    emit('created', res.id)
    emit('update:modelValue', false)
  } catch {
    // 网络异常/业务错误：拦截器已弹提示；本地表单内容原样保留，
    // formKey 不换——用户改完再点"发布"仍是同一键，服务器只见一次
    submitting.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="modelValue" class="overlay" @click.self="close">
      <div class="dlg">
        <h3>创作新主题</h3>
        <div class="row">
          <label>版块</label>
          <select v-model="board" :disabled="submitting">
            <option v-for="b in BOARDS" :key="b.code" :value="b.code">{{ b.name }} · {{ b.desc }}</option>
          </select>
        </div>
        <div class="row">
          <label>标题</label>
          <input v-model="title" maxlength="80" placeholder="一句话说清主题（80 字内）" :disabled="submitting">
        </div>
        <div class="row">
          <label>正文</label>
          <textarea v-model="bodyText" rows="7" placeholder="空行分段，段落会在详情页按段渲染" :disabled="submitting"></textarea>
        </div>
        <div class="row">
          <label>标签</label>
          <input v-model="tagsText" placeholder="逗号分隔，如：STM32, 电机控制（可空）" :disabled="submitting">
        </div>
        <div v-if="user.isAdmin" class="row">
          <label>置顶</label>
          <label class="chk"><input type="checkbox" v-model="pinned" :disabled="submitting"> 置顶展示（仅管理员可见此项）</label>
        </div>
        <div class="foot">
          <i>※ 文明发言；发布后修改需管理员操作</i>
          <span>
            <button class="btn" type="button" :disabled="submitting" @click="close">取消</button>
            <button class="btn main" type="button" :disabled="submitting" @click="submit">
              {{ submitting ? '发布中…' : '发布' }}
            </button>
          </span>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.overlay { position: fixed; inset: 0; background: rgba(0, 0, 0, 0.35); display: grid; place-items: center; z-index: 60; }
.dlg { width: 520px; background: var(--box); border-radius: var(--radius-box); box-shadow: var(--shadow-box); padding: 18px 20px; }
.dlg h3 { font-size: 15px; color: var(--ink); font-weight: 600; margin-bottom: 14px; }
.row { display: flex; align-items: flex-start; gap: 12px; margin-bottom: 12px; }
.row > label:first-child { width: 42px; flex: none; font-size: 13px; color: var(--fade); padding-top: 6px; }
.row select, .row input, .row textarea { flex: 1; border: 1px solid var(--line); border-radius: var(--radius-box); padding: 7px 10px; font-size: 13px; font-family: inherit; background: var(--box-soft); color: var(--ink); }
.row select:focus, .row input:focus, .row textarea:focus { outline: none; border-color: #BBB; background: var(--box); }
.row textarea { resize: vertical; line-height: 1.7; }
.chk { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--body); padding-top: 6px; }
.foot { display: flex; align-items: center; border-top: 1px solid var(--line-soft); padding-top: 12px; }
.foot i { flex: 1; font-style: normal; font-size: 12px; color: var(--fade); }
.btn { font-size: 13px; color: var(--ink); background: var(--box); border: 1px solid #CCC; border-radius: var(--radius-box); padding: 5px 16px; cursor: pointer; margin-left: 8px; }
.btn:hover:not(:disabled) { border-color: #999; }
.btn.main { background: var(--tab-dark); color: #FFF; border-color: var(--tab-dark); }
.btn:disabled { opacity: 0.55; cursor: not-allowed; }
</style>
