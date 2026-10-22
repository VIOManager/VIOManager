<script setup lang="ts">
/**
 * 课表导入面板（TT01–TT07）
 * 上传（同步解析返回成败数）→ 批次列表（PENDING 可人工指定学期）→ 班级映射批量确认
 * 铁规则：未确认映射的班级永远不参与空课统计
 */
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiAssignImportSemester, apiClassMappings, apiConfirmMappings, apiTimetableImport, apiTimetableImportDetail, apiTimetableImports, apiTimetableTemplate } from '@/api/schedule'
import type { ClassMapping, ImportBatch, ImportBatchDetail } from '@/api/schedule'
import { apiSemestersSafe } from '@/api/semester'
import type { Semester } from '@/api/semester'
import { IMPORT_BATCH_STATUS_LABEL } from '@/config/labels'

const batches = ref<ImportBatch[]>([])
const mappings = ref<ClassMapping[]>([])
const semesters = ref<Semester[]>([])
const currentSemesterId = ref('') // 当前激活学期：映射列表按学期查询（免参后端报 1004 学期不存在）
const onlyWithMember = ref(true) // 只看有在册成员的班级（全校课表班级多，登记在册的才是要管的）
const checkedIds = ref<string[]>([])
const uploading = ref(false)

async function load() {
  const [b, m] = await Promise.all([
    apiTimetableImports(),
    apiClassMappings({ semesterId: currentSemesterId.value || undefined, hasMember: onlyWithMember.value || undefined })
  ])
  batches.value = b
  mappings.value = m
}

async function onDownloadTemplate() {
  await apiTimetableTemplate()
  ElMessage.success('模板已下载（16 列 + 5 条示例行）')
}

/** 上传导入（幂等键随选择生成；同步解析，返回成败数） */
async function onPickFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files && input.files[0]
  if (!file || uploading.value) return
  uploading.value = true
  try {
    const r = await apiTimetableImport(file, crypto.randomUUID())
    ElMessage.success('解析完成：共 ' + r.totalCount + ' 行，成功 ' + r.successCount + '、失败 ' + r.failCount + '；请到下方确认班级映射')
    await load()
  } finally {
    uploading.value = false
    input.value = ''
  }
}

// ── 批次详情（错误明细）──
const detailVisible = ref(false)
const detail = ref<ImportBatchDetail | null>(null)

async function openDetail(id: string) {
  detail.value = await apiTimetableImportDetail(id)
  detailVisible.value = true
}

// ── TT05 归一化失败：人工指定学期 ──
async function assignSemester(b: ImportBatch, semesterId: string) {
  if (!semesterId) return
  await apiAssignImportSemester(b.id, semesterId)
  ElMessage.success('已指定学期并续跑解析')
  await load()
}

// ── TT07 批量确认 ──
function toggle(id: string) {
  const i = checkedIds.value.indexOf(id)
  if (i >= 0) checkedIds.value.splice(i, 1)
  else checkedIds.value.push(id)
}

async function confirmPicked() {
  if (checkedIds.value.length === 0) {
    ElMessage.warning('请勾选要确认的映射')
    return
  }
  await apiConfirmMappings(checkedIds.value)
  ElMessage.success('已确认 ' + checkedIds.value.length + ' 条映射（开始参与空课统计）')
  checkedIds.value = []
  await load()
}

/** 映射区切换学期（默认激活学期；教务导入常落在历史学期，需可切换查看/确认） */
async function onMappingFilterChange() {
  checkedIds.value = []
  await load()
}

onMounted(async () => {
  semesters.value = await apiSemestersSafe()
  const active = semesters.value.find((s) => s.status === 'ACTIVE') ?? semesters.value[0]
  if (active) currentSemesterId.value = String(active.id)
  await load()
})
</script>

