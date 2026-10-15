# VIOManager 团队论坛（forum/）

> 独立站：社区三模块（团队论坛 / 技术交流 / 知识库）2026-10-08 拍板迁出主站，本工程是其前端
> 视觉：图纸档案家族（与主站 front/ 同源令牌）；设计稿见 `.agentdocs/design-preview/forum-site/`

## 环境

- Node 24；npm 走 npmmirror；TypeScript 锁 ~5.9（vue-tsc 不兼容 TS7，与主站同规）

## 启动

```bash
npm i
cp .env.example .env.development
npm run dev   # http://localhost:5175
```

## 容器化运行（可选，不需要装 Node）

只想看效果/给别人演示时用；论坛后端尚未实现，容器默认烘焙演示数据（`USE_MOCK=true`），
不依赖后端即可完整浏览，后端就绪后 compose 里改 `false` 重建切真实接口：

```bash
docker compose up -d --build    # http://localhost:8092  # 8091 被 vio-kb 微服务占用
docker compose down             # 收场
```

## 与主站的关系

- **JWT 共享**：登录态读主站同一 localStorage 键 `vio_token`，论坛不设登录页；未登录进 /gate 引导回主站（VITE_MAIN_SITE_URL）
- 论坛服务独立库独立部署，接口由网关按路径转发（见《微服务拆分与论坛-架构调整方案》）

## 当前进度（骨架）

- 首页：版块 tab + 最新/最热/置顶 + 主题列表 + 热帖榜右栏（静态数据）
- 技术交流：分类/来源标注/收藏/置顶/订阅标签云（PRD §3.12 首发模块，静态数据）
- 帖子详情：主楼 + 楼层回复（引用条）+ 快速回复框（静态数据）
- 知识库：占位页（阶段三）
- TODO(API)：接口清单草案在 `.agentdocs/plans/2026-10-08/技术交流-接口清单.md`，确认后录 Apifox 再联调
