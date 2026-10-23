/**
 * 跨源 token 接力（2026-10-23 与主站 SideMenu 论坛入口同步加）。
 * 主站与论坛不同端口即不同源，sessionStorage 不互通——主站经 URL ?token= 一次性带过来。
 * 独立模块且必须是 main.ts 的第一个 import：ES import 先于模块体执行，
 * 若写在 main.ts 里会晚于 router 模块的 createWebHistory 捕获地址栏，
 * vue-router 初始化导航时会把带 token 的旧 URL 写回地址栏，抹除失效（实测踩坑）。
 */
const urlToken = new URLSearchParams(location.search).get('token')
if (urlToken) {
  sessionStorage.setItem('vio_token', urlToken)
  const u = new URL(location.href)
  u.searchParams.delete('token')
  history.replaceState(null, '', u.pathname + u.search + u.hash)
}
