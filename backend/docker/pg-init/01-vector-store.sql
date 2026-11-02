-- RAG 向量库表结构（RAG 方案拆步 1）：与 Spring AI PgVectorStore 1.0.0 的建表 SQL 逐字对齐
-- （id uuid / content text / metadata json / embedding vector(768) + HNSW 索引 spring_ai_vector_index）。
-- 应用侧 initializeSchema(false)，建表责任在本脚本——容器卷首次初始化时执行一次，
-- 与 mysql-init 自动建 kb / vioforum 库同一套路；应用启动零 DDL、不建启动期连接。
-- tsv 生成列 + GIN 索引是方案第七节混合检索的关键词路（框架只有向量路，自己补）。
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS vector_store (
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content text,
    metadata json,
    embedding vector(768)
);
CREATE INDEX IF NOT EXISTS spring_ai_vector_index
    ON vector_store USING hnsw (embedding vector_cosine_ops);

-- 中文经 simple 分词会整句成单 token，检索质量若不行拆步 3 换 pg_trgm + ILIKE，列先落位
ALTER TABLE vector_store ADD COLUMN IF NOT EXISTS tsv
    tsvector GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED;
CREATE INDEX IF NOT EXISTS vector_store_tsv_idx ON vector_store USING gin (tsv);

-- 拆步 3 实况落位（2026-10-27）：simple 分词中文整句单 token 实锤，tsv @@ tsquery 形同虚设，
-- 关键词路实际走 shingle 子串 ILIKE——pg_trgm 扩展 + gin_trgm_ops 索引让 ILIKE 吃上索引。
-- 已存在的旧卷不会重跑本脚本，需手动对活库执行一次这三句（见实现记录拆步 3 节）。
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS vector_store_content_trgm_idx
    ON vector_store USING gin (content gin_trgm_ops);
