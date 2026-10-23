# 多阶段构建：构建层 Maven + JDK21 编译打包，运行层只留 JRE，最终镜像不含源码与依赖缓存。
# 一个镜像装三个服务的 fat jar（vio-management 主服务 8088 + vio-kb 知识库 8091 + vio-forum 论坛 8089），
# 由 compose 的 command 指定跑哪个 jar，一次构建三服务共用。
# 构建层与宿主同款两步走：先 install 底座 vio-common，再 package 各服务
# （直接从根 reactor package 会拿到旧底座 jar，骨架开发期踩过的坑不在容器里重踩）。

# ---- 构建层 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# 依赖走阿里云镜像（与宿主 D:/apache-maven settings.xml 同源），容器内不直连 central
COPY docker/maven-settings.xml /usr/share/maven/conf/settings.xml
# 先只拷 pom 预拉依赖：之后改源码重建时命中 Docker 层缓存，不重新下载全部依赖
COPY pom.xml ./
COPY vio-common/pom.xml vio-common/
COPY vio-management/pom.xml vio-management/
COPY vio-kb/pom.xml vio-kb/
COPY vio-forum/pom.xml vio-forum/
RUN mvn -B -q -pl vio-common,vio-management,vio-kb,vio-forum dependency:go-offline || true
# 再拷源码正式构建：容器是全新 .m2，全量 reactor 按依赖序构建、reactor 内解析底座——
# 宿主两步 install 先行的"陈旧 jar"陷阱只在热 .m2 上存在，这里不存在；
# -DskipTests：镜像构建不起测试库，测试仍在宿主跑
COPY vio-common vio-common
COPY vio-management vio-management
COPY vio-kb vio-kb
COPY vio-forum vio-forum
RUN mvn -B -q install -DskipTests

# ---- 运行层 ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# repackage 后 target 里各只有一个 .jar（原始包被改名 .jar.original）；改名区分两服务
COPY --from=build /build/vio-management/target/vio-management-*.jar management.jar
COPY --from=build /build/vio-kb/target/vio-kb-*.jar kb.jar
COPY --from=build /build/vio-forum/target/vio-forum-*.jar forum.jar
# 时区与 JDBC serverTimezone=Asia/Shanghai 对齐，容器内日志/审计时间不漂移
ENV TZ=Asia/Shanghai
# 默认跑主服务；kb/forum 服务在 compose 里用 command 覆盖 CMD（kb.jar / forum.jar）
ENTRYPOINT ["java", "-jar"]
CMD ["management.jar"]
