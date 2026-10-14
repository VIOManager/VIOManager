<script setup lang="ts">
/**
 * 人员详情抽屉（A 线 M02 字段裁剪 + M10 完整身份证 + M12 技能编辑）
 * 验证点（方案 PA2）：三种角色看同一人详情，字段差异正确——
 *    管理角色/本人全量；其他学生只见姓名/学院/班级/技能（limited 视图）
 * ⚠️ 安全：字段有无完全以后端返回为准（limited 标记），前端不做越权拼装
 */
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiBindAccount, apiBindPhoto, apiBindSignature, apiDeleteMember, apiMemberDetail, apiMemberIdCard, apiResetAccount, apiUpdateSkills } from '@/api/member'
import type { MemberDetail } from '@/api/member'
import type { UploadResp } from '@/api/file'
import { useDict } from '@/composables/useDict'
import { MEMBER_TYPE_LABEL } from '@/config/labels'
import MaskField from '@/components/MaskField.vue'
import UploadField from '@/components/UploadField.vue'

const visible = defineModel<boolean>({ required: true })
const props = defineProps<{ memberId: string }>()
const emit = defineEmits<{ edit: [id: string]; deleted: [] }>()

const detail = ref<MemberDetail | null>(null)
const loading = ref(false)
const { selectable: skillOptions } = useDict('SKILL')

// 完整身份证查看（M10，走后端审计接口）
const idCardVisible = ref(false)
const fullIdCard = ref('')
const idCardLoading = ref(false)

// 技能编辑态
const editingSkills = ref(false)
const skillDraft = ref<string[]>([])
const savingSkills = ref(false)

// 打开或切换人员都要重取详情（抽屉未关时点另一行，memberId 变化也要刷新）
watch([visible, () => props.memberId], async ([v]) => {
  if (!v || !props.memberId) return
  loading.value = true
  detail.value = null
  editingSkills.value = false
  fullIdCard.value = ''
  idCardVisible.value = false
  try {
    detail.value = await apiMemberDetail(props.memberId)
  } finally {
    loading.value = false
  }
})

async function onViewIdCard() {
  idCardLoading.value = true
  try {
    const resp = await apiMemberIdCard(props.memberId)
    fullIdCard.value = resp.idCard
    idCardVisible.value = true
  } catch {
    // 3010 无权查看等提示已由 http 拦截器弹出
  } finally {
    idCardLoading.value = false
  }
}

function startEditSkills() {
  skillDraft.value = detail.value ? [...detail.value.skills] : []
  editingSkills.value = true
}

async function saveSkills() {
  savingSkills.value = true
  try {
    await apiUpdateSkills(props.memberId, skillDraft.value)
    if (detail.value) detail.value.skills = [...skillDraft.value]
    editingSkills.value = false
    ElMessage.success('技能标签已更新')
  } catch {
    // 提示已由拦截器弹出
  } finally {
    savingSkills.value = false
  }
}

