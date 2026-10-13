package com.viomanager.contest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.approval.ApprovalTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * 比赛模块验证：模板 CRUD / 模板引用保护(4012) / 比赛状态机(4011,4001) / 学生数据范围。
 * 继承 ApprovalTestBase 获容器装配 + 造人/建组织/登录辅助；@Transactional 每例回滚。
 */
@TestPropertySource(properties = "vio.audit.async=false")
class ContestServiceTest extends ApprovalTestBase {

    private static final long SEMESTER_ID = 9901L;
    private static final long ORG_ID = 9301L;

    private String superToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        insertOrg(ORG_ID, "软件学院", "/0/" + ORG_ID + "/");
        jdbcTemplate.update(
                "INSERT INTO semester (id, name, status, is_current, total_weeks) "
                        + "VALUES (?, ?, 'ACTIVE', 1, 20)",
                SEMESTER_ID, "2026-2027第一学期");
        superToken = createUserAndLogin("cs_super", "SUPER_ADMIN", null, null);
        studentToken = createUserAndLogin("cs_student", "STUDENT", ORG_ID, null);
    }

    /** 模板 CRUD 全流程：创建→详情→编辑→列表→删除 */
    @Test
    void templateCrud() throws Exception {
        //1.库里演示模板会污染列表计数，断言按 基线+本测试新增
        Integer tplBaseline = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contest_template WHERE deleted = 0", Integer.class);
        String json = mockMvc.perform(post("/api/contest-templates")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "cs-tpl-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"算法模板\",\"code\":\"ALGO\",\"category\":\"程序设计\","
                                + "\"defaultRules\":{\"memberLimit\":3,\"prefillLimit\":1}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("算法模板"))
                .andReturn().getResponse().getContentAsString();
        String tplId = readId(json);

        mockMvc.perform(get("/api/contest-templates/" + tplId)
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("算法模板"));

        mockMvc.perform(put("/api/contest-templates/" + tplId)
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "cs-tpl-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"算法模板V2\",\"code\":\"ALGO2\",\"category\":\"程序设计\","
                                + "\"defaultRules\":{\"memberLimit\":5,\"prefillLimit\":2}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("算法模板V2"));

        mockMvc.perform(get("/api/contest-templates")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value((tplBaseline == null ? 0 : tplBaseline) + 1));

        mockMvc.perform(delete("/api/contest-templates/" + tplId)
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "cs-tpl-delete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /** 模板被比赛引用时禁止删除（4012） */
    @Test
    void templateDeleteBlockedByReference() throws Exception {
        String tplJson = mockMvc.perform(post("/api/contest-templates")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "cs-ref-tpl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"引用模板\",\"code\":\"REF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        String tplId = readId(tplJson);

        mockMvc.perform(post("/api/contests")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "cs-ref-ct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"引用赛\",\"semesterId\":\"" + SEMESTER_ID + "\","
                                + "\"templateId\":\"" + tplId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(delete("/api/contest-templates/" + tplId)
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "cs-ref-del"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4012));
    }

    /** 比赛状态机：跳转(4011) / 发布前检查(4001) / 正常流转 */
    @Test
    void contestStatusMachine() throws Exception {
        String ctJson = mockMvc.perform(post("/api/contests")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "cs-sm-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"状态机赛\",\"semesterId\":\"" + SEMESTER_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        String ctId = readId(ctJson);

        // DRAFT→RUNNING 跳转 → 4011
        mockMvc.perform(post("/api/contests/" + ctId + "/status")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "cs-sm-skip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"RUNNING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4011));

        // DRAFT→PUBLISHED 缺项 → 4001
        mockMvc.perform(post("/api/contests/" + ctId + "/status")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "cs-sm-miss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"PUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4001));

        // 补全信息后 DRAFT→PUBLISHED → 成功
        mockMvc.perform(put("/api/contests/" + ctId)
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "cs-sm-fill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"状态机赛\",\"semesterId\":\"" + SEMESTER_ID + "\","
                                + "\"signupStart\":\"2026-11-01T00:00:00\","
                                + "\"signupEnd\":\"2026-11-30T23:59:59\","
                                + "\"contestStart\":\"2026-12-01T00:00:00\","
                                + "\"contestEnd\":\"2026-12-31T23:59:59\","
                                + "\"overrideRules\":{\"memberLimit\":5}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/contests/" + ctId + "/status")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "cs-sm-pub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"PUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // PUBLISHED→REGISTERING → 成功
        mockMvc.perform(post("/api/contests/" + ctId + "/status")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "cs-sm-reg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"REGISTERING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /** 学生仅可见 PUBLISHED 及之后状态的比赛 */
    @Test
    void studentDataScope() throws Exception {
        //1.先取基线再插数：演示比赛会污染计数，学生按可见口径、超管按全量，断言都是 基线+本测试新增
        Integer visibleBaseline = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contest WHERE deleted = 0 "
                        + "AND status IN ('PUBLISHED','REGISTERING','RUNNING','FINISHED','ARCHIVED')",
                Integer.class);
        Integer allBaseline = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contest WHERE deleted = 0", Integer.class);
        jdbcTemplate.update(
                "INSERT INTO contest (id, name, semester_id, org_id, status, source_type, deleted) "
                        + "VALUES (?, ?, ?, ?, 'DRAFT', 'MANUAL', 0)",
                4900101L, "草稿赛", SEMESTER_ID, ORG_ID);
        jdbcTemplate.update(
                "INSERT INTO contest (id, name, semester_id, org_id, status, source_type, deleted) "
                        + "VALUES (?, ?, ?, ?, 'PUBLISHED', 'MANUAL', 0)",
                4900102L, "已发布赛", SEMESTER_ID, ORG_ID);

        // 学生仅见 PUBLISHED（本测试新增可见 1 条：已发布赛）
        mockMvc.perform(get("/api/contests")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value((visibleBaseline == null ? 0 : visibleBaseline) + 1))
                .andExpect(jsonPath("$.data[0].name").value("已发布赛"));

        // 超管见全部（本测试新增 2 条：草稿赛 + 已发布赛）
        mockMvc.perform(get("/api/contests")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value((allBaseline == null ? 0 : allBaseline) + 2));
    }

    /** 从 JSON 响应中提取 data.id（Long 全局序列化为 String） */
    private String readId(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        return root.get("data").get("id").asText();
    }
}
