package com.viomanager.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 配置（方案拆步 1-2）：enabled 零装配开关 / internalToken 内部同步密钥 / pg 向量库连接。
 */
@ConfigurationProperties("vio.rag")
public record RagProperties(boolean enabled, String internalToken, Pg pg) {

    /** PG 连接参数：宿主默认 localhost:5433（compose 映射端口），容器内由环境变量覆盖为 postgres:5432 */
    public record Pg(String host, int port, String database, String username, String password) {
    }
}