async function onDelete() {
  const name = detail.value?.name ?? ''
  try {
    await ElMessageBox.confirm('确认删除「' + name + '」？仅移除账号与角色，业务记录（材料/成果）保留。', '删除人员', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await apiDeleteMember(props.memberId)
    ElMessage.success('已删除')
    visible.value = false
    emit('deleted')
  } catch {
    // 3003 队长拒删 / 3004 删当前账号 提示已由拦截器弹出
  }
}

// ── 档案附件（M08/M09：文件先走文件服务拿 fileId，再回填档案，覆盖式单图）──
async function onPhoto(f: UploadResp) {
  await apiBindPhoto(props.memberId, f.fileId)
  if (detail.value) detail.value.photoFileId = f.fileId
  ElMessage.success('证件照已更新')
}

async function onSignature(f: UploadResp) {
  await apiBindSignature(props.memberId, f.fileId)
  if (detail.value) detail.value.signatureFileId = f.fileId
  ElMessage.success('电子签名已更新')
}

// ── 登录账号（M13：绑定默认登录名=学工号、初始密码 abc123；重置后回默认密码态）──
const accountBusy = ref(false)

async function onBindAccount() {
  accountBusy.value = true
  try {
    await apiBindAccount(props.memberId)
    if (detail.value) {
      detail.value.hasAccount = true
      detail.value.loginName = detail.value.studentNo ?? ''
    }
    ElMessage.success('账号已绑定，初始密码 abc123（首次登录引导改密）')
  } catch {
    // 提示已由拦截器弹出
  } finally {
    accountBusy.value = false
  }
}

async function onResetPwd() {
  try {
    await ElMessageBox.confirm('确认将「' + (detail.value?.name ?? '') + '」的密码重置为默认 abc123？', '重置密码', {
      confirmButtonText: '重置',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  await apiResetAccount(props.memberId, undefined, true)
  ElMessage.success('密码已重置为 abc123')
}

async function onRenameAccount() {
  let newName = ''
  try {
    const r = await ElMessageBox.prompt('输入新登录名', '改登录名', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValue: detail.value?.loginName ?? ''
    })
    newName = r.value.trim()
  } catch {
    return
  }
  if (!newName) return
  await apiResetAccount(props.memberId, newName)
  if (detail.value) detail.value.loginName = newName
  ElMessage.success('登录名已修改')
}
</script>

<template>
  <el-drawer v-model="visible" :title="detail ? detail.name : '人员详情'" size="460px">
    <p v-if="loading" class="empty-note">加载中…</p>
    <template v-else-if="detail">
      <!-- 受限视角：仅基础字段（后端裁剪结果，前端照渲染） -->
      <template v-if="detail.limited">
        <dl class="kv">
          <div><dt>姓名</dt><dd>{{ detail.name }}</dd></div>
          <div><dt>类型</dt><dd>{{ MEMBER_TYPE_LABEL[detail.memberType] }}</dd></div>
          <div><dt>学院</dt><dd>{{ detail.college }}</dd></div>
          <div><dt>班级</dt><dd>{{ detail.className || '—' }}</dd></div>
          <div><dt>所属组织</dt><dd>{{ detail.orgName || '—' }}</dd></div>
          <div><dt>技能标签</dt><dd>{{ detail.skills.join('、') || '—' }}</dd></div>
        </dl>
        <p class="fs limit-note">其余字段为敏感信息，当前角色无权查看（后端已按权限表裁剪）。</p>
      </template>

      <!-- 全量视角 -->
      <template v-else>
        <dl class="kv">
          <div><dt>学号/工号</dt><dd class="mono">{{ detail.studentNo || '—' }}</dd></div>
          <div><dt>类型</dt><dd>{{ MEMBER_TYPE_LABEL[detail.memberType] }}</dd></div>
          <div><dt>性别 / 民族</dt><dd>{{ detail.gender || '—' }} / {{ detail.nation || '—' }}</dd></div>
          <div><dt>手机号</dt><dd><MaskField :text="detail.phone ?? ''" kind="phone" /></dd></div>
          <div><dt>邮箱</dt><dd>{{ detail.email || '—' }}</dd></div>
          <div><dt>学院</dt><dd>{{ detail.college }}</dd></div>
          <div><dt>专业</dt><dd>{{ detail.major || '—' }}</dd></div>
          <div><dt>班级</dt><dd>{{ detail.className || '—' }}</dd></div>
          <div><dt>所属组织</dt><dd>{{ detail.orgName || '—' }}</dd></div>
          <div><dt>建档时间</dt><dd class="mono">{{ detail.createdAt || '—' }}</dd></div>
        </dl>

        <div class="sec">
          <h6>敏感信息</h6>
          <div class="idcard-line">
            <MaskField :text="detail.idCardLast4 ?? ''" kind="idcard" />
            <button class="btn-act" :disabled="idCardLoading" @click="onViewIdCard">查看完整身份证号（写审计日志）</button>
          </div>
          <p v-if="idCardVisible" class="idcard-full mono">{{ fullIdCard }}</p>
        </div>

        <div class="sec">
          <h6>技能标签 <button class="btn-act" @click="startEditSkills">编辑</button></h6>
          <template v-if="!editingSkills">
            <span v-for="s in detail.skills" :key="s" class="stamp blue" style="margin-right: 6px">{{ s }}</span>
            <span v-if="detail.skills.length === 0" class="fs" style="color: var(--vio-ink-3); font-size: 12px">未标注</span>
          </template>
          <template v-else>
            <el-select v-model="skillDraft" multiple style="width: 100%">
              <el-option v-for="s in skillOptions" :key="s.id" :value="s.label" :label="s.label" />
            </el-select>
            <div style="margin-top: 8px; display: flex; gap: 8px">
              <button class="btn-ink" :disabled="savingSkills" @click="saveSkills">保存</button>
              <button class="btn-line" @click="editingSkills = false">取消</button>
            </div>
          </template>
        </div>

        <div class="sec">
          <h6>档案附件</h6>
          <div class="asset">
            <div class="asset-head">
              <span>证件照</span>
              <span v-if="detail.photoFileId" class="stamp ok">已上传（可覆盖）</span>
            </div>
            <UploadField biz-type="MEMBER_PHOTO" accept=".jpg,.jpeg,.png" :max-mb="5" button-text="上传证件照" tip="jpg/png，5MB 内；覆盖式单图" @uploaded="onPhoto" />
          </div>
          <div class="asset">
            <div class="asset-head">
              <span>电子签名</span>
              <span v-if="detail.signatureFileId" class="stamp ok">已上传（可覆盖）</span>
            </div>
            <UploadField biz-type="MEMBER_SIGNATURE" accept=".jpg,.jpeg,.png" :max-mb="5" button-text="上传电子签名" tip="jpg/png，5MB 内；覆盖式单图" @uploaded="onSignature" />
          </div>
        </div>

        <div class="sec">
          <h6>登录账号</h6>
          <template v-if="detail.hasAccount">
            <p class="acc-line">登录名 <b class="mono">{{ detail.loginName }}</b></p>
            <div style="display: flex; gap: 8px">
              <button class="btn-line" @click="onResetPwd">重置密码</button>
              <button class="btn-line" @click="onRenameAccount">改登录名</button>
            </div>
            <p class="fs hint">重置后密码回到默认 abc123，该成员下次登录会被引导改密</p>
          </template>
          <template v-else>
            <button class="btn-line" :disabled="accountBusy" @click="onBindAccount">绑定账号（登录名默认=学工号，初始密码 abc123）</button>
          </template>
        </div>

        <div class="ops">
          <button class="btn-ink" @click="emit('edit', detail.id)">编辑档案</button>
          <button class="btn-line danger" @click="onDelete">删除</button>
        </div>
      </template>
    </template>
  </el-drawer>
</template>

<style scoped>
.kv { display: grid; grid-template-columns: 1fr 1fr; gap: 12px 20px; }
.kv dt { font-family: var(--vio-fangsong); font-size: 12px; color: var(--vio-ink-3); letter-spacing: 0.1em; }
.kv dd { font-size: 13.5px; color: var(--vio-ink); margin-top: 2px; word-break: break-all; }
.limit-note { margin-top: 16px; font-size: 12px; color: var(--vio-ink-3); }
.sec { margin-top: 18px; border-top: 1px solid var(--vio-rule-soft); padding-top: 12px; }
.sec h6 { font-family: var(--vio-serif); font-size: 13.5px; letter-spacing: 0.12em; margin-bottom: 10px; display: flex; justify-content: space-between; align-items: baseline; }
.idcard-line { display: flex; align-items: center; gap: 10px; }
.idcard-full { margin-top: 8px; padding: 8px 10px; background: var(--vio-wash); font-size: 13px; letter-spacing: 0.1em; }
.asset { margin-bottom: 12px; }
.asset-head { display: flex; align-items: center; gap: 10px; font-size: 13px; margin-bottom: 6px; }
.acc-line { font-size: 13px; margin-bottom: 8px; }
.hint { margin-top: 8px; font-size: 12px; color: var(--vio-ink-3); }
.ops { margin-top: 22px; display: flex; gap: 10px; }
.btn-line.danger { color: var(--vio-seal); border-color: var(--vio-seal); }
.btn-line.danger:hover { background: var(--vio-seal); color: #fff; }
</style>
