package com.viomanager.kb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.common.auth.JwtUtil;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 知识库安全网测试（对齐 C 线标准）：状态机非法流转、唯一键、计数一致性、越权封堵。
 * 只依赖 MySQL（本服务不碰 MinIO/ClamAV）；token 用 JwtUtil 直铸（本服务无登录端点）；
 * 数据夹具与清理见 KbTestSupport。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // 测试库 = 业务库名 + _test；默认对齐本机原生 MySQL（3306，root/root），可用环境变量覆盖
        "spring.datasource.url=jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}"
                + "/${KB_DATABASE:viomanager_kb}_test?useUnicode=true"
                + "&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
        "vio.audit.async=false"
})
class KbSafetyTest {

    private static final long ADMIN_UID = 90001L;
    private static final long STUDENT_UID = 90002L;
    private static final long OTHER_STUDENT_UID = 90003L;
    private static final long ORG_A = 9100L;
    private static final long ORG_B = 9200L;

    @Resource
    private MockMvc mockMvc;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private KbTestSupport support;

    private String adminToken;
    private String studentToken;
    private String otherStudentToken;
    private String deptAdminToken;

    @BeforeEach
    void setUp() {
        adminToken = jwtUtil.createToken(ADMIN_UID, "admin", "SUPER_ADMIN", null, "李管理", 0L);
        studentToken = jwtUtil.createToken(STUDENT_UID, "2023319024", "STUDENT", ORG_A, "王同学", 0L);
        otherStudentToken = jwtUtil.createToken(OTHER_STUDENT_UID, "2023319005", "STUDENT", ORG_A, "周同学", 0L);
        deptAdminToken = jwtUtil.createToken(ADMIN_UID + 10, "dept01", "DEPT_ADMIN", ORG_A, "陈管理", 0L);
    }

    @AfterEach
    void tearDown() {
        support.cleanup();
    }

    /** 未登录 401 + 1002（前端跳登录契约） */
    @Test
    void unauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/kb/docs")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    /** 学生看不到非 PUBLISHED 文档（9002，不存在与不可见同文案） */
    @Test
    void studentCannotViewDraft() throws Exception {
        long docId = support.insertDoc(KbTestSupport.PREFIX + "-草稿", "DRAFT", ORG_A);
        mockMvc.perform(get("/api/kb/docs/{id}", docId).header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(9002));
    }

