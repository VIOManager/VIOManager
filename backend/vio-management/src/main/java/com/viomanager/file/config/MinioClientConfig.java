package com.viomanager.file.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端装配。连接位取 application.yml 的 minio 段（Step 0 预留的约定）：
 * 本地默认 localhost:9000；开发者环境变量 MINIO_ENDPOINT 可覆盖。
 * ⚠️ 组长机器例外：Docker 镜像源不可用，MinIO 用 D:\minio 的本地 exe 起同端口同账号实例，
 * 应用配置零差异（见 code-changes Step 8 说明）。
 */
@Configuration
public class MinioClientConfig {

    @Bean
    public MinioClient minioClient(@Value("${minio.endpoint}") String endpoint,
                                    @Value("${minio.access-key}") String accessKey,
                                    @Value("${minio.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
