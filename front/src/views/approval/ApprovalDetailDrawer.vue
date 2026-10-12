<script setup lang="ts">
/**
 * 审批详情抽屉（P09 + P02–P06 操作）
 * 按钮显隐按进入时的视角（view prop）：TODO=批准/驳回（管理员），MINE=取消（发起人），TARGET=同意/拒绝（目标人）
 * ⚠️ 视角只是身份声明，最终权限一律由后端判定（6002/6003 兜底，规范 §5.3）
 * 高危操作二次确认（FS5）：批准/同意 confirm；驳回/拒绝必填意见（prompt 校验非空）；取消选填
 * 快照 bizPayload 只格式化展示不解析语义（各线快照结构未登记，清单 §7 遗留）
 */
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiAgree, apiApprovalDetail, apiApprove, apiCancel, apiRefuse, apiReject } from '@/api/approval'
import type { ApprovalDetail, ApprovalView } from '@/api/approval'
import { APPROVAL_ACTION_LABEL, APPROVAL_ROLE_LABEL, APPROVAL_STATUS_LABEL, APPROVAL_TYPE_LABEL } from '@/config/labels'

const props = defineProps<{ modelValue: boolean; approvalId: string; view: ApprovalView }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; changed: [] }>()

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => {
  visible.value = v
  if (v) void load()
})
watch(visible, (v) => emit('update:modelValue', v))

const detail = ref<ApprovalDetail | null>(null)
const submitting = ref(false)

async function load() {
  if (!props.approvalId) return
  detail.value = await apiApprovalDetail(props.approvalId)
}

/** 快照格式化（JSON 缩进展示；非 JSON 原样） */
const payloadText = computed(() => {
  const raw = detail.value?.bizPayload ?? ''
  if (!raw) return ''
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
})

/** PENDING 且视角匹配时才出现对应操作区 */
const pending = computed(() => detail.value?.status === 'PENDING')
const canAdmin = computed(() => pending.value && props.view === 'TODO')
const canCancel = computed(() => pending.value && props.view === 'MINE')
const canTarget = computed(() => pending.value && props.view === 'TARGET')

function stampClass(status: string): string {
  if (status === 'PENDING') return 'warn'
  if (status === 'APPROVED') return 'ok'
  if (status === 'REJECTED') return 'bad'
  return 'dim'
}

/** 意见收集：requireReason=true 时必填（驳回/拒绝），否则选填（取消）；批准/同意无意见框 */
async function askComment(kind: 'CONFIRM' | 'OPTIONAL' | 'REQUIRED', title: string, tip: string): Promise<string | null> {
  try {
    if (kind === 'CONFIRM') {
      await ElMessageBox.confirm(tip, title, { type: 'warning' })
      return ''
    }
    const { value } = await ElMessageBox.prompt(tip, title, {
      type: 'warning',
      inputPlaceholder: kind === 'REQUIRED' ? '原因（必填，将展示给申请人）' : '说明（选填）',
      inputValidator: (v: string) => (kind !== 'REQUIRED' || (v && v.trim() ? true : '原因不能为空'))
    })
    return (value ?? '').trim()
  } catch {
    return null
  }
}

type ActionKey = 'APPROVE' | 'REJECT' | 'CANCEL' | 'AGREE' | 'REFUSE'

