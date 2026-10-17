/**
 * 头像映射（论坛站，2026-10-15 起与主站同口径）
 * 主站 TopBar 的 .ava 规格复刻：圆形浅蓝底（#EEF1F6）+ 钢笔墨蓝首字（#1D3A8F，宋体加粗）。
 * 论坛以 SVG data-URI 实现同款——跨站同人同貌，且离线可用
 * （不再依赖 pravatar 外链图床：外部网络不可达时会全体裂图，且旧映射表是 mock 时代名单，
 *  现演示用户全员落到同一兜底图）。
 * TODO(联调)：接人员档案头像（member.photo_file_id 经文件服务下发），本文件届时退役
 */

/** 主站 tokens.css 同款色值（跨站同步的单一事实来源在主站，此处为复刻） */
const BG = '#EEF1F6'
const INK = '#1D3A8F'

/** XML 特殊字符转义（姓名进 SVG 文本前的安全处理） */
function esc(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

/**
 * 取某人头像地址（SVG data-URI，姓名首字圆徽）
 * @param name 姓名
 * @param size 展示尺寸 px：列表 48 / 楼层 48 / 详情主楼 73 / 侧栏热议 24 / 顶栏 26
 *             画布边长按 2 倍绘制，高分屏不发虚
 */
export function avatarOf(name: string, size: number): string {
  const ch = esc((name && name.length > 0 ? name : '?').slice(0, 1))
  const s = size * 2
  const svg =
    '<svg xmlns="http://www.w3.org/2000/svg" width="' + s + '" height="' + s + '">' +
    '<circle cx="50%" cy="50%" r="50%" fill="' + BG + '"/>' +
    '<text x="50%" y="55%" text-anchor="middle" dominant-baseline="middle" ' +
    'font-family="SimSun, NSimSun, serif" font-weight="700" ' +
    'font-size="' + Math.round(s * 0.46) + '" fill="' + INK + '">' + ch + '</text>' +
    '</svg>'
  return 'data:image/svg+xml,' + encodeURIComponent(svg)
}
