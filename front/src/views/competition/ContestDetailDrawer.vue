<script setup lang="ts">
/**
 * 比赛详情抽屉（C02 + C05/C06/C07–C09）
 * 核心展示：生效规则表（三级继承合并 + 来源标注：模板默认/比赛覆盖/赛道覆盖）
 * 状态流转：按序流转按钮（下一步）；DRAFT 删除；发布前检查失败由拦截器弹 4001 明细
 * ⚠️ 安全：流转/删除只是入口，判定全在后端（4001/4011）
 */
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiContestDetail, apiContestStatus, apiDeleteContest, apiDeleteTrack } from '@/api/contest'
import type { ContestDetail, ContestStatus, ContestTrack } from '@/api/contest'
import { CONTEST_STATUS_LABEL, RULE_KEY_LABEL, RULE_SOURCE_LABEL, ruleValueText } from '@/config/labels'
import TrackFormDialog from './TrackFormDialog.vue'

const visible = defineModel<boolean>({ required: true })
const props = defineProps<{ contestId: string }>()
const emit = defineEmits<{ changed: []; edit: [id: string] }>()

const detail = ref<ContestDetail | null>(null)
const loading = ref(false)
const trackDialogVisible = ref(false)
const editingTrack = ref<ContestTrack | null>(null)

/** 状态机下一步（与清单 §3 一致）；ARCHIVED 为终态 */
const NEXT_STATUS: Record<string, ContestStatus | null> = {
  DRAFT: 'PUBLISHED',
  PUBLISHED: 'REGISTERING',
  REGISTERING: 'RUNNING',
  RUNNING: 'FINISHED',
  FINISHED: 'ARCHIVED',
  ARCHIVED: null
}

watch([visible, () => props.contestId], async ([v]) => {
  if (!v || !props.contestId) return
  loading.value = true
  try {
    detail.value = await apiContestDetail(props.contestId)
  } finally {
    loading.value = false
  }
})

