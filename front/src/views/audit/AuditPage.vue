<script setup lang="ts">
/**
 * 审计日志页（/audit，仅超管与部门管理员，AQ01–AQ03）
 * 查账列表：姓名/登录名/摘要模糊 + bizType（AQ02 动态枚举）+ 成功失败 + 时间范围；倒序分页
 * 详情抽屉：httpMethod/uri/userAgent/requestParams（排查失败用 errorMessage 列表已带）
 * 数据范围：部门管理员仅见操作人所属组织在本部门子树内的日志（后端 DataScope）
 */
import { onMounted, ref } from 'vue'
import { apiAuditBizTypes, apiAuditLogDetail, apiAuditLogs } from '@/api/audit'
import type { AuditBizType, AuditLogDetail, AuditLogItem } from '@/api/audit'

const list = ref<AuditLogItem[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)

const keyword = ref('')
const bizType = ref('')
const success = ref('')
const startTime = ref('')
const endTime = ref('')
const bizTypes = ref<AuditBizType[]>([])

const drawerVisible = ref(false)
const detail = ref<AuditLogDetail | null>(null)

async function load() {
  loading.value = true
  try {
    const r = await apiAuditLogs({
      keyword: keyword.value || undefined,
      bizType: bizType.value || undefined,
      success: success.value === '' ? undefined : Number(success.value),
      startTime: startTime.value ? startTime.value + ' 00:00:00' : undefined,
      endTime: endTime.value ? endTime.value + ' 23:59:59' : undefined,
      page: page.value,
      size
    })
    list.value = r.list
    total.value = r.total
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  void load()
}

async function openDetail(id: string) {
  detail.value = await apiAuditLogDetail(id)
  drawerVisible.value = true
}

onMounted(async () => {
  await load()
  bizTypes.value = await apiAuditBizTypes()
})
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>审计日志</h3>
      <div class="dossier-meta mono">
        <span>共 {{ total }} 条</span>
        <span>骨架 · AQ01–AQ03</span>
      </div>
    </div>

    <div class="fbar">
      <input v-model="keyword" class="input-line" style="width: 170px" placeholder="姓名 / 登录名 / 摘要" @keyup.enter="onSearch" />
      <select v-model="bizType" class="input-line" @change="onSearch">
        <option value="">全部业务类型</option>
        <option v-for="t in bizTypes" :key="t.bizType" :value="t.bizType">{{ t.label }}</option>
      </select>
      <select v-model="success" class="input-line" @change="onSearch">
        <option value="">全部结果</option>
        <option value="1">成功</option>
        <option value="0">失败</option>
      </select>
      <input v-model="startTime" type="date" class="input-line" @change="onSearch" />
      <span class="mono" style="color: var(--vio-ink-3)">至</span>
      <input v-model="endTime" type="date" class="input-line" @change="onSearch" />
      <button class="btn-line" @click="onSearch">查询</button>
    </div>

    <div class="panel">
      <table class="ledger">
        <thead>
          <tr><th>时间</th><th>操作人</th><th>业务类型</th><th>摘要</th><th>结果</th><th>耗时</th><th>IP</th><th style="width: 60px">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="l in list" :key="l.id" class="linkrow" @click="openDetail(l.id)">
            <td class="mono" style="font-size: 12px">{{ l.createdAt }}</td>
            <td style="color: var(--vio-ink); font-weight: 600">{{ l.realName }}<span class="mono" style="font-weight: 400; color: var(--vio-ink-3); font-size: 11px"> {{ l.loginName }}</span></td>
            <td>{{ l.bizTypeLabel }}</td>
            <td>{{ l.summary }}<span v-if="l.success === 0" class="err-text">（{{ l.errorMessage }}）</span></td>
            <td><span class="stamp" :class="l.success === 1 ? 'ok' : 'bad'">{{ l.success === 1 ? '成功' : '失败' }}</span></td>
            <td class="mono" style="font-size: 12px">{{ l.costMillis }}ms</td>
            <td class="mono" style="font-size: 12px">{{ l.ip }}</td>
            <td><button class="btn-act" @click.stop="openDetail(l.id)">详情</button></td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && list.length === 0" class="empty-note">暂无日志</p>
    </div>

    <div v-if="total > size" class="pages mono">
      <button class="btn-line" :disabled="page <= 1" @click="page--; load()">上一页</button>
      <span>第 {{ page }} 页 · 共 {{ total }} 条</span>
      <button class="btn-line" :disabled="page * size >= total" @click="page++; load()">下一页</button>
    </div>

    <el-drawer v-model="drawerVisible" title="日志详情" size="480px">
      <template v-if="detail">
        <dl class="sheet">
          <div><dt>时间</dt><dd class="mono">{{ detail.createdAt }}</dd></div>
          <div><dt>操作人</dt><dd>{{ detail.realName }}（{{ detail.loginName }}）</dd></div>
          <div><dt>业务类型</dt><dd>{{ detail.bizTypeLabel }}<span class="mono" style="color: var(--vio-ink-3); font-size: 11px"> {{ detail.bizType }}</span></dd></div>
          <div><dt>摘要</dt><dd>{{ detail.summary }}</dd></div>
          <div><dt>结果</dt><dd>{{ detail.success === 1 ? '成功' : '失败：' + detail.errorMessage }}</dd></div>
          <div><dt>请求</dt><dd class="mono">{{ detail.httpMethod }} {{ detail.uri }}</dd></div>
          <div><dt>耗时</dt><dd class="mono">{{ detail.costMillis }}ms</dd></div>
          <div><dt>IP</dt><dd class="mono">{{ detail.ip }}</dd></div>
          <div><dt>UA</dt><dd class="mono" style="font-size: 11px">{{ detail.userAgent }}</dd></div>
        </dl>
        <div v-if="detail.requestParams" class="zone">
          <h6>请求参数</h6>
          <pre class="payload mono">{{ detail.requestParams }}</pre>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.stamp.bad { color: var(--vio-danger); border-color: var(--vio-danger); }
.err-text { color: var(--vio-danger); font-size: 12px; }
.pages { display: flex; align-items: center; gap: 14px; margin-top: 14px; font-size: 12px; color: var(--vio-ink-3); }
.sheet div { display: flex; padding: 7px 0; border-bottom: 1px dashed var(--vio-rule-soft); font-size: 13px; }
.sheet dt { width: 76px; color: var(--vio-ink-3); flex-shrink: 0; }
.sheet dd { color: var(--vio-ink); word-break: break-all; }
.zone { margin-top: 18px; padding-top: 12px; border-top: 1px solid var(--vio-rule-soft); }
.zone h6 { font-size: 12px; color: var(--vio-ink-3); margin-bottom: 10px; letter-spacing: 0.08em; }
.payload { margin: 0; padding: 10px 12px; background: var(--vio-wash); font-size: 12px; line-height: 1.7; white-space: pre-wrap; word-break: break-all; }
</style>
