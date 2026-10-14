/**
 * 论坛站枚举翻译（零硬编码红线：页面禁止写死枚举中文名，统一进本文件）
 * 对应：主站 front/src/config/labels.ts 的论坛子集
 */

/** 技术交流推送来源（清单 sourceType） */
export const SOURCE_TYPE_LABEL: Record<string, string> = {
  ADMIN: '管理员录入',
  RSS: '外部收录',
  AI: 'AI 摘要'
}

/** 推送内容形态（清单 contentType） */
export const CONTENT_TYPE_LABEL: Record<string, string> = {
  ARTICLE: '图文',
  VIDEO: '视频',
  LINK: '外链'
}

/** 角色名（顶栏调试下拉用） */
export const ROLE_LABEL: Record<string, string> = {
  SUPER_ADMIN: '超级管理员',
  DEPT_ADMIN: '部门管理员',
  STUDENT: '学生'
}