    /** KB02 详情浏览数原子 +1，库里同步落（直更库口径） */
    @Test
    void detailIncreasesViewCount() throws Exception {
        long docId = support.insertDoc(KbTestSupport.PREFIX + "-浏览", "PUBLISHED", ORG_A);
        mockMvc.perform(get("/api/kb/docs/{id}", docId).header("Authorization", bearer(studentToken)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.viewCount").value(1));
        assertEquals(1, support.count("kb_doc", "view_count", docId));
    }

    /** KB05 收藏：成功 +1；同幂等键重放 1005；换键重复收藏 9001 且计数不虚增 */
    @Test
    void collectIdempotencyAndUniqueKey() throws Exception {
        long docId = support.insertDoc(KbTestSupport.PREFIX + "-收藏", "PUBLISHED", ORG_A);
        mockMvc.perform(withKey(post("/api/kb/docs/{id}/collect", docId), "test-kb-c1")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(withKey(post("/api/kb/docs/{id}/collect", docId), "test-kb-c1")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(jsonPath("$.code").value(1005));
        mockMvc.perform(withKey(post("/api/kb/docs/{id}/collect", docId), "test-kb-c2")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(jsonPath("$.code").value(9001));
        assertEquals(1, support.count("kb_doc", "collect_count", docId));
    }

    /** KB06 取消收藏幂等：二次取消不报错，计数已回 0 */
    @Test
    void uncollectIsIdempotent() throws Exception {
        long docId = support.insertDoc(KbTestSupport.PREFIX + "-取消", "PUBLISHED", ORG_A);
        mockMvc.perform(withKey(post("/api/kb/docs/{id}/collect", docId), "test-kb-u1")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(delete("/api/kb/docs/{id}/collect", docId)
                        .header("Authorization", bearer(studentToken)))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(delete("/api/kb/docs/{id}/collect", docId)
                        .header("Authorization", bearer(studentToken)))
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(0, support.count("kb_doc", "collect_count", docId));
    }

    /** KB09/KB10：存量计数一致；他人删评 1003；作者删评成功且计数回退 */
    @Test
    void commentDeletePermissionAndCount() throws Exception {
        long docId = support.insertDoc(KbTestSupport.PREFIX + "-评论", "PUBLISHED", ORG_A);
        long commentId = support.insertComment(docId, STUDENT_UID, "王同学的评论");
        assertEquals(1, support.count("kb_doc", "comment_count", docId));

        mockMvc.perform(delete("/api/kb/comments/{id}", commentId)
                        .header("Authorization", bearer(otherStudentToken)))
                .andExpect(jsonPath("$.code").value(1003));
        assertEquals(1, support.count("kb_doc", "comment_count", docId));

        mockMvc.perform(delete("/api/kb/comments/{id}", commentId)
                        .header("Authorization", bearer(studentToken)))
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(0, support.count("kb_doc", "comment_count", docId));
    }

    /** KB11 管理员隐藏/恢复：计数随可见口径 ±1；学生调用 1003 */
    @Test
    void hideCommentAdjustsCount() throws Exception {
        long docId = support.insertDoc(KbTestSupport.PREFIX + "-隐藏", "PUBLISHED", ORG_A);
        long commentId = support.insertComment(docId, STUDENT_UID, "待隐藏的评论");

        mockMvc.perform(hide(commentId, studentToken, true))
                .andExpect(jsonPath("$.code").value(1003));
        mockMvc.perform(hide(commentId, adminToken, true))
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(0, support.count("kb_doc", "comment_count", docId));

        mockMvc.perform(hide(commentId, adminToken, false))
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(1, support.count("kb_doc", "comment_count", docId));
    }

    /** KB16 状态机：DRAFT→OFFLINE 拒绝（1006）；合法链路 DRAFT→PUBLISHED→OFFLINE 通过 */
    @Test
    void statusMachineGuardsJumps() throws Exception {
        long docId = support.insertDoc(KbTestSupport.PREFIX + "-状态机", "DRAFT", ORG_A);
        mockMvc.perform(putStatus(docId, adminToken, "OFFLINE"))
                .andExpect(jsonPath("$.code").value(1006));
        mockMvc.perform(putStatus(docId, adminToken, "PUBLISHED"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(putStatus(docId, adminToken, "OFFLINE"))
                .andExpect(jsonPath("$.code").value(0));
    }

    /** KB12 学生发布 1003；管理员带幂等键发布成功且学生可读（新契约：LINK 二选一形态） */
    @Test
    void createRequiresAdmin() throws Exception {
        String body = "{\"title\":\"" + KbTestSupport.PREFIX + "-新文档\",\"sourceType\":\"LINK\","
                + "\"linkUrl\":\"https://example.com/kb\"}";
        mockMvc.perform(withKey(post("/api/kb/docs"), "test-kb-cr1")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(1003));

        String resp = mockMvc.perform(withKey(post("/api/kb/docs"), "test-kb-cr2")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        long newId = Long.parseLong(JsonPath.read(resp, "$.data.id"));
        mockMvc.perform(get("/api/kb/docs/{id}", newId).header("Authorization", bearer(studentToken)))
                .andExpect(jsonPath("$.code").value(0));
    }

    /** KB12 二选一校验：非法形态 / LINK 缺链接 / FILE 缺 fileId、fileName 均 1001（拍板口径） */
    @Test
    void createValidatesBinaryChoice() throws Exception {
        postCreate(adminToken, "test-kb-v1", "{\"title\":\"" + KbTestSupport.PREFIX + "-形态\","
                        + "\"sourceType\":\"MANUAL\"}")
                .andExpect(jsonPath("$.code").value(1001));
        postCreate(adminToken, "test-kb-v2", "{\"title\":\"" + KbTestSupport.PREFIX + "-缺链\","
                        + "\"sourceType\":\"LINK\"}")
                .andExpect(jsonPath("$.code").value(1001));
        postCreate(adminToken, "test-kb-v3", "{\"title\":\"" + KbTestSupport.PREFIX + "-缺文件\","
                        + "\"sourceType\":\"FILE\"}")
                .andExpect(jsonPath("$.code").value(1001));
        postCreate(adminToken, "test-kb-v4", "{\"title\":\"" + KbTestSupport.PREFIX + "-缺文件名\","
                        + "\"sourceType\":\"FILE\",\"fileId\":123}")
                .andExpect(jsonPath("$.code").value(1001));
    }

    /** KB12 FILE 形态：返回 fileId/fileName/publisherName（JWT realName 快照冗余，V2 列） */
    @Test
    void createFileDocReturnsFileFields() throws Exception {
        String resp = mockMvc.perform(withKey(post("/api/kb/docs"), "test-kb-f1")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + KbTestSupport.PREFIX + "-文件条目\",\"sourceType\":\"FILE\","
                                + "\"fileId\":99001,\"fileName\":\"赛题分析.pdf\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.fileId").value("99001"))
                .andExpect(jsonPath("$.data.fileName").value("赛题分析.pdf"))
                .andExpect(jsonPath("$.data.publisherName").value("李管理"))
                .andExpect(jsonPath("$.data.pinned").value(false))
                .andReturn().getResponse().getContentAsString();
        long newId = Long.parseLong(JsonPath.read(resp, "$.data.id"));
        mockMvc.perform(get("/api/kb/docs/{id}", newId).header("Authorization", bearer(studentToken)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.favorited").value(false))
                .andExpect(jsonPath("$.data.fileId").value("99001"));
    }

    /** KB13 乐观锁：带过期 updated_at 编辑 → 1007（规范预留码） */
    @Test
    void updateWithStaleUpdatedAtGets1007() throws Exception {
        long docId = support.insertDoc(KbTestSupport.PREFIX + "-乐观锁", "PUBLISHED", ORG_A);
        String body = "{\"title\":\"" + KbTestSupport.PREFIX + "-乐观锁-改\",\"updatedAt\":\"2020-01-01 00:00:00\"}";
        mockMvc.perform(put("/api/kb/docs/{id}", docId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(1007));
    }

    /** KB17 部门管理员范围：只见本组织文档（v1 精确匹配口径） */
    @Test
    void deptAdminSeesOnlyOwnOrg() throws Exception {
        support.insertDoc(KbTestSupport.PREFIX + "-本部门", "PUBLISHED", ORG_A);
        support.insertDoc(KbTestSupport.PREFIX + "-他部门", "PUBLISHED", ORG_B);
        mockMvc.perform(get("/api/kb/admin/docs").header("Authorization", bearer(deptAdminToken)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list.length()").value(1))
                .andExpect(jsonPath("$.data.list[0].title").value(KbTestSupport.PREFIX + "-本部门"));
    }

    /** KB01 学生浏览正常返回（@DataScope(ALL) 生效——漏声明会被默认拒绝，防退化） */
    @Test
    void studentCanBrowsePublishedList() throws Exception {
        support.insertDoc(KbTestSupport.PREFIX + "-列表", "PUBLISHED", ORG_B);
        mockMvc.perform(get("/api/kb/docs").header("Authorization", bearer(studentToken)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ---------- 请求构造辅助 ----------

    private MockHttpServletRequestBuilder withKey(MockHttpServletRequestBuilder builder, String key) {
        return builder.header("X-Idempotency-Key", key);
    }

    /** 发布请求统一执行（二选一校验用例共用） */
    private ResultActions postCreate(String token, String key, String body) throws Exception {
        return mockMvc.perform(withKey(post("/api/kb/docs"), key)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private MockHttpServletRequestBuilder hide(long commentId, String token, boolean hidden) {
        return put("/api/kb/comments/{id}/hide", commentId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hidden\": " + hidden + "}");
    }

    private MockHttpServletRequestBuilder putStatus(long docId, String token, String target) {
        return put("/api/kb/docs/{id}/status", docId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"" + target + "\"}");
    }
}
