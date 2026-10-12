<script setup lang="ts">
/**
 * 加人弹窗（TE05 搜索 + TE04 申请）：300ms 防抖搜索，已在队的置灰
 * 4003/4005/4007 等拒绝由 http 拦截器弹出；成功后提示"已生成审批单"
 */
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { apiAddTeamMember, apiSearchTeamMembers } from '@/api/team'
import type { TeamMemberSearchItem } from '@/api/team'
import { debounce } from '@/utils/debounce'

const visible = defineModel<boolean>({ required: true })
const props = defineProps<{ teamId: string }>()

const keyword = ref('')
const results = ref<TeamMemberSearchItem[]>([])
const searching = ref(false)
const addingId = ref('')

async function search() {
  searching.value = true
  try {
    results.value = await apiSearchTeamMembers(props.teamId, keyword.value)
  } finally {
    searching.value = false
  }
}

const onInput = debounce(search, 300)

watch(visible, (v) => {
  if (v) {
    keyword.value = ''
    void search()
  }
})

async function onAdd(item: TeamMemberSearchItem) {
  if (item.inTeam || addingId.value) return
  addingId.value = item.memberId
  try {
    await apiAddTeamMember(props.teamId, item.memberId)
    ElMessage.success('已提交加人申请（生成审批单，通过后队员待确认）')
    await search()
  } catch {
    // 提示已由拦截器弹出
  } finally {
    addingId.value = ''
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="添加队员" width="480px" append-to-body>
    <input v-model="keyword" class="input-line" style="width: 100%" placeholder="姓名 / 学号 模糊搜索" @input="onInput" />
    <table class="ledger" style="margin-top: 12px">
      <thead><tr><th>姓名</th><th>学号</th><th>学院</th><th style="width: 80px">操作</th></tr></thead>
      <tbody>
        <tr v-for="r in results" :key="r.memberId">
          <td style="color: var(--vio-ink); font-weight: 600">{{ r.name }}</td>
          <td class="mono">{{ r.studentNo || '—' }}</td>
          <td>{{ r.college }}</td>
          <td>
            <button v-if="!r.inTeam" class="btn-act" :disabled="addingId === r.memberId" @click="onAdd(r)">加入</button>
            <span v-else class="fs" style="color: var(--vio-ink-3); font-size: 12px">已在队</span>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-if="!searching && results.length === 0" class="empty-note">没有匹配的人</p>
  </el-dialog>
</template>
