<script setup lang="ts">
/**
 * 换头像弹窗（个人中心头像点击唤起）
 * 流程：选图（File）→ FileReader 转 dataURL → canvas 中心正方形裁剪缩到 128px → 预览 → 保存
 * 存储：演示期存 localStorage（stores/user.setAvatar），联调后改走文件服务（member.photo_file_id）
 * ⚠️ 不压缩格式只裁尺寸：128px PNG 约 10–40KB，localStorage 5MB 上限无压力
 */
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

const user = useUserStore()

/** 待保存的裁剪结果（空串 = 还没选新图，保存钮置灰） */
const preview = ref('')
/** 原始文件名（预览区小字提示） */
const fileName = ref('')

watch(
  () => props.modelValue,
  (v) => {
    if (v) {
      preview.value = ''
      fileName.value = ''
    }
  }
)

function close() {
  emit('update:modelValue', false)
}

/** File → dataURL（Promise 包装 FileReader，链路上一步一错一步拦） */
function readAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(reader.error ?? new Error('读取失败'))
    reader.readAsDataURL(file)
  })
}

/** 中心正方形裁剪并缩到 128px：取短边为基准，长边居中截断 */
function cropSquare(dataUrl: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => {
      const side = Math.min(img.width, img.height)
      const sx = (img.width - side) / 2
      const sy = (img.height - side) / 2
      const canvas = document.createElement('canvas')
      canvas.width = 128
      canvas.height = 128
      const ctx = canvas.getContext('2d')
      if (!ctx) {
        reject(new Error('canvas 不可用'))
        return
      }
      ctx.drawImage(img, sx, sy, side, side, 0, 0, 128, 128)
      resolve(canvas.toDataURL('image/png'))
    }
    img.onerror = () => reject(new Error('图片解析失败'))
    img.src = dataUrl
  })
}

/** 选图入口：类型拦一道，之后裁剪入预览（此时还没保存，关弹窗即放弃） */
async function onPick(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files && input.files.length > 0 ? input.files[0] : null
  if (!file) return
  if (!file.type || file.type.indexOf('image/') !== 0) {
    ElMessage.warning('只能选图片文件')
    return
  }
  try {
    preview.value = await cropSquare(await readAsDataUrl(file))
    fileName.value = file.name
  } catch {
    ElMessage.error('图片处理失败，换一张试试')
  } finally {
    // 同名文件重选也要触发 change：用完清空
    input.value = ''
  }
}

function save() {
  if (!preview.value) return
  user.setAvatar(preview.value)
  ElMessage.success('头像已更新')
  close()
}

/** 恢复默认：清自定义头像回 pravatar 映射 */
function reset() {
  user.setAvatar('')
  ElMessage.success('已恢复默认头像')
  close()
}
</script>

<template>
  <Teleport to="body">
    <div v-if="modelValue" class="overlay" @click.self="close">
      <div class="dlg">
        <h3>换头像</h3>

        <!-- 预览区：方形粗边，未选图时显示当前头像做对照 -->
        <div class="stage">
          <img v-if="preview" class="pv" :src="preview" alt="新头像预览">
          <img v-else class="pv" :src="user.avatarByName(user.realName, 128)" alt="当前头像">
          <div class="note">
            <b>{{ preview ? '新头像预览' : '当前头像' }}</b>
            <span v-if="fileName">{{ fileName }} · 裁成正方形 128px</span>
            <span v-else>选图后自动中心裁剪成正方形</span>
          </div>
        </div>

        <div class="foot">
          <i>
            <button class="btn" type="button" @click="reset">恢复默认</button>
          </i>
          <button class="btn" type="button" @click="close">取消</button>
          <button class="btn main" type="button" :disabled="!preview" @click="save">保存</button>
        </div>

        <!-- 隐藏文件选择：标签按钮唤起，避开原生 input 样式 -->
        <label class="pick">选一张图片
          <input type="file" accept="image/*" hidden @change="onPick">
        </label>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
/* 弹窗皮与 PostDialog 同款：遮罩 --vio2-mask、卡片 3px 粗边 + 硬投影 + 直角 */
.overlay { position: fixed; inset: 0; background: var(--vio2-mask); display: grid; place-items: center; z-index: 60; }
.dlg { width: 400px; max-width: calc(100vw - 32px); background: var(--vio2-panel); border: var(--vio2-bd-main); box-shadow: var(--vio2-hard); padding: 22px 24px 18px; }
.dlg h3 { font-size: 17px; color: var(--vio2-ink); font-weight: 900; margin-bottom: 16px; padding-bottom: 12px; border-bottom: var(--vio2-bd-main); letter-spacing: 0.02em; }
.stage { display: flex; align-items: center; gap: 16px; margin-bottom: 14px; }
.pv { width: 96px; height: 96px; flex: none; border: 3px solid var(--vio2-bd); object-fit: cover; background: var(--vio2-panel-2); }
.note b { display: block; font-size: 13px; color: var(--vio2-ink); margin-bottom: 6px; }
.note span { font-family: var(--vio2-mono); font-size: 11px; color: var(--vio2-ink-3); line-height: 1.7; }
.foot { display: flex; align-items: center; border-top: 2px solid var(--vio2-row-line); padding-top: 12px; }
.foot i { flex: 1; font-style: normal; }
.btn { font-size: 13px; color: var(--vio2-ink-2); background: var(--vio2-panel); border: var(--vio2-bd-row); font-weight: 700; padding: 6px 16px; cursor: pointer; margin-left: 8px; box-shadow: var(--vio2-hard-xs); }
.btn:hover:not(:disabled) { transform: translate(2px, 2px); box-shadow: 1px 1px 0 var(--vio2-shadow); }
.btn.main { background: var(--vio2-yellow); color: var(--vio2-on-ac); border: var(--vio2-bd-acc); }
.btn:disabled { opacity: 0.55; cursor: not-allowed; }
/* 选图钮：黄块主钮形态（视觉主操作在选图，保存是确认） */
.pick { display: block; text-align: center; margin-top: 14px; background: var(--vio2-yellow); color: var(--vio2-on-ac); border: var(--vio2-bd-acc); box-shadow: var(--vio2-hard-xs); font-size: 13px; font-weight: 900; padding: 8px 0; cursor: pointer; letter-spacing: 0.06em; }
.pick:hover { transform: translate(1px, 1px); box-shadow: 1px 1px 0 var(--vio2-shadow); }
</style>