async function onFlow() {
  const d = detail.value
  if (!d) return
  const next = NEXT_STATUS[d.status]
  if (!next) return
  const label = CONTEST_STATUS_LABEL[next]
  try {
    await ElMessageBox.confirm(
      next === 'PUBLISHED' ? '发布前将检查：基础信息/规则/赛道/材料要求。确认流转为「' + label + '」？' : '确认将状态流转为「' + label + '」？',
      '状态流转',
      { confirmButtonText: '流转', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await apiContestStatus(d.id, next)
    ElMessage.success('已流转为「' + label + '」')
    detail.value = await apiContestDetail(d.id)
    emit('changed')
  } catch {
    // 4001 发布前检查缺项 / 4011 不可跳转 提示已由拦截器弹出
  }
}

async function onDelete() {
  const d = detail.value
  if (!d) return
  try {
    await ElMessageBox.confirm('确认删除比赛「' + d.name + '」？仅草稿可删。', '删除比赛', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch {
    return
  }
  try {
    await apiDeleteContest(d.id)
    ElMessage.success('已删除')
    visible.value = false
    emit('changed')
  } catch {
    // 4001 提示已由拦截器弹出
  }
}

function openAddTrack() {
  editingTrack.value = null
  trackDialogVisible.value = true
}

function openEditTrack(t: ContestTrack) {
  editingTrack.value = t
  trackDialogVisible.value = true
}

async function onDeleteTrack(t: ContestTrack) {
  try {
    await ElMessageBox.confirm('确认删除赛道「' + t.name + '」？', '删除赛道', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch {
    return
  }
  try {
    await apiDeleteTrack(props.contestId, t.id)
    ElMessage.success('赛道已删除')
    detail.value = await apiContestDetail(props.contestId)
  } catch {
    // 有赛队挂载拒删提示已由拦截器弹出
  }
}

async function reloadDetail() {
  detail.value = await apiContestDetail(props.contestId)
}

/** 赛道覆盖摘要：列出被覆盖的键 */
function overrideSummary(t: ContestTrack): string {
  const keys = Object.keys(t.overrideRules)
  if (keys.length === 0) return '跟随比赛规则'
  return keys.map((k) => RULE_KEY_LABEL[k] + '=' + ruleValueText(t.overrideRules[k])).join('，')
}
</script>

<template>
  <el-drawer v-model="visible" :title="detail ? detail.name : '比赛详情'" size="560px">
    <p v-if="loading" class="empty-note">加载中…</p>
    <template v-else-if="detail">
      <dl class="kv">
        <div><dt>状态</dt><dd><span class="stamp blue">{{ CONTEST_STATUS_LABEL[detail.status] }}</span></dd></div>
        <div><dt>学期</dt><dd>{{ detail.semesterName }}</dd></div>
        <div><dt>模板</dt><dd>{{ detail.templateName }}</dd></div>
        <div><dt>联系人</dt><dd>{{ detail.contact || '—' }}</dd></div>
        <div><dt>报名起止</dt><dd class="mono">{{ detail.signupStart || '—' }} ~ {{ detail.signupEnd || '—' }}</dd></div>
        <div><dt>比赛起止</dt><dd class="mono">{{ detail.contestStart || '—' }} ~ {{ detail.contestEnd || '—' }}</dd></div>
      </dl>
      <p v-if="detail.description" class="fs desc">{{ detail.description }}</p>

      <div class="ops">
        <button class="btn-line" @click="emit('edit', detail.id)">编辑</button>
        <button v-if="NEXT_STATUS[detail.status]" class="btn-ink" @click="onFlow">
          流转为「{{ CONTEST_STATUS_LABEL[NEXT_STATUS[detail.status]!] }}」
        </button>
        <button v-if="detail.status === 'DRAFT'" class="btn-line danger" @click="onDelete">删除草稿</button>
      </div>

      <div class="sec">
        <h6>生效规则（比赛级）</h6>
        <table class="ledger">
          <thead><tr><th>规则项</th><th>生效值</th><th>来源</th></tr></thead>
          <tbody>
            <tr v-for="r in detail.rules" :key="r.key">
              <td>{{ RULE_KEY_LABEL[r.key] }}</td>
              <td class="mono">{{ ruleValueText(r.value) }}</td>
              <td><span class="stamp" :class="r.source === 'TEMPLATE' ? 'dim' : r.source === 'CONTEST' ? 'blue' : 'ok'">{{ RULE_SOURCE_LABEL[r.source] }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="sec">
        <h6>赛道 <button class="btn-act" @click="openAddTrack">添加赛道</button></h6>
        <table class="ledger">
          <thead><tr><th>名称</th><th>编码</th><th>规则覆盖</th><th class="num">赛队</th><th style="width: 110px">操作</th></tr></thead>
          <tbody>
            <tr v-for="t in detail.tracks" :key="t.id">
              <td style="color: var(--vio-ink); font-weight: 600">{{ t.name }}</td>
              <td class="mono">{{ t.code || '—' }}</td>
              <td class="fs" style="font-size: 12px">{{ overrideSummary(t) }}</td>
              <td class="num mono">{{ t.teamCount }}</td>
              <td class="nw">
                <button class="btn-act" @click="openEditTrack(t)">编辑</button>
                <button class="btn-act danger" @click="onDeleteTrack(t)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-if="detail.tracks.length === 0" class="empty-note">未配置赛道（发布前检查会拦截）</p>
      </div>
    </template>

    <TrackFormDialog v-model="trackDialogVisible" :contest-id="props.contestId" :track="editingTrack" @saved="reloadDetail" />
  </el-drawer>
</template>

<style scoped>
.kv { display: grid; grid-template-columns: 1fr 1fr; gap: 12px 20px; }
.kv dt { font-family: var(--vio-fangsong); font-size: 12px; color: var(--vio-ink-3); letter-spacing: 0.1em; }
.kv dd { font-size: 13.5px; color: var(--vio-ink); margin-top: 2px; }
.desc { font-size: 12.5px; color: var(--vio-ink-3); margin: 10px 0 0; }
.ops { display: flex; gap: 10px; margin: 16px 0 4px; flex-wrap: wrap; }
.sec { margin-top: 18px; border-top: 1px solid var(--vio-rule-soft); padding-top: 12px; }
.sec h6 { font-family: var(--vio-serif); font-size: 13.5px; letter-spacing: 0.12em; margin-bottom: 10px; display: flex; justify-content: space-between; align-items: baseline; }
.btn-line.danger { color: var(--vio-seal); border-color: var(--vio-seal); }
.btn-line.danger:hover { background: var(--vio-seal); color: #fff; }
</style>
