package com.viomanager.probe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Step 2 验证：ID 与基座字段。
 * 覆盖：插入自动填雪花 ID 与 created/updated 四字段、逻辑删除后默认查询不可见但行仍在、
 * Long 全局序列化为字符串。@Transactional 每例回滚，不在开发库留残渣。
 */
@SpringBootTest
@Transactional
class MybatisBaseTest {

    @Autowired
    private DictTypeProbeMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void insertShouldFillSnowflakeIdAndAuditFields() {
        SysDictTypeProbeEntity entity = new SysDictTypeProbeEntity();
        entity.setDictCode("PROBE_" + System.nanoTime());
        entity.setDictName("基座探针");

        mapper.insert(entity);

        // 雪花 ID 由 MyBatis-Plus 生成（19 位左右长整数）
        assertNotNull(entity.getId());
        assertTrue(entity.getId() > 0);
        // 审计四字段：时间字段由 AuditMetaObjectHandler 自动填充
        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
        // created_by / updated_by 留待 Step 4 接入 CurrentUser 后填充（列允许 NULL）
        assertNull(entity.getCreatedBy());
    }

    @Test
    void logicDeleteShouldHideRowButKeepData() {
        SysDictTypeProbeEntity entity = new SysDictTypeProbeEntity();
        entity.setDictCode("PROBE_DEL_" + System.nanoTime());
        entity.setDictName("逻辑删除探针");
        mapper.insert(entity);
        Long id = entity.getId();

        mapper.deleteById(id);

        // 默认查询不可见（@TableLogic 自动追加 deleted=0）
        assertNull(mapper.selectById(id));
        // 行还在库里，只是 deleted 置 1（软删保留历史）
        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM sys_dict_type WHERE id = ?", Integer.class, id);
        assertEquals(1, deleted);
    }

    @Test
    void longShouldSerializeAsString() throws Exception {
        // 规范 §2.1：雪花 ID 超 JS 安全整数，Long 出参一律字符串
        String json = objectMapper.writeValueAsString(java.util.Map.of("id", 123L));
        assertEquals("{\"id\":\"123\"}", json);
    }
}
