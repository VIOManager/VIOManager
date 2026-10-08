# VIOManager 前端（front/）

> 视觉定调：方向一「图纸档案」（组长 2026-10-08 拍板）——令牌见 `src/styles/tokens.css`

## 环境

- Node 24；npm 已配 npmmirror 镜像
- 构建工具链：Vite 8 + Vue 3.5 + TypeScript **~5.9**（⚠️ 勿升 TS 7，vue-tsc 暂不兼容）+ Element Plus + Pinia + Vue Router

## 启动

```bash
npm i
cp .env.example .env.development   # 按需改 VITE_API_BASE_URL（Apifox Mock / 真实后端）
npm run dev                        # http://localhost:5174
```

## 验证

- `npm run build`：vue-tsc 类型检查 + 构建，须零错误
- 顶栏"角色切换"下拉为开发调试件，可现场验证三端菜单显隐（超管 17 / 部门管理员 15 / 学生 10），联调后移除

## 当前进度

- 已完成：工程初始化、布局壳（目录式侧边栏 + 顶栏）、路由守卫（无 token 跳登录）、三端菜单配置表、登录页（图纸档案视觉，**登录桩**）、概览页版式、全模块占位页
- 未做（后续步骤）：axios 封装（Step 1）、真实登录/改密（Step 3，待 Apifox）、字典 hook 与字段裁剪（Step 4）、上传组件（Step 5）、审批中心（Step 6）、构建收尾（Step 7）
- 已知项：Element Plus 全量引入导致首包约 1MB，Step 7 前改按需引入
