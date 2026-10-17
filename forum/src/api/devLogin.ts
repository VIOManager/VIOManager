/**
 * ⚠️ 开发调试件：直连管理侧登录接口换真 JWT（联调后随调试入口一起移除）
 * 背景：论坛前端切真实模式后，桩 token（dev-stub-token）过不了论坛服务 8089 的
 * JWT 验签——首个请求即 401 踢回登录。调试进入必须走真登录。
 * 请求经 vite 代理 /mgmt-api → 8088/api（浏览器直连 8088 跨源，会被 CORS 拦）。
 */
export interface DevAccount {
  name: string
  role: string
  loginName: string
  password: string
}

/** 三个预设身份 = 管理侧演示账号（vio-management R__demo_data 种子） */
export const DEV_ACCOUNTS: DevAccount[] = [
  { name: '管理员', role: 'SUPER_ADMIN', loginName: 'admin', password: 'Admin@123456' },
  { name: '部门管理员', role: 'DEPT_ADMIN', loginName: 'dept_chen', password: 'Dept@123456' },
  { name: '学生', role: 'STUDENT', loginName: 'stu_wang', password: 'Stu@123456' }
]

/** 登录成功返回 token；账号错误/服务未启动返回 null（网络异常抛出，调用方 catch） */
export async function devLogin(loginName: string, password: string): Promise<string | null> {
  const r = await fetch('/mgmt-api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ loginName, password })
  })
  const j = await r.json()
  if (j.code === 0 && typeof j.data?.token === 'string') return j.data.token
  return null
}
