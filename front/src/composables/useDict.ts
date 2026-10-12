/**
 * 字典 hook（全站枚举下拉唯一数据源 —— 红线：页面禁止硬编码字典项）
 * 对应方案：《前端ABC模块页面开发-优化方案》FS3
 * 机制：同一字典类型全站共享一份缓存，多处调用不重复请求；refresh() 强制刷新
 *    （审核通过/用户补充后，由操作页面调 refresh 让下拉立即含新项）
 */
import { computed, ref } from 'vue'
import type { Ref } from 'vue'
import { apiDictItems } from '@/api/dict'
import type { DictItem } from '@/api/dict'

interface DictSlot {
  items: Ref<DictItem[]>
  loading: Ref<boolean>
  loaded: boolean
}

const cache = new Map<string, DictSlot>()

export function useDict(type: string) {
  let found = cache.get(type)
  if (!found) {
    found = { items: ref<DictItem[]>([]), loading: ref(false), loaded: false }
    cache.set(type, found)
  }
  const slot = found

  async function refresh(): Promise<void> {
    if (slot.loading.value) return
    slot.loading.value = true
    try {
      slot.items.value = await apiDictItems(type)
      slot.loaded = true
    } finally {
      slot.loading.value = false
    }
  }

  // 首次使用自动拉取
  if (!slot.loaded && !slot.loading.value) void refresh()

  /** 可选项：预置项 + 审核通过的用户补充项（待审/已驳回不可被选用） */
  const selectable = computed(() => {
    const list: DictItem[] = []
    for (const it of slot.items.value) {
      if (it.source === 'PRESET' || it.auditStatus === 'APPROVED') list.push(it)
    }
    return list
  })

  return { items: slot.items, selectable, loading: slot.loading, refresh }
}
