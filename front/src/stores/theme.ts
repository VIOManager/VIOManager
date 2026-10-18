/**
 * 主题仓库：图纸白 / 深空黑双主题（2026-10-15 定稿）
 * 机制见方案 §2.3：html[data-theme] 驱动 tokens.css 两套变量；
 * 偏好存 localStorage 键 vio_theme，缺省跟随系统 prefers-color-scheme（index.html 防闪烁脚本先行）
 */
import { defineStore } from 'pinia'

type Theme = 'light' | 'dark'

const KEY = 'vio_theme'

/** 初始值直接读根节点属性（index.html 脚本已在我们挂载前算好，不重复判系统偏好） */
function readInitial(): Theme {
  const v = document.documentElement.getAttribute('data-theme')
  return v === 'dark' ? 'dark' : 'light'
}

export const useThemeStore = defineStore('theme', {
  state: () => ({ current: readInitial() }),
  actions: {
    /** 切换：图纸白 ↔ 深空黑 */
    toggle() {
      this.set(this.current === 'light' ? 'dark' : 'light')
    },
    /** 指定主题并持久化（storage 抛异常时静默降级为本次会话内生效） */
    set(t: Theme) {
      this.current = t
      document.documentElement.setAttribute('data-theme', t)
      try {
        localStorage.setItem(KEY, t)
      } catch {
        // 无痕模式等场景写不进就算了
      }
    }
  }
})
