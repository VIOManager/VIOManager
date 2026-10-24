<script setup lang="ts">
/**
 * 我的项目（/my/project，学生端）
 * 列表=自己参与的（所有人/成员/指导教师；可见性八条由后端判定，清单 §2）
 * 新建项目（所有人=本人）+ 详情抽屉（复用管理端组件，管理操作按 viewerCanManage 自动显隐）
 * ⚠️ 学生数据范围由后端过滤；写口权限（7002）同样后端判定
 */
import { onMounted, ref } from 'vue'
import { apiProjects } from '@/api/project'
import type { ProjectItem } from '@/api/project'
import { PROJECT_STATUS_LABEL, PROJECT_VISIBILITY_LABEL } from '@/config/labels'
import ProjectDetailDrawer from '@/views/project/ProjectDetailDrawer.vue'
import ProjectCreateDialog from '@/views/project/ProjectCreateDialog.vue'

const list = ref<ProjectItem[]>([])
const loading = ref(false)
const createVisible = ref(false)
const drawerVisible = ref(false)
const detailId = ref('')

async function load() {
  loading.value = true
  try {
    list.value = await apiProjects({})
  } finally {
    loading.value = false
  }
}

function openDetail(id: string) {
  detailId.value = id
  drawerVisible.value = true
}

function stampClass(status: string): string {
  if (status === 'ACTIVE') return 'warn'
  if (status === 'FINISHED') return 'ok'
  return 'dim'
}

onMounted(load)
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>我的项目</h3>
      <div class="dossier-meta mono">
        <span>学生视角 · 仅自己参与的项目</span>
      </div>
    </div>

    <div class="fbar">
      <button class="btn-ink" @click="createVisible = true">新建项目</button>
      <span class="fs" style="font-size: 12px; color: var(--vio-ink-3)">创建后你是项目所有人，可加成员/指导教师；队长可在赛队详情里挂载项目</span>
    </div>

    <div class="panel">
      <table class="ledger">
        <thead>
          <tr><th>项目名称</th><th>所有人</th><th>所属组织</th><th>关联比赛</th><th>可见性</th><th>状态</th><th>起止</th><th style="width: 60px">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="p in list" :key="p.id" class="linkrow" @click="openDetail(p.id)">
            <td style="color: var(--vio-ink); font-weight: 600">{{ p.name }}</td>
            <td>{{ p.ownerName }}</td>
            <td>{{ p.orgName || '—' }}</td>
            <td>{{ p.contestName || '—' }}</td>
            <td>{{ PROJECT_VISIBILITY_LABEL[p.visibility] }}</td>
            <td><span class="stamp" :class="stampClass(p.status)">{{ PROJECT_STATUS_LABEL[p.status] }}</span></td>
            <td class="mono" style="font-size: 12px">{{ p.startDate }} ~ {{ p.endDate }}</td>
            <td><button class="btn-act" @click.stop="openDetail(p.id)">详情</button></td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && list.length === 0" class="empty-note">暂无参与的项目，点左上新建项目</p>
    </div>

    <ProjectCreateDialog v-model="createVisible" @submitted="load" />
    <ProjectDetailDrawer v-model="drawerVisible" :project-id="detailId" @changed="load" />
  </div>
</template>
