-- 论坛独立库（论坛-独立库与骨架-设计方案 §二.4）：
-- MySQL 容器首次初始化时由 /docker-entrypoint-initdb.d 执行，建论坛空库；
-- 表由 vio-forum 服务的 Flyway（V1__forum_init.sql）自建。
-- ⚠️ 只在数据卷为空的首次初始化执行：已有 vio-mysql-data 卷的开发机需手工执行一次本文件
--（CREATE DATABASE 幂等，重复执行无害）。
CREATE DATABASE IF NOT EXISTS vioforum DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
