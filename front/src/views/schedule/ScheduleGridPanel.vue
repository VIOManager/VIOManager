<script setup lang="ts">
/**
 * 周排班表格子面板（SC01–SC04）
 * 7×5 格子视图；点空格"排班"弹窗从该格空课名单选人（SC07 同源），8008 冲突时确认强制（force 重提）
 * 排班规则：同周同人同子组织仅一条（8005）；超上限 8006；未启用时段后端拦截（前端置灰未启用格）
 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiCancelSchedule, apiCreateSchedules, apiFreeSlotMembers, apiScheduleExport, apiScheduleRules, apiSchedules } from '@/api/schedule'
import type { FreeMember, ScheduleCell, ScheduleRule } from '@/api/schedule'
import { apiOrgTree } from '@/api/org'
import type { OrgNode } from '@/api/org'
import { SLOT_LABEL, WEEKDAY_LABEL } from '@/config/labels'
import { BizError } from '@/api/http'

const semesterId = ref('s2') // 当前学期（mock 演示固定 s2；联调后从学期接口取 ACTIVE）
const weekNo = ref(6)
const subOrgId = ref('')
const orgs = ref<Array<{ id: string; name: string }>>([])
const cells = ref<ScheduleCell[]>([])
const rules = ref<ScheduleRule[]>([])
const loading = ref(false)

function cellAt(weekday: number, slotIndex: number): ScheduleCell {
  for (const c of cells.value) {
    if (c.weekday === weekday && c.slotIndex === slotIndex) return c
  }
  return { weekday, slotIndex, entries: [] }
}

function ruleEnabled(weekday: number, slotIndex: number): boolean {
  for (const r of rules.value) {
    if (r.weekday === weekday && r.slotIndex === slotIndex) return r.enabled
  }
  return false
}

async function load() {
  if (!subOrgId.value) return
  loading.value = true
  try {
    const [c, r] = await Promise.all([
      apiSchedules({ semesterId: semesterId.value, weekNo: weekNo.value, subOrgId: subOrgId.value }),
      apiScheduleRules({ subOrgId: subOrgId.value })
    ])
    cells.value = c
    rules.value = r
  } finally {
    loading.value = false
  }
}

// ── 排班弹窗：从该格空课名单勾选（SC07），值班内容必填 ──
const dialogVisible = ref(false)
const dialogCell = ref<{ weekday: number; slotIndex: number }>({ weekday: 1, slotIndex: 1 })
const freeMembers = ref<FreeMember[]>([])
const checkedIds = ref<string[]>([])
const dutyName = ref('值班')
const submitting = ref(false)

async function openAssign(weekday: number, slotIndex: number) {
  dialogCell.value = { weekday, slotIndex }
  checkedIds.value = []
  dutyName.value = '值班'
  freeMembers.value = await apiFreeSlotMembers({ semesterId: semesterId.value, weekNo: weekNo.value, weekday, slotIndex })
  dialogVisible.value = true
}

function toggle(memberId: string) {
  const i = checkedIds.value.indexOf(memberId)
  if (i >= 0) checkedIds.value.splice(i, 1)
  else checkedIds.value.push(memberId)
}

async function submitAssign(force: boolean) {
  if (submitting.value) return
  if (checkedIds.value.length === 0) {
    ElMessage.warning('请勾选要排班的成员')
    return
  }
  submitting.value = true
  try {
    const assignments = checkedIds.value.map((memberId) => ({
      memberId, weekday: dialogCell.value.weekday, slotIndex: dialogCell.value.slotIndex, dutyName: dutyName.value
    }))
    await apiCreateSchedules({ semesterId: semesterId.value, weekNo: weekNo.value, subOrgId: subOrgId.value, assignments, force })
    ElMessage.success('排班已保存')
    dialogVisible.value = false
    await load()
  } catch (e) {
    // 8008 课表冲突：确认后带 force 重提（高危操作二次确认）
    if (e instanceof BizError && e.code === 8008) {
      try {
        await ElMessageBox.confirm(e.message + '。确认强制排入？', '课表冲突确认', { type: 'warning' })
      } catch {
        return
      }
      await apiCreateSchedules({
        semesterId: semesterId.value, weekNo: weekNo.value, subOrgId: subOrgId.value,
        assignments: checkedIds.value.map((memberId) => ({ memberId, weekday: dialogCell.value.weekday, slotIndex: dialogCell.value.slotIndex, dutyName: dutyName.value })),
        force: true
      })
      ElMessage.success('已强制排入（标记冲突）')
      dialogVisible.value = false
      await load()
    }
  } finally {
    submitting.value = false
  }
}

async function onCancel(entryId: string, memberName: string) {
  try {
    await ElMessageBox.confirm('取消 ' + memberName + ' 的该班次？', '取消排班确认', { type: 'warning' })
  } catch {
    return
  }
  await apiCancelSchedule(entryId)
  ElMessage.success('已取消')
  await load()
}

async function onExport() {
  const r = await apiScheduleExport({ semesterId: semesterId.value, weekNo: weekNo.value, subOrgId: subOrgId.value })
  ElMessage.success(r.note)
}

function flatten(nodes: OrgNode[], out: Array<{ id: string; name: string }>) {
  for (const n of nodes) {
    if (n.orgType !== 'ROOT') out.push({ id: n.id, name: n.name })
    flatten(n.children, out)
  }
}

onMounted(async () => {
  const tree = await apiOrgTree()
  const out: Array<{ id: string; name: string }> = []
  flatten(tree, out)
  orgs.value = out
  if (out.length > 0) {
    subOrgId.value = out[0].id
    await load()
  }
})
</script>

<template>
  <div>
    <div class="fbar">
      <select v-model="subOrgId" class="input-line" @change="load">
        <option v-for="o in orgs" :key="o.id" :value="o.id">{{ o.name }}</option>
      </select>
      <input v-model.number="weekNo" type="number" min="1" max="18" class="input-line" style="width: 90px" @change="load" />
      <span class="mono" style="font-size: 12px; color: var(--vio-ink-3)">第 {{ weekNo }} 教学周 · 灰格=未启用排班（规则配置里开）</span>
      <span style="flex: 1"></span>
      <button class="btn-line" @click="onExport">导出本周排班</button>
    </div>

    <div class="panel" style="overflow-x: auto">
      <table class="grid">
        <thead>
          <tr>
            <th class="mono">节次</th>
            <th v-for="w in 7" :key="w">{{ WEEKDAY_LABEL[w] }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="slot in 5" :key="slot">
            <td class="mono slot-head">{{ SLOT_LABEL[slot] }}</td>
            <td v-for="w in 7" :key="w" class="cell" :class="{ off: !ruleEnabled(w, slot) }">
              <template v-if="ruleEnabled(w, slot)">
                <div v-for="e in cellAt(w, slot).entries" :key="e.id" class="entry" :class="{ conflict: e.courseConflict }">
                  <span>{{ e.memberName }}<i v-if="e.courseConflict" class="warn-mark" :title="e.conflictNote">冲</i></span>
                  <em class="mono">{{ e.dutyName }}</em>
                  <button class="entry-x" title="取消该班次" @click="onCancel(e.id, e.memberName)">×</button>
                </div>
                <button class="add-btn" @click="openAssign(w, slot)">+ 排班</button>
              </template>
              <span v-else class="off-note">未启用</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <el-dialog v-model="dialogVisible" :title="'排班 · ' + WEEKDAY_LABEL[dialogCell.weekday] + ' ' + SLOT_LABEL[dialogCell.slotIndex]" width="480px">
      <div class="form-col">
        <label>值班内容</label>
        <input v-model="dutyName" class="input-line" placeholder="如 机房值守" />
        <label>该格空课成员（勾选排入；同一人本周已排会被 8005 拦截）</label>
        <div class="pick-list">
          <label v-for="m in freeMembers" :key="m.memberId" class="pick-row">
            <input type="checkbox" :checked="checkedIds.includes(m.memberId)" @change="toggle(m.memberId)" />
            <span>{{ m.name }}</span>
            <em class="mono">{{ m.className }} · {{ m.grade }} 级</em>
          </label>
          <p v-if="freeMembers.length === 0" class="dim">该时段无空课成员（或班级映射未确认）</p>
        </div>
      </div>
      <template #footer>
        <button class="btn-line" @click="dialogVisible = false">取消</button>
        <button class="btn-ink" :disabled="submitting" @click="submitAssign(false)">保存排班</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.grid { width: 100%; border-collapse: collapse; font-size: 12.5px; }
.grid th { font-family: var(--vio-fangsong); font-weight: 400; font-size: 11.5px; color: var(--vio-ink-3); letter-spacing: 0.1em; padding: 8px 6px; border-bottom: 1px solid var(--vio-rule); }
.grid td { border: 1px solid var(--vio-rule-soft); vertical-align: top; padding: 6px; min-width: 110px; }
.slot-head { color: var(--vio-ink-3); white-space: nowrap; }
.cell.off { background: var(--vio-wash); }
.off-note { font-size: 11px; color: var(--vio-ink-3); }
.entry { position: relative; background: var(--vio-sheet); border: 1px solid var(--vio-rule); padding: 4px 6px; margin-bottom: 4px; }
.entry.conflict { border-color: var(--vio-seal); }
.entry span { color: var(--vio-ink); font-weight: 600; }
.entry em { font-style: normal; color: var(--vio-ink-3); font-size: 11px; margin-left: 6px; }
.entry-x { position: absolute; top: 2px; right: 4px; border: none; background: none; color: var(--vio-ink-3); cursor: pointer; font-size: 12px; }
.entry-x:hover { color: var(--vio-danger); }
.warn-mark { font-style: normal; font-size: 10px; color: var(--vio-seal); border: 1px solid var(--vio-seal); padding: 0 3px; margin-left: 4px; cursor: help; }
.add-btn { border: none; background: none; color: var(--vio-blue-ink); font-size: 11.5px; cursor: pointer; padding: 2px 0; }
.add-btn:hover { text-decoration: underline; }
.form-col { display: flex; flex-direction: column; gap: 6px; }
.form-col label { font-size: 12px; color: var(--vio-ink-3); margin-top: 6px; }
.pick-list { max-height: 220px; overflow: auto; border: 1px solid var(--vio-rule-soft); }
.pick-row { display: flex; align-items: center; gap: 8px; padding: 7px 10px; border-bottom: 1px solid var(--vio-rule-soft); font-size: 13px; cursor: pointer; }
.pick-row em { font-style: normal; font-size: 11px; color: var(--vio-ink-3); }
.dim { color: var(--vio-ink-3); font-size: 12px; padding: 8px 10px; }
</style>
