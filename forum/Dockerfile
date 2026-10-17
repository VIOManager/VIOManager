# 论坛站容器化：构建期 Node 编译静态产物，运行期 nginx 托管 + /api 同源反代
# ⚠️ 论坛后端（/api/forum、/api/tech-posts）尚未实现：容器默认烘焙演示数据模式（USE_MOCK=true，
#    不依赖后端即可完整浏览）；后端就绪后 compose 里把 USE_MOCK 改 false 重建即切真实接口
FROM node:24-alpine AS build
WORKDIR /app
RUN npm config set registry https://registry.npmmirror.com
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# 接口走同源相对路径（不设会回落写死的 localhost:8088 直连，容器里既是跨域又指错机器）
ENV VITE_API_BASE_URL=""
# 演示模式开关：true=内置假数据；切真实接口时构建参数传 false
ARG USE_MOCK=true
ENV VITE_USE_MOCK=$USE_MOCK
# 未登录引导页"回主站登录"链接（容器版主站默认 8090）
ARG MAIN_URL=http://localhost:8090/login
ENV VITE_MAIN_SITE_URL=$MAIN_URL
RUN npm run build

FROM nginx:alpine
COPY docker/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
