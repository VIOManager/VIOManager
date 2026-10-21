<script setup lang="ts">
/**
 * 我的材料页（/my/material，学生端，PC2 / MS01–MS05）
 * 按本人所在队伍展示材料任务：要求清单（状态/当前版本/文件名）+ 单条覆盖上传（版本 +1 旧版失效）
 * + zip 批量上传（演示：包名含类型中文名即匹配；不匹配文件清单展示）+ 截止提醒与齐全率条
 * 覆盖上传/齐全率联动：提交后重算，赛队列表"材料状态"同步变化
 */
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiBatchUpload, apiMyMaterials, apiSubmitMaterial } from '@/api/material'
import type { MyTeamMaterial } from '@/api/material'
import type { UploadResp } from '@/api/file'
import UploadField from '@/components/UploadField.vue'
import { MATERIAL_TYPE_LABEL } from '@/config/labels'

interface BatchPanel {
  teamId: string
  matched: string[]
  unmatched: string[]
}

const list = ref<MyTeamMaterial[]>([])
const lastBatch = ref<BatchPanel | null>(null)

async function load() {
  list.value = await apiMyMaterials()
}

/** 截止倒计时（天）；负数显示已过期 */
function daysLeft(deadline: string): number {
  return Math.ceil((new Date(deadline.replace(' ', 'T')).getTime() - Date.now()) / 86400000)
}

function acceptOf(formats: string[]): string {
  return formats.map((f) => '.' + f).join(',')
}
/** 单条上传：文件服务上传完成（拿到 fileId）后再调提交接口 */
async function onSubmitOne(teamId: string, requirementId: string, resp: UploadResp) {
  const r = await apiSubmitMaterial(requirementId, { teamId, fileId: resp.fileId, fileName: resp.originalName, fileSize: resp.fileSize })
  ElMessage.success('已提交，当前版本 v' + r.version + (r.version > 1 ? '（旧版本已失效）' : ''))
  await load()
}

