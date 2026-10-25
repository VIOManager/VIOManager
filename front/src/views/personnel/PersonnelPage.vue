<script setup lang="ts">
/**
 * 人员档案列表页（/personnel，A 线 M01 + M05B 批量删除）
 * 对应方案：《前端ABC模块页面开发-优化方案》PA2；《列表分页-优化方案》ST2 改滚动分页
 * 筛选：关键词（300ms 防抖）/组织/类型/技能（useDict）/账号绑定状态；滚动加载每批 20 条（追加模式）
 * ⚠️ 隐私：列表只展示裁剪后字段（身份证只显后 4 位）；邮箱/证件照等进详情（M02/M10）
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiBatchDeleteMembers, apiMembers } from '@/api/member'
import type { MemberListItem } from '@/api/member'
import { apiOrgTreeSafe } from '@/api/org'
import type { OrgNode } from '@/api/org'
import { useDict } from '@/composables/useDict'
import { debounce } from '@/utils/debounce'
import { MEMBER_TYPE_LABEL } from '@/config/labels'
import MaskField from '@/components/MaskField.vue'
import ScrollLoader from '@/components/ScrollLoader.vue'
import MemberDetailDrawer from './MemberDetailDrawer.vue'
import MemberFormDialog from './MemberFormDialog.vue'
import ImportDialog from './ImportDialog.vue'

const query = reactive({ size: 20, keyword: '', orgId: '', memberType: '', skill: '', hasAccount: '' })
const rows = ref<MemberListItem[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

const { selectable: skillOptions } = useDict('SKILL')
const orgOptions = ref<{ id: string; name: string; level: number }[]>([])

const drawerVisible = ref(false)
const importVisible = ref(false)
const detailId = ref('')
const formVisible = ref(false)
const editingId = ref('')

/** 行勾选（M05B 批量删除基础）：选中集合只含当前已加载行，刷新/筛选后清空 */
const selectedIds = ref<string[]>([])
const allPicked = computed(() => rows.value.length > 0 && selectedIds.value.length === rows.value.length)

function toggle(id: string) {
  const i = selectedIds.value.indexOf(id)
  if (i >= 0) selectedIds.value.splice(i, 1)
  else selectedIds.value.push(id)
}

function toggleAll() {
  selectedIds.value = allPicked.value ? [] : rows.value.map((m) => m.id)
}

/** 已加载条数达到总数即到底（total 可能随筛选变化，动态算不存变量） */
const noMore = computed(() => rows.value.length >= total.value)

/** 首次加载 / 筛选变化 / 增删改后刷新：清空回第 1 批 */
async function load() {
  loading.value = true
  try {
    page.value = 1
    const r = await apiMembers({
      page: 1,
      size: query.size,
      keyword: query.keyword || undefined,
      orgId: query.orgId || undefined,
      memberType: query.memberType || undefined,
      skill: query.skill || undefined,
      hasAccount: query.hasAccount || undefined
    })
    rows.value = r.list
    total.value = r.total
    selectedIds.value = []
  } finally {
    loading.value = false
  }
}

/** 滚动到底追加下一批（ScrollLoader 的 reach 回调） */
async function loadMore() {
  if (loading.value || noMore.value) return
  loading.value = true
  try {
    page.value++
    const r = await apiMembers({
      page: page.value,
      size: query.size,
      keyword: query.keyword || undefined,
      orgId: query.orgId || undefined,
      memberType: query.memberType || undefined,
      skill: query.skill || undefined,
      hasAccount: query.hasAccount || undefined
    })
    rows.value = rows.value.concat(r.list)
    total.value = r.total
  } finally {
    loading.value = false
  }
}

const onKeywordInput = debounce(() => {
  void load()
}, 300)

