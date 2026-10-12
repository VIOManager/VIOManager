/**
 * 防抖工具：模糊搜索输入 300ms（规范 §2.3 / PRD §4）
 * 用法：const onKeyword = debounce((v: string) => load(), 300)
 */
export function debounce<A extends unknown[]>(fn: (...args: A) => void, wait = 300): (...args: A) => void {
  let timer: ReturnType<typeof setTimeout> | undefined
  return (...args: A) => {
    clearTimeout(timer)
    timer = setTimeout(() => fn(...args), wait)
  }
}
