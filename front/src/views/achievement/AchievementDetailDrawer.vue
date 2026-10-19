<script setup lang="ts">
/**
 * 成果详情抽屉（PC3，A05/A04）
 * 管理端：待审批成果可 通过/驳回（通过触发 §5 联动）；未通过成果可删除
 * 队长视角：佐证材料覆盖式上传（5007 拦截非队长，后端判定）
 */
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiAchievementDetail, apiDeleteAchievement, apiUploadEvidence } from '@/api/achievement'
import type { AchievementDetail } from '@/api/achievement'
import type { UploadResp } from '@/api/file'
import { useUserStore } from '@/stores/user'
import UploadField from '@/components/UploadField.vue'
import { ACHIEVEMENT_STATUS_LABEL, ACHIEVEMENT_TYPE_LABEL } from '@/config/labels'

const props = defineProps<{ modelValue: boolean; achId: string }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; changed: [] }>()

const user = useUserStore()
const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => {
  visible.value = v
  if (v) void load()
})
watch(visible, (v) => emit('update:modelValue', v))

const detail = ref<AchievementDetail | null>(null)

async function load() {
  if (!props.achId) return
  detail.value = await apiAchievementDetail(props.achId)
}

const isAdmin = () => user.role !== 'STUDENT'

/**
 * 成果审批：后端没有独立的成果审批接口。
 * 按接口清单 §5 口径，学生提交成果（A03）会创建一条 ACHIEVEMENT_SUBMIT 审批单，
 * 管理员在「审批中心」批准/驳回，批准回调里才置 APPROVED 并触发材料侧 §5 联动
 * （原实现直连 POST /api/achievements/{id}/review，后端无此路由，联调必 404）。
 */
async function onReview(action: 'APPROVE' | 'REJECT') {
  ElMessage.info(
    action === 'APPROVE'
      ? '成果审批请到「审批中心」处理对应的 ACHIEVEMENT_SUBMIT 审批单；批准后自动生效并新增佐证必交项'
      : '成果驳回请到「审批中心」处理对应的 ACHIEVEMENT_SUBMIT 审批单'
  )
}

/** 佐证覆盖上传（仅队长，按钮显隐由 viewerIsLeader 驱动） */
async function onEvidence(file: UploadResp) {
  if (!detail.value) return
  await apiUploadEvidence(detail.value.id, file.fileId, file.originalName)
  ElMessage.success('佐证材料已更新（覆盖式）')
  await load()
  emit('changed')
}

async function onDelete() {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm('删除该未通过成果？', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await apiDeleteAchievement(detail.value.id)
  ElMessage.success('已删除')
  visible.value = false
  emit('changed')
}
</script>

<template>
  <el-drawer v-model="visible" :title="detail ? detail.name : '成果详情'" size="480px">
    <template v-if="detail">
      <div class="dossier-meta mono" style="margin-bottom: 12px">
        <span class="stamp" :class="detail.status === 'APPROVED' ? 'ok' : detail.status === 'PENDING' ? 'warn' : 'bad'">{{ ACHIEVEMENT_STATUS_LABEL[detail.status] }}</span>
        <span>{{ ACHIEVEMENT_TYPE_LABEL[detail.type] }}</span>
      </div>
      <dl class="sheet">
        <div><dt>队伍</dt><dd>{{ detail.teamName }}</dd></div>
        <div><dt>级别</dt><dd>{{ detail.level }}</dd></div>
        <div><dt>获奖时间</dt><dd class="mono">{{ detail.achievedAt }}</dd></div>
        <div><dt>提交人</dt><dd>{{ detail.submittedBy }}</dd></div>
        <div><dt>提交时间</dt><dd class="mono">{{ detail.createdAt }}</dd></div>
        <div><dt>佐证材料</dt><dd>{{ detail.evidenceFileName || '未上传' }}</dd></div>
      </dl>
      <p v-if="detail.description" class="fs desc">{{ detail.description }}</p>

      <div class="zone">
        <h6>佐证材料（覆盖式上传）</h6>
        <UploadField
          v-if="detail.viewerIsLeader"
          biz-type="ACHIEVEMENT_EVIDENCE"
          accept=".pdf,.jpg,.png,.zip"
          :max-mb="20"
          button-text="上传/覆盖佐证"
          @uploaded="onEvidence"
        />
        <p v-else class="fs" style="font-size: 12px; color: var(--vio-ink-3)">仅当前队长可上传佐证材料</p>
      </div>

      <div v-if="isAdmin() && detail.status === 'PENDING'" class="zone">
        <h6>审批操作</h6>
        <button class="btn-ink" @click="onReview('APPROVE')">通过</button>
        <button class="btn-line" style="margin-left: 8px" @click="onReview('REJECT')">驳回</button>
      </div>
      <div v-if="isAdmin() && detail.status !== 'APPROVED'" class="zone">
        <button class="btn-line" @click="onDelete">删除该成果</button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.sheet div { display: flex; padding: 7px 0; border-bottom: 1px dashed var(--vio-rule-soft); font-size: 13px; }
.sheet dt { width: 76px; color: var(--vio-ink-3); flex-shrink: 0; }
.sheet dd { color: var(--vio-ink); }
.stamp.bad { color: var(--vio-danger); border-color: var(--vio-danger); }
.desc { margin-top: 12px; padding: 10px 12px; background: var(--vio-wash); font-size: 12.5px; line-height: 1.8; }
.zone { margin-top: 18px; padding-top: 12px; border-top: 1px solid var(--vio-rule-soft); }
.zone h6 { font-size: 12px; color: var(--vio-ink-3); margin-bottom: 10px; letter-spacing: 0.08em; }
</style>
