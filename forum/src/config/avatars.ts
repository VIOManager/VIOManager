/**
 * 头像生成（论坛站默认头像 = 主站 TopBar 同款"彩底黑边首字块"）
 * lzh 2026-10-23 拍板：弃用 pravatar 图床；形态与主站一致（色块+黑边+姓名首字），
 *    底色不全绿——按名字哈希取 --vio2-* 强调色调色盘，同人恒色、异人异色
 * 自定义头像优先级在 stores/user.avatarByName（本人已设自定义→自定义），本文件只管默认
 * 实现：canvas 画彩底 + 黑描边 + 姓名首字 → dataURL；按 姓名:尺寸:主题 记忆化（列表页
 *    多次渲染不重复画；主题在缓存键里只为配合响应式重取，颜色本身不随主题变）
 * TODO(联调)：接人员档案头像（member.photo_file_id 经文件服务下发），本文件届时退役
 */

/** 首字头像缓存：键 = name:size:theme */
const cache = new Map<string, string>()

/**
 * 首字块调色盘：取自 --vio2-* 强调色令牌（黄/蓝/丁香/粉/绿），与整站贴纸一个家族
 * lzh 2026-10-23 拍板：不要全绿——按名字哈希定色，同人恒色不跳变、异人自动铺开
 */
const PALETTE = ['#FFD02F', '#5B8CFF', '#B9AFFF', '#FF8FAB', '#7DE07A']

/** 名字 → 调色盘下标（31 进位哈希；固定算法保证同一人永远同一个颜色） */
function colorOf(name: string): string {
  let h = 0
  for (let i = 0; i < name.length; i++) {
    h = (h * 31 + name.charCodeAt(i)) >>> 0
  }
  return PALETTE[h % PALETTE.length]
}

/**
 * 取某人头像地址（默认首字块）
 * @param name 姓名（取首字入画）
 * @param size 展示尺寸 px：顶栏 34 / 列表 48 / 详情主楼 73 / 弹窗预览 128
 *             按 2 倍出图，高分屏不发虚（沿用原 pravatar 口径）
 * @param theme 当前主题档（绿令牌随主题翻转；由调用方传入响应式值，切主题触发重取）
 */
export function avatarOf(name: string, size: number, theme: string): string {
  const key = name + ':' + size + ':' + theme
  const hit = cache.get(key)
  if (hit) return hit

  const px = size * 2
  const canvas = document.createElement('canvas')
  canvas.width = px
  canvas.height = px
  const ctx = canvas.getContext('2d')
  if (!ctx) return ''

  // 彩底 + 黑描边：主站 TopBar 的 .ava 同款形态，底色按名字哈希取调色盘；边宽随尺寸放大
  const bg = colorOf(name)
  const ink = '#101010'
  const border = Math.max(4, Math.round(px / 16))
  ctx.fillStyle = bg
  ctx.fillRect(0, 0, px, px)
  ctx.strokeStyle = ink
  ctx.lineWidth = border
  ctx.strokeRect(border / 2, border / 2, px - border, px - border)

  // 姓名首字：DIN 令牌字体（Bahnschrift 优先），居中略下移补偿 CJK 基线
  ctx.fillStyle = ink
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.font = '900 ' + Math.round(px * 0.44) + 'px "Bahnschrift", "Microsoft YaHei", sans-serif'
  ctx.fillText(name.slice(0, 1), px / 2, px / 2 + px * 0.04)

  const url = canvas.toDataURL('image/png')
  cache.set(key, url)
  return url
}
