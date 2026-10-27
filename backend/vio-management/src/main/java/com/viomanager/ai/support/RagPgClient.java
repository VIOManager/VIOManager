package com.viomanager.ai.support;

import com.viomanager.ai.config.RagProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Array;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RAG 关键词路检索客户端（方案拆步 3）：pg_trgm + shingle 子串 ILIKE 兜中文关键词召回。
 * 与 RagVectorConfig 同款铁律——PG 连接绝不注册为 DataSource/JdbcTemplate Bean
 * （容器里出现第二个 DataSource 会让 MySQL 主库自动装配整体退出），故本类是
 * 由 RagVectorConfig 条件装配的普通 POJO，连接池自持，关容器经 close() 释放。
 * simple 分词对中文是整句单 token（pg-init 注记），tsv @@ tsquery 路形同虚设——
 * 关键词路实际走 shingle 子串 ILIKE，gin_trgm_ops 索引吃 ILIKE 加速，
 * 命中 shingle 数即粗排（RRF 只取排名，粗排够用）。PG 掉线时异常上抛由调用方降级。
 */
public class RagPgClient {

    private static final String SEARCH_SQL = """
            SELECT id::text AS id, content,
                   metadata->>'docId' AS doc_id, metadata->>'docTitle' AS doc_title,
                   (metadata->>'chunkIndex')::int AS chunk_index,
                   (SELECT COUNT(*) FROM unnest(?::text[]) AS p(w) WHERE content ILIKE w) AS hits
            FROM vector_store
            WHERE content ILIKE ANY(?::text[])
            ORDER BY hits DESC, id
            LIMIT ?
            """;

    private final HikariDataSource dataSource;

    private final JdbcTemplate jdbcTemplate;

    public RagPgClient(RagProperties rag) {
        RagProperties.Pg pg = rag.pg();
        HikariConfig pool = new HikariConfig();
        pool.setJdbcUrl("jdbc:postgresql://%s:%d/%s".formatted(pg.host(), pg.port(), pg.database()));
        pool.setUsername(pg.username());
        pool.setPassword(pg.password());
        pool.setMaximumPoolSize(2);
        pool.setPoolName("vio-rag-pg-kw");
        this.dataSource = new HikariDataSource(pool);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * 关键词路一行：docId/docTitle/chunkIndex 从 metadata JSON 抽出（出处随行带回，供 RRF 融合对齐）。
     * patterns 全部为已加 % 包裹的子串——参数化绑定，用户输入不拼 SQL。
     */
    public List<KwRow> keywordSearch(List<String> patterns, int limit) {
        String[] arr = patterns.toArray(new String[0]);
        return jdbcTemplate.query(SEARCH_SQL, ps -> {
            ps.setArray(1, createArray(ps, arr));
            ps.setArray(2, createArray(ps, arr));
            ps.setInt(3, limit);
        }, (rs, i) -> new KwRow(
                rs.getString("id"), rs.getString("content"),
                rs.getString("doc_id"), rs.getString("doc_title"),
                rs.getInt("chunk_index"), rs.getInt("hits")));
    }

    /** 关容器时释放池（@Bean destroyMethod 指名调用） */
    public void close() {
        dataSource.close();
    }

    private static Array createArray(java.sql.PreparedStatement ps, String[] values) throws SQLException {
        return ps.getConnection().createArrayOf("text", values);
    }

    /** 行结构：id 与向量路 Document id 同口径（uuid 文本），RRF 融合按它对齐去重 */
    public record KwRow(String id, String content, String docId, String docTitle, int chunkIndex, int hits) {
    }
}
