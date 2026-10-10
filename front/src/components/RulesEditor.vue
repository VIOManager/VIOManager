<script setup lang="ts">
/**
 * 规则编辑器（B 线 §0 六键，全组唯一键名）
 * mode=full：模板默认值编辑（六键全录）
 * mode=override：比赛/赛道覆盖编辑——勾选"覆盖"才把该键写入 JSON（只存被覆盖的键）
 * ⚠️ 键名禁止新增；新增规则键必须先过 lzh 拍板改清单 §0
 */
import { RULE_KEY_LABEL } from '@/config/labels'
import type { RulesMap } from '@/api/contest'

defineProps<{ mode: 'full' | 'override' }>()
const model = defineModel<RulesMap>({ required: true })

/** 数值键 / 布尔键（§0 键型固定） */
const NUM_KEYS = ['memberLimit', 'prefillLimit', 'maxAdvisorCount']
const BOOL_KEYS = ['allowRepeatJoin', 'crossTeamLeader', 'crossTrackLimit']
const ALL_KEYS = [...NUM_KEYS, ...BOOL_KEYS]

function isNum(k: string): boolean {
  return NUM_KEYS.indexOf(k) >= 0
}

function isChecked(k: string): boolean {
  return Object.prototype.hasOwnProperty.call(model.value, k)
}

function toggle(k: string, on: boolean) {
  const next: RulesMap = { ...model.value }
  if (on) next[k] = isNum(k) ? 1 : false
  else delete next[k]
  model.value = next
}

function setNum(k: string, e: Event) {
  const v = Number((e.target as HTMLInputElement).value)
  model.value = { ...model.value, [k]: Number.isFinite(v) ? v : 0 }
}

function setBool(k: string, e: Event) {
  model.value = { ...model.value, [k]: (e.target as HTMLSelectElement).value === 'true' }
}

function getNum(k: string): number {
  const v = model.value[k]
  return typeof v === 'number' ? v : 0
}

function getBool(k: string): string {
  return model.value[k] === true ? 'true' : 'false'
}
</script>

<template>
  <div class="rules-editor">
    <div v-for="k in ALL_KEYS" :key="k" class="rule-row">
      <span class="rlabel">{{ RULE_KEY_LABEL[k] }}</span>
      <!-- 模板：直接编辑默认值 -->
      <template v-if="mode === 'full'">
        <input v-if="isNum(k)" type="number" min="0" class="input-line rinput" :value="getNum(k)" @input="setNum(k, $event)" />
        <select v-else class="input-line rinput" :value="getBool(k)" @change="setBool(k, $event)">
          <option value="true">是</option>
          <option value="false">否</option>
        </select>
      </template>
      <!-- 比赛/赛道：勾选覆盖才写键 -->
      <template v-else>
        <label class="cov">
          <input type="checkbox" :checked="isChecked(k)" @change="toggle(k, ($event.target as HTMLInputElement).checked)" />
          <span class="fs">覆盖</span>
        </label>
        <template v-if="isChecked(k)">
          <input v-if="isNum(k)" type="number" min="0" class="input-line rinput" :value="getNum(k)" @input="setNum(k, $event)" />
          <select v-else class="input-line rinput" :value="getBool(k)" @change="setBool(k, $event)">
            <option value="true">是</option>
            <option value="false">否</option>
          </select>
        </template>
        <span v-else class="fs follow">跟随上级</span>
      </template>
    </div>
  </div>
</template>

<style scoped>
.rules-editor { border: 1px dashed var(--vio-rule); padding: 10px 14px; }
.rule-row { display: flex; align-items: center; gap: 12px; padding: 5px 0; font-size: 12.5px; }
.rlabel { width: 140px; color: var(--vio-ink-2); font-family: var(--vio-fangsong); letter-spacing: 0.06em; }
.rinput { width: 110px; padding: 4px 8px; }
.cov { display: flex; align-items: center; gap: 4px; color: var(--vio-ink-3); font-size: 12px; cursor: pointer; }
.follow { color: var(--vio-ink-3); font-size: 12px; }
</style>
