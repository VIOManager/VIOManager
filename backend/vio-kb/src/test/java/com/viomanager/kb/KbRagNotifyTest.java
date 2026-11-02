package com.viomanager.kb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.viomanager.common.auth.JwtUtil;
import com.viomanager.kb.entity.KbDoc;
import com.viomanager.kb.support.RagNotifyClient;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RAG 薄通知钩子测试（拆步 2）：发布/编辑/下架/删除/重建五个钩子点的调用契约。
 * RagNotifyClient 整个换成 mock（不真连 AI 服务）；@DirtiesContext 防共享上下文被 mock 污染
 * （与 StringRedisTemplate mock 同款规矩）。夹具清理走 KbTestSupport 的"安全网"前缀。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}"
                + "/${KB_DATABASE:viomanager_kb}_test?useUnicode=true"
                + "&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
        "vio.audit.async=false"})
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class KbRagNotifyTest {

    @Resource
    private MockMvc mockMvc;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private KbTestSupport support;

    @Resource
    private ObjectMapper mapper;

    @MockitoBean
    private RagNotifyClient ragNotifyClient;

    private String adminToken;

    @BeforeEach
    void setUp() {
        adminToken = jwtUtil.createToken(90001L, "admin", "SUPER_ADMIN", null, "李管理", 0L);
    }

    @AfterEach
    void tearDown() {
        support.cleanup();
    }

    @Test
    void manualPublishShouldNotifyRag() throws Exception {
        MvcResult result = createDoc("""
                {"title":"安全网-组队指南","sourceType":"MANUAL","status":"PUBLISHED",
                 "content":"# 组队流程\\n正文"}""").andReturn();
        org.junit.jupiter.api.Assertions.assertEquals(0,
                ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.code")).intValue());
        ArgumentCaptor<KbDoc> captor = ArgumentCaptor.forClass(KbDoc.class);
        verify(ragNotifyClient).notifyDocAsync(captor.capture());
        org.junit.jupiter.api.Assertions.assertTrue(
                captor.getValue().getContent().startsWith("# 组队流程"),
                "MANUAL 正文应随通知带给 AI 切片");
    }

    @Test
    void manualWithoutContentShouldReject() throws Exception {
        createDoc("{\"title\":\"安全网-无正文\",\"sourceType\":\"MANUAL\",\"status\":\"PUBLISHED\"}")
                .andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(ragNotifyClient);
    }

    @Test
    void draftCreateShouldNotNotify() throws Exception {
        createDoc("{\"title\":\"安全网-草稿\",\"sourceType\":\"LINK\",\"linkUrl\":\"https://x.y\","
                + "\"status\":\"DRAFT\"}")
                .andExpect(jsonPath("$.code").value(0));
        verify(ragNotifyClient, never()).notifyDocAsync(any());
    }

    @Test
    void offlineShouldNotifyRemove() throws Exception {
        //先发布（会 notify 一次），再下架 → 应改为清索引通知
        MvcResult created = createDoc("{\"title\":\"安全网-下架测\",\"sourceType\":\"LINK\","
                + "\"linkUrl\":\"https://x.y\",\"status\":\"PUBLISHED\"}").andReturn();
        long docId = Long.parseLong(
                JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
        clearInvocations(ragNotifyClient);
        mockMvc.perform(put("/api/kb/docs/" + docId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFFLINE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(ragNotifyClient).notifyRemoveAsync(docId);
    }

    @Test
    void rebuildShouldPushPublishedDocs() throws Exception {
        //测试自足：先造一篇发布态文档（测试库种子数据无保证），再重建断言它被推送
        MvcResult created = createDoc("{\"title\":\"安全网-重建测\",\"sourceType\":\"LINK\","
                + "\"linkUrl\":\"https://x.y\",\"status\":\"PUBLISHED\"}").andReturn();
        long docId = Long.parseLong(
                JsonPath.read(created.getResponse().getContentAsString(), "$.data.id"));
        clearInvocations(ragNotifyClient);
        mockMvc.perform(post("/api/kb/admin/rag-rebuild")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total", greaterThanOrEqualTo(1)));
        ArgumentCaptor<KbDoc> pushed = ArgumentCaptor.forClass(KbDoc.class);
        verify(ragNotifyClient, atLeastOnce()).sendDocSync(pushed.capture());
        org.junit.jupiter.api.Assertions.assertTrue(
                pushed.getAllValues().stream().anyMatch(d -> d.getId() == docId),
                "新建的发布态文档应被重建推送");
    }

    private org.springframework.test.web.servlet.ResultActions createDoc(String json) throws Exception {
        return mockMvc.perform(post("/api/kb/docs")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Idempotency-Key", "test-kb-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }
}