/** zip 批量上传：模拟解压结果展示匹配/不匹配清单 */
async function onBatch(teamId: string, taskId: string, resp: UploadResp) {
  const r = await apiBatchUpload({ teamId, taskId, fileId: resp.fileId, fileName: resp.originalName })
  lastBatch.value = {
    teamId,
    matched: r.matched.map((m) => MATERIAL_TYPE_LABEL[m.materialType] ?? m.materialType),
    unmatched: r.unmatched
  }
  if (r.matched.length > 0) {
    ElMessage.success('批量提交 ' + r.matched.length + ' 项')
  } else {
    ElMessage.warning('压缩包内没有匹配到待交材料（演示：包名需含类型中文名）')
  }
  await load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>我的材料</h3>
      <div class="dossier-meta mono">
        <span>{{ list.length }} 支队伍</span>
        <span>截止前完成必传项，逾期 5002</span>
      </div>
    </div>

    <div v-for="tm in list" :key="tm.teamId" class="panel tm-card">
      <div class="tm-head">
        <div>
          <b>{{ tm.teamName }}</b>
          <span class="fs" style="margin-left: 10px; font-size: 12.5px">{{ tm.task.title }}</span>
        </div>
        <div class="tm-deadline mono" :class="{ urgent: daysLeft(tm.task.deadline) <= 3 }">
          截止 {{ tm.task.deadline }}（{{ daysLeft(tm.task.deadline) >= 0 ? '剩 ' + daysLeft(tm.task.deadline) + ' 天' : '已过期' }}）
        </div>
      </div>

      <div class="rate-line">
        <div class="rate-bar"><i :style="{ width: tm.completeRate + '%' }" /></div>
        <span class="mono">{{ tm.completeRate }}%</span>
        <span class="stamp" :class="tm.requiredMissing > 0 ? 'warn' : 'ok'">{{ tm.requiredMissing > 0 ? '待补 ' + tm.requiredMissing + ' 项' : '材料已交' }}</span>
      </div>

      <table class="ledger">
        <thead>
          <tr><th>材料</th><th>必传</th><th>允许格式</th><th>命名规则</th><th>状态</th><th>当前文件</th><th style="width: 150px">上传/覆盖</th></tr>
        </thead>
        <tbody>
          <tr v-for="it in tm.items" :key="it.requirement.id">
            <td>
              <!-- 名称为主标识（MR01 必填），类型中文名小字辅助；旧数据无名称时回退类型 -->
              <div style="color: var(--vio-ink); font-weight: 600">{{ it.requirement.name || (MATERIAL_TYPE_LABEL[it.requirement.materialType] ?? it.requirement.materialType) }}</div>
              <div class="fs" style="font-size: 11px">{{ MATERIAL_TYPE_LABEL[it.requirement.materialType] ?? it.requirement.materialType }}</div>
            </td>
            <td><span class="stamp" :class="it.requirement.requiredFlag === 1 ? 'warn' : 'ok'">{{ it.requirement.requiredFlag === 1 ? '必传' : '选传' }}</span></td>
            <td class="mono" style="font-size: 12px">{{ it.requirement.allowedFormats.join(' / ') }}</td>
            <td class="fs">{{ it.requirement.namingRule || '—' }}</td>
            <td>
              <span v-if="it.status === 'SUBMITTED'" class="stamp ok">已交 v{{ it.version }}</span>
              <span v-else class="stamp warn">未交</span>
            </td>
            <td class="fs" style="font-size: 12px">{{ it.fileName || '—' }}</td>
            <td>
              <UploadField
                biz-type="MATERIAL_SUBMIT"
                :accept="acceptOf(it.requirement.allowedFormats)"
                :max-mb="it.requirement.maxSizeMb"
                button-text="上传"
                @uploaded="onSubmitOne(tm.teamId, it.requirement.id, $event)"
              />
            </td>
          </tr>
        </tbody>
      </table>

      <div class="batch-zone">
        <UploadField biz-type="MATERIAL_SUBMIT" accept=".zip" :max-mb="50" button-text="zip 批量上传" @uploaded="onBatch(tm.teamId, tm.task.id, $event)" />
        <span class="fs" style="font-size: 12px; color: var(--vio-ink-3)">演示规则：压缩包文件名包含类型中文名即匹配（如 承诺书+合照.zip）</span>
      </div>

      <div v-if="lastBatch && lastBatch.teamId === tm.teamId" class="batch-result">
        <p><b>匹配 {{ lastBatch.matched.length }} 项</b>：<span v-if="lastBatch.matched.length">{{ lastBatch.matched.join('、') }}</span><span v-else>无</span></p>
        <p><b>不匹配文件</b>：<span v-if="lastBatch.unmatched.length" class="mono">{{ lastBatch.unmatched.join('、') }}</span><span v-else>无</span></p>
      </div>
    </div>

    <p v-if="list.length === 0" class="empty-note">暂无材料任务（加入队伍且所在比赛已配置材料任务后在此展示）</p>
  </div>
</template>

<style scoped>
.tm-card { margin-bottom: 18px; padding: 16px 18px; }
.tm-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.tm-head b { font-family: var(--vio2-din); font-size: 16px; font-weight: 900; color: var(--vio2-ink); }
.tm-deadline { font-size: 12px; color: var(--vio2-ink-2); }
.tm-deadline.urgent { color: #101010; background: var(--vio2-pink); border: 2px solid var(--vio2-bd); padding: 0 6px 1px; font-weight: 700; }
.rate-line { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
/* 进度条：黄斜纹填充 + 粗描边（与概览学期刻度尺同语言） */
.rate-bar { flex: 1; height: 14px; background: var(--vio2-panel-2); border: 2px solid var(--vio2-bd); }
.rate-bar i { display: block; height: 100%; background: repeating-linear-gradient(-45deg, var(--vio2-yellow) 0 8px, var(--vio2-panel-2) 8px 16px); transition: width 0.3s var(--vio-ease); }
.batch-zone { display: flex; align-items: center; gap: 14px; margin-top: 14px; padding-top: 12px; border-top: 2px solid var(--vio2-bd); }
.batch-result { margin-top: 10px; padding: 10px 12px; background: var(--vio2-panel-2); border: 2px solid var(--vio2-bd); font-size: 12.5px; }
.batch-result p { line-height: 1.9; }
</style>
