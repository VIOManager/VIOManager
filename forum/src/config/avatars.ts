/**
 * 头像映射（论坛站演示期唯一头像数据源）
 * 依据：《论坛V2EX风格重设计-优化方案》§4——真人照片，演示期用 pravatar 图床按人固定映射
 * TODO(联调)：接人员档案头像（member.photo_file_id 经文件服务下发），本文件届时退役
 */

/** 演示成员 → pravatar 图片编号（固定映射；新增成员只许往后排新编号，不许改老编号） */
const AVATAR_MAP: Record<string, number> = {
  赵天宇: 12,
  陈子昂: 32,
  李明轩: 56,
  孙嘉仪: 45,
  王雨桐: 44,
  周子墨: 8
}

/** 兜底编号：未登记成员统一用它（固定而非随机，避免同一人每次渲染头像跳变） */
const FALLBACK_IMG = 5

/**
 * 取某人头像地址
 * @param name 姓名（演示数据需与 AVATAR_MAP 对齐）
 * @param size 展示尺寸 px：列表 48 / 楼层 48 / 详情主楼 73 / 侧栏热议 24 / 顶栏 26
 *             请求边长按 2 倍传图，高分屏不发虚
 */
export function avatarOf(name: string, size: number): string {
  const img = AVATAR_MAP[name] ?? FALLBACK_IMG
  return `https://i.pravatar.cc/${size * 2}?img=${img}`
}