watch(
  () => [query.orgId, query.memberType, query.skill, query.hasAccount],
  () => {
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

/** 批量删除（M05B）：整批拒绝——任一不可删（队长 / 当前登录账号）全批不动，
 *  不可删名单由后端 message 带回、拦截器统一弹错，列表保持原样 */
async function onBatchDelete() {
  if (selectedIds.value.length === 0) return
  const count = selectedIds.value.length
  try {
    await ElMessageBox.confirm('确认删除选中的 ' + count + ' 名人员？任一不可删（队长 / 当前登录账号）则整批不执行。', '批量删除', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch {
    return
  }
  try {
    await apiBatchDeleteMembers(selectedIds.value.slice())
    ElMessage.success('已删除 ' + count + ' 名人员')
    await load()
  } catch {
    // 3003 / 3004 / 3006 等提示已由拦截器弹出
  }
}

onMounted(async () => {
  void load()
  // 组织树降级：O 线组织模块未交付前接口 1004，catch 掉避免未处理拒绝——
  // 列表不受影响，组织筛选下拉暂时只有"全部"，O01 落地后自动恢复
  try {
    const tree = await apiOrgTreeSafe()
    const out: { id: string; name: string; level: number }[] = []
    flatten(tree, out)
    orgOptions.value = out
  } catch {
    /* 组织接口未就绪，筛选降级为空 */
  }
})
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>人员档案</h3>
      <div class="dossier-meta mono">
        <span>共 {{ total }} 人</span>
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
      <button class="btn-line" :disabled="selectedIds.length === 0" @click="onBatchDelete">批量删除{{ selectedIds.length > 0 ? '（' + selectedIds.length + ' 人）' : '' }}</button>
      <button class="btn-line" @click="importVisible = true">Excel 导入</button>
      <button class="btn-ink" @click="openCreate">新建人员</button>
    </div>

    <div class="panel">
      <table class="ledger">
        <thead>
          <tr>
            <th style="width: 36px">
              <input id="pk-all" type="checkbox" class="pk" :checked="allPicked" @change="toggleAll" />
              <label for="pk-all" class="pick" title="全选 / 清空"></label>
            </th>
            <th class="nw">姓名</th><th class="nw">学号/工号</th><th class="nw">类型</th><th>学院 / 专业</th><th class="nw">班级</th>
            <th class="nw">手机号</th><th class="nw">所属组织</th><th>技能</th><th class="nw">身份证号</th><th class="nw">账号</th><th style="width: 60px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in rows" :key="m.id" class="linkrow" @click="openDetail(m.id)">
            <td class="pickcell" @click.stop>
              <input :id="'pk-' + m.id" type="checkbox" class="pk" :checked="selectedIds.indexOf(m.id) >= 0" @change="toggle(m.id)" />
              <label :for="'pk-' + m.id" class="pick"></label>
            </td>
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
      <p v-if="!loading && rows.length === 0" class="empty-note">没有匹配的人员</p>
      <ScrollLoader :loading="loading" :no-more="noMore" :loaded="rows.length" :total="total" @reach="loadMore" />
    </div>

    <MemberDetailDrawer v-model="drawerVisible" :member-id="detailId" @edit="openEdit" @deleted="load" />
    <MemberFormDialog v-model="formVisible" :member-id="editingId" :org-options="orgOptions" @saved="load" />
    <ImportDialog v-model="importVisible" @done="load" />
  </div>
</template>

<style scoped>
/* 行勾选圆圈（与成果页同一套图纸档案风：input 隐藏 + label 绘制） */
.pk { display: none; }
.pick { display: inline-block; position: relative; width: 16px; height: 16px; border: 1.5px solid var(--vio-rule); border-radius: 50%; cursor: pointer; vertical-align: middle; box-sizing: border-box; }
.pick:hover { border-color: var(--vio-blue-ink); }
.pk:checked + .pick { border-color: var(--vio-blue-ink); background: var(--vio-blue-ink); }
.pk:checked + .pick::after { content: ''; position: absolute; left: 4px; top: 1.5px; width: 4px; height: 8px; border: solid #FFF; border-width: 0 1.5px 1.5px 0; transform: rotate(45deg); }
.pickcell { text-align: center; }
</style>
