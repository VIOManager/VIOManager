/**
 * 审批红点（P08 未读数全站共享：顶栏 chip / 侧边菜单 badge / 审批页 tab 角标）
 * 语义（审批中心清单 §4 P08）：todoCount=发给我的邀请/通知（PENDING 且未读），
 *    resultCount=我发起的已出结果且未读；管理员待办是工作职责，不进红点
 */
import { defineStore } from 'pinia'
import { apiUnreadCount } from '@/api/approval'

export const useApprovalStore = defineStore('approval', {
  state: () => ({
    todoCount: 0,
    resultCount: 0
  }),
  getters: {
    total: (s) => s.todoCount + s.resultCount
  },
  actions: {
    /** 拉取未读数（失败静默：红点不挡页面） */
    async refresh(): Promise<void> {
      try {
        const r = await apiUnreadCount()
        this.todoCount = r.todoCount
        this.resultCount = r.resultCount
      } catch {
        // 未读数拉取失败不打断页面
      }
    }
  }
})
