<script setup lang="ts">
/**
 * 赛队挂载项目区块（PR12–PR15，项目清单 §3.3：表在项目域、赛队详情页调用）
 * 队长：从可挂载池（PUBLIC / ORG 同部门 / 自己参与的）选择挂载，解绑置 RELEASED（已生成任务不回收）
 * 校验在后端：仅队长（7002）、同学期（7008）、不重复（7007）
 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiAttachableProjects, apiAttachProject, apiDetachProject, apiTeamProjects } from '@/api/project'
import type { ProjectItem, TeamProjectItem } from '@/api/project'

const props = defineProps<{ teamId: string; contestId: string; viewerIsLeader: boolean }>()

const list = ref<TeamProjectItem[]>([])
const pool = ref<ProjectItem[]>([])
const pickedId = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = await apiTeamProjects(props.teamId)
    if (props.viewerIsLeader) {
      pool.value = await apiAttachableProjects(props.contestId || undefined)
    }
  } catch {
    // 联调降级：项目模块后端未实现（/api/teams/{id}/projects 与 attachable 均 404），
    // 面板置空不弹错；后九模块上线后自动恢复
    list.value = []
    pool.value = []
  } finally {
    loading.value = false
  }
}

async function onAttach() {
  if (!pickedId.value) {
    ElMessage.warning('请选择要挂载的项目')
    return
  }
  await apiAttachProject(props.teamId, pickedId.value)
  ElMessage.success('已挂载（若该比赛有已审核节点，将自动补建任务）')
  pickedId.value = ''
  await load()
}

async function onDetach(projectId: string, name: string) {
  try {
    await ElMessageBox.confirm('解绑项目「' + name + '」？已生成的任务不回收', '解绑确认', { type: 'warning' })
  } catch {
    return
  }
  await apiDetachProject(props.teamId, projectId)
  ElMessage.success('已解绑')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="zone">
    <h6>挂载项目（同一项目可被多支赛队挂载）</h6>
    <div v-for="p in list" :key="p.projectId" class="row-line">
      <span>{{ p.name }}<em class="mono">{{ p.ownerName }} · {{ p.attachedAt }}</em></span>
      <button v-if="viewerIsLeader" class="btn-act" @click="onDetach(p.projectId, p.name)">解绑</button>
    </div>
    <p v-if="!loading && list.length === 0" class="dim-note">暂未挂载项目</p>
    <div v-if="viewerIsLeader" class="add-row">
      <select v-model="pickedId" class="input-line" style="flex: 1">
        <option value="">选择可挂载项目…</option>
        <option v-for="p in pool" :key="p.id" :value="p.id">{{ p.name }}（{{ p.ownerName }}）</option>
      </select>
      <button class="btn-line" @click="onAttach">挂载</button>
    </div>
  </div>
</template>

<style scoped>
.zone { margin-top: 18px; padding-top: 12px; border-top: 1px solid var(--vio-rule-soft); }
.zone h6 { font-size: 12px; color: var(--vio-ink-3); margin-bottom: 10px; letter-spacing: 0.08em; }
.row-line { display: flex; align-items: center; justify-content: space-between; padding: 7px 0; border-bottom: 1px dashed var(--vio-rule-soft); font-size: 13px; }
.row-line em { font-style: normal; font-size: 11px; color: var(--vio-ink-3); margin-left: 8px; }
.add-row { display: flex; gap: 8px; margin-top: 10px; }
.dim-note { font-size: 12px; color: var(--vio-ink-3); }
</style>
