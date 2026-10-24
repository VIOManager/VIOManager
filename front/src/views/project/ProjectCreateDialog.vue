<script setup lang="ts">
/**
 * 新建项目弹窗（管理端 /project 与学生端 /my/project 共用）
 * 所有人=当前用户；管理员额外可代建（ownerMemberId 帮成员创建，2026-10-16 需求）
 * 关联比赛选填：挂载池按 contest_id 过滤，不关联的项目进不了对应比赛的池（2026-10-24 补）
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiCreateProject } from '@/api/project'
import { apiMembers } from '@/api/member'
import type { MemberListItem } from '@/api/member'
import { apiContests } from '@/api/contest'
import type { ContestListItem } from '@/api/contest'
import { useUserStore } from '@/stores/user'
import { PROJECT_VISIBILITY_LABEL } from '@/config/labels'

const visible = defineModel<boolean>({ required: true })
const emit = defineEmits<{ submitted: [] }>()

const userStore = useUserStore()
const isAdmin = computed(() => userStore.role === 'SUPER_ADMIN' || userStore.role === 'DEPT_ADMIN')

const form = ref({ name: '', description: '', visibility: 'ORG', startDate: '', endDate: '', contestId: '' })
const submitting = ref(false)

/** 关联比赛候选（当前学期全部，选填）：挂载池按 contest_id 过滤，不关联的项目进不了对应比赛的池 */
const contests = ref<ContestListItem[]>([])

// 代建（仅管理员）：null=为自己建；选中成员后帮其创建（搜索走 A 线人员档案）
const owner = ref<MemberListItem | null>(null)
const ownerKw = ref('')
const ownerOptions = ref<MemberListItem[]>([])

async function searchOwner() {
  if (!ownerKw.value.trim()) return
  const r = await apiMembers({ page: 1, size: 10, keyword: ownerKw.value.trim() })
  ownerOptions.value = r.list
}

function pickOwner(m: MemberListItem) {
  owner.value = m
  ownerOptions.value = []
  ownerKw.value = ''
}

function resetCreateForm() {
  form.value = { name: '', description: '', visibility: 'ORG', startDate: '', endDate: '', contestId: '' }
  owner.value = null
  ownerOptions.value = []
  ownerKw.value = ''
}

async function submit() {
  if (submitting.value) return
  if (!form.value.name.trim()) {
    ElMessage.warning('项目名称不能为空')
    return
  }
  submitting.value = true
  try {
    await apiCreateProject({
      name: form.value.name.trim(),
      description: form.value.description,
      visibility: form.value.visibility,
      startDate: form.value.startDate,
      endDate: form.value.endDate,
      ...(form.value.contestId ? { contestId: form.value.contestId } : {}),
      ...(owner.value ? { ownerMemberId: owner.value.id } : {})
    })
    ElMessage.success(owner.value
      ? '项目已创建（所有人：' + owner.value.name + '）'
      : '项目已创建（你是所有人，可加成员/挂赛队）')
    visible.value = false
    resetCreateForm()
    emit('submitted')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    contests.value = await apiContests({})
  } catch {
    contests.value = [] // 比赛加载失败不阻断建项，仅无可选项
  }
})
</script>

<template>
  <el-dialog v-model="visible" title="新建项目" width="480px">
    <div class="form-col">
      <label>项目名称（同学期同组织内唯一，7004）</label>
      <input v-model="form.name" class="input-line" placeholder="如 智能循迹小车" />
      <label>项目简介</label>
      <textarea v-model="form.description" class="input-line" rows="3" placeholder="做什么、用什么技术"></textarea>
      <label>关联比赛（选填：关联后才能被该比赛的赛队挂载）</label>
      <select v-model="form.contestId" class="input-line">
        <option value="">不关联（通用项目）</option>
        <option v-for="c in contests" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
      <template v-if="isAdmin">
        <label>所有人（管理员代建，选填：不选=为自己创建）</label>
        <template v-if="owner">
          <div class="owner-picked">
            <span>已选：{{ owner.name }}<em class="mono">· {{ owner.college }}</em></span>
            <button class="btn-act" @click="owner = null">重选</button>
          </div>
        </template>
        <template v-else>
          <div style="display: flex; gap: 8px">
            <input v-model="ownerKw" class="input-line" style="flex: 1" placeholder="姓名/学号搜索成员" @keyup.enter="searchOwner" />
            <button class="btn-line" @click="searchOwner">搜索</button>
          </div>
          <div v-for="m in ownerOptions" :key="m.id" class="owner-opt" @click="pickOwner(m)">
            <span>{{ m.name }}<em class="mono">· {{ m.college }} · {{ m.studentNo }}</em></span>
            <span class="mono" style="font-size: 12px">选为所有人</span>
          </div>
        </template>
      </template>
      <label>可见性</label>
      <select v-model="form.visibility" class="input-line">
        <option v-for="(label, v) in PROJECT_VISIBILITY_LABEL" :key="v" :value="v">{{ label }}</option>
      </select>
      <label>起止日期</label>
      <div style="display: flex; gap: 8px">
        <input v-model="form.startDate" type="date" class="input-line" />
        <input v-model="form.endDate" type="date" class="input-line" />
      </div>
    </div>
    <template #footer>
      <button class="btn-line" @click="visible = false">取消</button>
      <button class="btn-ink" :disabled="submitting" @click="submit">创建</button>
    </template>
  </el-dialog>
</template>

<style scoped>
.form-col { display: flex; flex-direction: column; gap: 6px; }
.form-col label { font-size: 12px; color: var(--vio2-ink-2); margin-top: 6px; }
.owner-picked {
  display: flex; align-items: center; justify-content: space-between;
  padding: 6px 10px; border: 2px dashed var(--vio2-bd); background: var(--vio2-panel);
  font-size: 13px;
}
.owner-picked em { font-style: normal; color: var(--vio-ink-2); margin-left: 6px; font-size: 12px; }
.owner-opt {
  display: flex; align-items: center; justify-content: space-between;
  padding: 5px 10px; border: 1px solid var(--vio2-bd); font-size: 13px; cursor: pointer;
}
.owner-opt:hover { background: var(--vio2-yellow); }
.owner-opt em { font-style: normal; color: var(--vio-ink-2); margin-left: 6px; font-size: 12px; }
</style>
