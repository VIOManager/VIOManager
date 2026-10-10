<script setup lang="ts">
/**
 * 人员档案列表页（/personnel，A 线 M01）
 * 对应方案：《前端ABC模块页面开发-优化方案》PA2
 * 筛选：关键词（300ms 防抖）/组织/类型/技能（useDict）/账号绑定状态；分页 page/size
 * ⚠️ 隐私：列表只展示裁剪后字段（身份证只显后 4 位）；邮箱/证件照等进详情（M02/M10）
 */
import { onMounted, reactive, ref, watch } from 'vue'
import { apiMembers } from '@/api/member'
import type { MemberListItem } from '@/api/member'
import type { PageResult } from '@/api/types'
import { apiOrgTree } from '@/api/org'
import type { OrgNode } from '@/api/org'
import { useDict } from '@/composables/useDict'
import { debounce } from '@/utils/debounce'
import { MEMBER_TYPE_LABEL } from '@/config/labels'
import MaskField from '@/components/MaskField.vue'
import MemberDetailDrawer from './MemberDetailDrawer.vue'
import MemberFormDialog from './MemberFormDialog.vue'
import ImportDialog from './ImportDialog.vue'

const query = reactive({ page: 1, size: 20, keyword: '', orgId: '', memberType: '', skill: '', hasAccount: '' })
const data = ref<PageResult<MemberListItem>>({ list: [], total: 0, page: 1, size: 20 })
const loading = ref(false)

const { selectable: skillOptions } = useDict('SKILL')
const orgOptions = ref<{ id: string; name: string; level: number }[]>([])

const drawerVisible = ref(false)
const importVisible = ref(false)
const detailId = ref('')
const formVisible = ref(false)
const editingId = ref('')

async function load() {
  loading.value = true
  try {
    data.value = await apiMembers({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      orgId: query.orgId || undefined,
      memberType: query.memberType || undefined,
      skill: query.skill || undefined,
      hasAccount: query.hasAccount || undefined
    })
  } finally {
    loading.value = false
  }
}

const onKeywordInput = debounce(() => {
  query.page = 1
  void load()
}, 300)

watch(
  () => [query.orgId, query.memberType, query.skill, query.hasAccount],
  () => {
    query.page = 1
    void load()
  }
)

function flatten(nodes: OrgNode[], out: { id: string; name: string; level: number }[]) {
  for (const n of nodes) {
    out.push({ id: n.id, name: n.name, level: n.level })
    flatten(n.children, out)
  }
}

function openDetail(id: string) {
  detailId.value = id
  drawerVisible.value = true
}

function openCreate() {
  editingId.value = ''
  formVisible.value = true
}

function openEdit(id: string) {
  editingId.value = id
  formVisible.value = true
}

function pageTo(p: number) {
  const maxPage = Math.max(1, Math.ceil(data.value.total / query.size))
  if (p < 1 || p > maxPage) return
  query.page = p
  void load()
}

onMounted(async () => {
  void load()
  const tree = await apiOrgTree()
  const out: { id: string; name: string; level: number }[] = []
  flatten(tree, out)
  orgOptions.value = out
})
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>人员档案</h3>
      <div class="dossier-meta mono">
        <span>共 {{ data.total }} 人</span>
        <span>A 线 · M01–M13</span>
      </div>
    </div>

    <div class="fbar">
      <input v-model="query.keyword" class="input-line" style="width: 220px" placeholder="姓名 / 学号 / 手机号" @input="onKeywordInput" />
      <select v-model="query.orgId" class="input-line">
        <option value="">全部组织</option>
        <option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ '　'.repeat(o.level - 1) + o.name }}</option>
      </select>
      <select v-model="query.memberType" class="input-line">
        <option value="">全部类型</option>
        <option v-for="(label, v) in MEMBER_TYPE_LABEL" :key="v" :value="v">{{ label }}</option>
      </select>
      <select v-model="query.skill" class="input-line">
        <option value="">全部技能</option>
        <option v-for="s in skillOptions" :key="s.id" :value="s.label">{{ s.label }}</option>
      </select>
      <select v-model="query.hasAccount" class="input-line">
        <option value="">账号状态</option>
        <option value="true">已绑定</option>
        <option value="false">未绑定</option>
      </select>
      <span style="flex: 1"></span>
      <button class="btn-line" @click="importVisible = true">Excel 导入</button>
      <button class="btn-ink" @click="openCreate">新建人员</button>
    </div>

    <div class="panel">
      <table class="ledger">
        <thead>
          <tr>
            <th class="nw">姓名</th><th class="nw">学号/工号</th><th class="nw">类型</th><th>学院 / 专业</th><th class="nw">班级</th>
            <th class="nw">手机号</th><th class="nw">所属组织</th><th>技能</th><th class="nw">身份证号</th><th class="nw">账号</th><th style="width: 60px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in data.list" :key="m.id" class="linkrow" @click="openDetail(m.id)">
            <td class="nw" style="color: var(--vio-ink); font-weight: 600">{{ m.name }}</td>
            <td class="mono nw">{{ m.studentNo || '—' }}</td>
            <td class="nw">{{ MEMBER_TYPE_LABEL[m.memberType] }}</td>
            <td>{{ m.college }}<template v-if="m.major"> · {{ m.major }}</template></td>
            <td class="nw">{{ m.className || '—' }}</td>
            <td class="nw"><MaskField :text="m.phone" kind="phone" /></td>
            <td class="nw">{{ m.orgName || '—' }}</td>
            <td>
              <span v-for="s in m.skills.slice(0, 2)" :key="s" class="stamp blue" style="margin-right: 4px">{{ s }}</span>
              <span v-if="m.skills.length > 2" class="fs" style="color: var(--vio-ink-3); font-size: 11px">+{{ m.skills.length - 2 }}</span>
            </td>
            <td class="nw"><MaskField :text="m.idCardLast4" kind="idcard" /></td>
            <td class="nw"><span class="stamp" :class="m.hasAccount ? 'ok' : 'dim'">{{ m.hasAccount ? '已绑定' : '未绑定' }}</span></td>
            <td><button class="btn-act" @click.stop="openDetail(m.id)">详情</button></td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && data.list.length === 0" class="empty-note">没有匹配的人员</p>
      <div class="pager">
        <span class="mono">第 {{ data.page }} 页 / 共 {{ data.total }} 条</span>
        <button :disabled="data.page <= 1" @click="pageTo(data.page - 1)">上一页</button>
        <button :disabled="data.page * data.size >= data.total" @click="pageTo(data.page + 1)">下一页</button>
      </div>
    </div>

    <MemberDetailDrawer v-model="drawerVisible" :member-id="detailId" @edit="openEdit" @deleted="load" />
    <MemberFormDialog v-model="formVisible" :member-id="editingId" :org-options="orgOptions" @saved="load" />
    <ImportDialog v-model="importVisible" @done="load" />
  </div>
</template>