<template>
  <div>
    <div class="fbar">
      <button class="btn-line" @click="onDownloadTemplate">下载导入模板</button>
      <label class="btn-ink" :class="{ disabled: uploading }">
        {{ uploading ? '解析中…' : '上传课表 Excel' }}
        <input type="file" accept=".xlsx,.xls,.csv" style="display: none" :disabled="uploading" @change="onPickFile" />
      </label>
      <span class="fs" style="font-size: 12px; color: var(--vio-ink-3)">同步解析：上传即出结果；学期名认不出的批次留在待归一化，人工指定后续跑</span>
    </div>

    <div class="panel">
      <h6 class="panel-title">导入批次</h6>
      <table class="ledger">
        <thead>
          <tr><th>时间</th><th>学期</th><th>状态</th><th>总数</th><th>成功</th><th>失败</th><th style="width: 190px">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="b in batches" :key="b.id">
            <td class="mono" style="font-size: 12px">{{ b.createdAt }}</td>
            <td>{{ b.semesterName || '—' }}</td>
            <td>
              <span class="stamp" :class="b.status === 'COMPLETED' ? 'ok' : b.status === 'FAILED' ? 'bad' : 'warn'">{{ IMPORT_BATCH_STATUS_LABEL[b.status] }}</span>
            </td>
            <td class="mono">{{ b.totalCount }}</td>
            <td class="mono">{{ b.successCount }}</td>
            <td class="mono">{{ b.failCount }}</td>
            <td>
              <button class="btn-act" @click="openDetail(b.id)">明细</button>
              <template v-if="b.status === 'PENDING'">
                <select class="input-line" style="width: 130px; margin-left: 6px" @change="assignSemester(b, ($event.target as HTMLSelectElement).value)">
                  <option value="">指定学期…</option>
                  <option v-for="s in semesters" :key="s.id" :value="s.id">{{ s.name }}</option>
                </select>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="panel" style="margin-top: 14px">
      <h6 class="panel-title">
        班级映射（确认后才参与空课统计）
        <button class="btn-line" style="float: right" :disabled="checkedIds.length === 0" @click="confirmPicked">确认所选（{{ checkedIds.length }}）</button>
        <label style="float: right; margin-right: 12px; font-size: 12px; font-weight: 400; cursor: pointer">
          <input type="checkbox" v-model="onlyWithMember" @change="onMappingFilterChange" /> 只看有在册成员的班级
        </label>
        <select class="input-line" style="float: right; width: 210px; margin-right: 8px" title="按学期查看班级映射" v-model="currentSemesterId" @change="onMappingFilterChange">
          <option v-for="s in semesters" :key="s.id" :value="String(s.id)">{{ s.name }}</option>
        </select>
      </h6>
      <table class="ledger" v-if="mappings.length > 0">
        <thead>
          <tr><th style="width: 40px"></th><th>课表班级名</th><th>档案班级名</th><th>年级</th><th>年级来源</th><th>状态</th></tr>
        </thead>
        <tbody>
          <tr v-for="m in mappings" :key="m.id">
            <td><input v-if="!m.confirmed" type="checkbox" :checked="checkedIds.includes(m.id)" @change="toggle(m.id)" /></td>
            <td>{{ m.classKey }}</td>
            <td>{{ m.className || '—（无对应在册成员）' }}</td>
            <td class="mono">{{ m.grade ? m.grade + ' 级' : '—' }}</td>
            <td>{{ m.gradeSource }}</td>
            <td><span class="stamp" :class="m.confirmed ? 'ok' : 'warn'">{{ m.confirmed ? '已确认' : '待确认' }}</span></td>
          </tr>
        </tbody>
      </table>
      <p v-else class="empty-note" style="padding: 18px 4px">
        {{ onlyWithMember ? '当前学期还没有在册成员匹配到课表班级——新增成员（或调整其班级）后会自动出现在这里，确认后即可参与空课统计' : '该学期暂无班级映射，先上传课表' }}
      </p>
    </div>

    <el-dialog v-model="detailVisible" title="批次明细（错误行）" width="480px">
      <template v-if="detail">
        <p class="mono" style="font-size: 12.5px">共 {{ detail.totalCount }} 行 · 成功 {{ detail.successCount }} · 失败 {{ detail.failCount }}</p>
        <table class="ledger" style="margin-top: 10px">
          <thead>
            <tr><th style="width: 80px">行号</th><th>失败原因</th></tr>
          </thead>
          <tbody>
            <tr v-for="e in detail.errors" :key="e.rowNo">
              <td class="mono">{{ e.rowNo }}</td>
              <td>{{ e.reason }}</td>
            </tr>
          </tbody>
        </table>
        <p v-if="detail.errors.length === 0" class="empty-note">无错误行</p>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.panel-title { font-size: 12px; color: var(--vio-ink-3); letter-spacing: 0.08em; margin-bottom: 8px; }
.btn-ink.disabled { opacity: 0.6; pointer-events: none; }
.stamp.bad { color: var(--vio-danger); border-color: var(--vio-danger); }
</style>
