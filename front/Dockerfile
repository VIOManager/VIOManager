# 前端主站容器化：构建期 Node 编译静态产物，运行期 nginx 托管 + /api 同源反代
# ⚠️ 开发联调用 npm run dev（热更新）；本文件面向"不装 Node 一条命令跑起来看"与服务器部署预演
FROM node:24-alpine AS build
WORKDIR /app
# 国内镜像源加速拉包
RUN npm config set registry https://registry.npmmirror.com
# 先只拷依赖清单装包：源码改动不触发重装，利用镜像层缓存
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# 接口走同源相对路径（不设会回落写死的 localhost:8088 直连，容器里既是跨域又指错机器）
ENV VITE_API_BASE_URL=""
# 侧边栏"VIO 论坛"入口地址（容器版论坛站默认本机 8091，可用构建参数覆盖）
ARG FORUM_URL=http://localhost:8092
ENV VITE_FORUM_URL=$FORUM_URL
RUN npm run build

FROM nginx:alpine
COPY docker/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
