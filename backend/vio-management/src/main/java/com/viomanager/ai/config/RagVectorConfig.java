package com.viomanager.ai.config;

import com.viomanager.ai.support.RagPgClient;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RAG 向量库装配（方案拆步 1）。三个刻意的架构决策：
 * ① PG 连接不暴露为 DataSource/JdbcTemplate Bean——Spring Boot 的数据源自动装配是
 *   @ConditionalOnMissingBean(DataSource.class)，容器里出现第二个 DataSource 会让 MySQL
 *   主库的自动装配整体退出（MyBatis/Flyway/JdbcTemplate 全断）；向量库连接只在 VectorStore 内部自持。
 * ② 手动 build 而非 starter 自动装配——自动装配会抓主库 JdbcTemplate（MySQL）去建向量表。
 *   它的 @ConditionalOnMissingBean 按方法返回的具体类型判断：本 Bean 必须声明返回 PgVectorStore
 *   才被它看见而退让（声明接口 VectorStore 时类型检查不可见，撞名 BeanDefinitionOverrideException）；
 *   自动装配类本身另由 yml 的 spring.ai.vectorstore.type=none 整体关闭——否则 vio.rag.enabled=false
 *   时它会装配出连 MySQL 主库的假 VectorStore，RAG 零装配语义被破坏。
 * ③ vio.rag.enabled=false 时零装配（RocketMQ 同款）：不建任何 PG Bean，没起 postgres 容器
 *   不挡应用启动与全量测试；initializeSchema(false)——建表责任在 compose 的 pg-init 脚本
 *   （含混合检索 tsv 列），应用启动不做 DDL 不建连接，PG 掉线只在真正读写向量时暴露。
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagVectorConfig {

    /** nomic-embed-text 输出维度，与 pg-init 脚本的 vector(768) 严格一致 */
    private static final int EMBEDDING_DIMENSIONS = 768;

    @Bean
    @ConditionalOnProperty(name = "vio.rag.enabled", havingValue = "true")
    public PgVectorStore vectorStore(RagProperties rag, EmbeddingModel embeddingModel) {
        RagProperties.Pg pg = rag.pg();
        HikariConfig pool = new HikariConfig();
        pool.setJdbcUrl("jdbc:postgresql://%s:%d/%s".formatted(pg.host(), pg.port(), pg.database()));
        pool.setUsername(pg.username());
        pool.setPassword(pg.password());
        pool.setMaximumPoolSize(4);
        pool.setPoolName("vio-rag-pg");
        // 池由 VectorStore 生命周期自持、不注册为 Bean（见类注释①的代价）：进程退出连接随 TCP 断开释放，
        // 常驻开销 = 池上限 4 连接，对 PG 默认 100 连接上限无压力
        HikariDataSource dataSource = new HikariDataSource(pool);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(EMBEDDING_DIMENSIONS)
                .initializeSchema(false)
                .build();
    }

    /**
     * 关键词路检索客户端（拆步 3）：同为 vio.rag.enabled 条件装配的普通 POJO——
     * 类型不是 DataSource/JdbcTemplate，不触发主库自动装配的退让判断（类注释①同款约束）。
     * 池上限 2（关键词路每次对话至多一查），destroyMethod 关容器时释放。
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "vio.rag.enabled", havingValue = "true")
    public RagPgClient ragPgClient(RagProperties rag) {
        return new RagPgClient(rag);
    }
}