async function run(action: ActionKey) {
  if (!detail.value || submitting.value) return
  let comment: string | null = null
  if (action === 'APPROVE') comment = await askComment('CONFIRM', '批准确认', '批准该申请？批准后按单据类型回调业务线落地正式数据')
  if (action === 'REJECT') comment = await askComment('REQUIRED', '驳回确认', '填写驳回原因')
  if (action === 'CANCEL') comment = await askComment('OPTIONAL', '取消确认', '取消后单据终结且不通知业务线，可填写取消说明')
  if (action === 'AGREE') comment = await askComment('CONFIRM', '同意确认', '同意该申请？')
  if (action === 'REFUSE') comment = await askComment('REQUIRED', '拒绝确认', '填写拒绝原因')
  if (comment === null) return
  submitting.value = true
  try {
    const id = detail.value.id
    if (action === 'APPROVE') await apiApprove(id, comment)
    if (action === 'REJECT') await apiReject(id, comment)
    if (action === 'CANCEL') await apiCancel(id, comment)
    if (action === 'AGREE') await apiAgree(id, comment)
    if (action === 'REFUSE') await apiRefuse(id, comment)
    ElMessage.success({ APPROVE: '已批准', REJECT: '已驳回', CANCEL: '已取消', AGREE: '已同意', REFUSE: '已拒绝' }[action])
    await load()
    emit('changed')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-drawer v-model="visible" :title="detail ? detail.bizTitle : '审批详情'" size="520px">
    <template v-if="detail">
      <div class="dossier-meta mono" style="margin-bottom: 12px">
        <span class="stamp" :class="stampClass(detail.status)">{{ APPROVAL_STATUS_LABEL[detail.status] ?? detail.status }}</span>
        <span>{{ APPROVAL_TYPE_LABEL[detail.bizType] ?? detail.bizType }}</span>
      </div>
      <dl class="sheet">
        <div><dt>申请人</dt><dd>{{ detail.applicantName }}</dd></div>
        <div v-if="detail.targetMemberName"><dt>目标人</dt><dd>{{ detail.targetMemberName }}</dd></div>
        <div><dt>申请说明</dt><dd>{{ detail.applyReason || '—' }}</dd></div>
        <div><dt>申请时间</dt><dd class="mono">{{ detail.createdAt }}</dd></div>
        <div v-if="detail.handledAt"><dt>处理时间</dt><dd class="mono">{{ detail.handledAt }}</dd></div>
        <div v-if="detail.handleComment"><dt>处理意见</dt><dd>{{ detail.handleComment }}</dd></div>
      </dl>

      <div v-if="payloadText" class="zone">
        <h6>申请快照</h6>
        <pre class="payload mono">{{ payloadText }}</pre>
      </div>

      <div class="zone">
        <h6>处理记录</h6>
        <ul class="flow">
          <li v-for="(r, i) in detail.records" :key="i">
            <div class="flow-line">
              <b>{{ APPROVAL_ACTION_LABEL[r.action] ?? r.action }}</b>
              <span>{{ r.operatorName }} · {{ APPROVAL_ROLE_LABEL[r.operatorRole] ?? r.operatorRole }}</span>
              <span class="mono flow-time">{{ r.createdAt }}</span>
            </div>
            <p v-if="r.commentText" class="flow-comment">{{ r.commentText }}</p>
          </li>
        </ul>
      </div>

      <div v-if="canAdmin" class="zone">
        <h6>管理员处理</h6>
        <button class="btn-ink" :disabled="submitting" @click="run('APPROVE')">批准</button>
        <button class="btn-line" style="margin-left: 8px" :disabled="submitting" @click="run('REJECT')">驳回</button>
      </div>
      <div v-if="canTarget" class="zone">
        <h6>目标人确认</h6>
        <button class="btn-ink" :disabled="submitting" @click="run('AGREE')">同意</button>
        <button class="btn-line" style="margin-left: 8px" :disabled="submitting" @click="run('REFUSE')">拒绝</button>
      </div>
      <div v-if="canCancel" class="zone">
        <h6>发起人操作</h6>
        <button class="btn-line" :disabled="submitting" @click="run('CANCEL')">取消申请</button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.sheet div { display: flex; padding: 7px 0; border-bottom: 1px dashed var(--vio-rule-soft); font-size: 13px; }
.sheet dt { width: 76px; color: var(--vio-ink-3); flex-shrink: 0; }
.sheet dd { color: var(--vio-ink); }
.stamp.dim { color: var(--vio-ink-3); border-color: var(--vio-rule); }
.zone { margin-top: 18px; padding-top: 12px; border-top: 1px solid var(--vio-rule-soft); }
.zone h6 { font-size: 12px; color: var(--vio-ink-3); margin-bottom: 10px; letter-spacing: 0.08em; }
.payload {
  margin: 0; padding: 10px 12px; background: var(--vio-wash);
  font-size: 12px; line-height: 1.7; white-space: pre-wrap; word-break: break-all;
}
.flow { list-style: none; margin: 0; padding: 0; }
.flow li { padding: 8px 0 8px 14px; border-left: 2px solid var(--vio-rule); margin-left: 4px; }
.flow li:first-child { border-left-color: var(--vio-blue-ink); }
.flow-line { display: flex; align-items: center; gap: 10px; font-size: 12.5px; }
.flow-line b { color: var(--vio-ink); }
.flow-line span { color: var(--vio-ink-3); }
.flow-time { margin-left: auto; font-size: 11.5px; }
.flow-comment { margin: 4px 0 0; font-size: 12.5px; color: var(--vio-ink-2); }
</style>
