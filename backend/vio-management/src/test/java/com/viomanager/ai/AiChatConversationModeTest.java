package com.viomanager.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.viomanager.ai.support.GlmFakeUpstream;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * AI01 会话模式 + 中断落库（接口清单 §4/§5）。
 * ⚠️ 不挂 @Transactional：chat 落库在异步线程（跨事务不可见未提交数据），
 * 一切写入（用户/会话/消息）autocommit 直进开发库，@AfterEach 按 login_name 前缀物理清理。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AiChatConversationModeTest {

    private static final String USER_PREFIX = "ai_conv_mode_";

    private static final ObjectMapper JSON = new ObjectMapper();

    private static GlmFakeUpstream upstream;

    @BeforeAll
    static void startUpstream() throws Exception {
        upstream = GlmFakeUpstream.shared();
    }

    @DynamicPropertySource
    static void glmProperties(DynamicPropertyRegistry registry) {
        registry.add("vio.ai.api-key", () -> "test-dummy-key-not-real");
        registry.add("vio.ai.base-url", upstream::baseUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private com.viomanager.ai.mapper.AiConversationMapper conversationMapper;

    @Autowired
    private com.viomanager.ai.mapper.AiMessageMapper messageMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanupAutocommittedRows() {
        jdbcTemplate.update("DELETE FROM ai_message WHERE conversation_id IN "
                + "(SELECT id FROM ai_conversation WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE login_name LIKE ?))", USER_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM ai_conversation WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE login_name LIKE ?)", USER_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_user WHERE login_name LIKE ?", USER_PREFIX + "%");
    }

    @Test
    void persistsMessagesCountAndTitle() throws Exception {
        String token = login("a");
        Long conversationId = insertConversation("a");
        String body = performChat(token, "介绍下排班功能", conversationId);
        Assertions.assertTrue(body.contains("event:done"), body);
        Assertions.assertTrue(body.contains("\"conversationId\":\"" + conversationId + "\""),
                "meta 应回显会话 id");

        List<com.viomanager.ai.entity.AiMessage> messages = messagesOf(conversationId);
        Assertions.assertEquals(2, messages.size(), "user + assistant 各一条");
        Assertions.assertEquals("user", messages.get(0).getRole());
        Assertions.assertEquals("介绍下排班功能", messages.get(0).getContent());
        Assertions.assertEquals("assistant", messages.get(1).getRole());
        Assertions.assertEquals("你好，平台", messages.get(1).getContent());
        Assertions.assertEquals(0, messages.get(1).getIncomplete(), "正常完成不标中断");
        Assertions.assertEquals(12, messages.get(1).getPromptTokens());
        Assertions.assertEquals(5, messages.get(1).getCompletionTokens());
        Assertions.assertNotNull(messages.get(1).getCostMillis());

        com.viomanager.ai.entity.AiConversation conversation = conversationMapper.selectById(conversationId);
        Assertions.assertEquals(2, conversation.getMessageCount());
        Assertions.assertNotNull(conversation.getLastMessageAt());
        Assertions.assertEquals("介绍下排班功能", conversation.getTitle(), "首问应改写默认标题");
    }

    @Test
    void longQuestionTitleTruncatedToTwentyChars() throws Exception {
        String token = login("b");
        Long conversationId = insertConversation("b");
        String longQuestion = "这是一段超过二十个字符的超长提问用来验证标题截取逻辑是否按拍板口径执行";
        performChat(token, longQuestion, conversationId);
        Assertions.assertEquals(20, conversationMapper.selectById(conversationId).getTitle().length());
    }

    @Test
    void historyAppendedToNextRequest() throws Exception {
        String token = login("c");
        Long conversationId = insertConversation("c");
        performChat(token, "第一问", conversationId);
        performChat(token, "第二问", conversationId);
        JsonNode messages = JSON.readTree(upstream.lastRequestBody()).get("messages");
        Assertions.assertEquals(4, messages.size(), "system + 第一轮 user/assistant + 本次提问");
        Assertions.assertEquals("system", messages.get(0).get("role").asText());
        Assertions.assertEquals("第一问", messages.get(1).get("content").asText());
        Assertions.assertEquals("assistant", messages.get(2).get("role").asText());
        Assertions.assertEquals("第二问", messages.get(3).get("content").asText());
    }

    @Test
    void truncatedReplyPersistedAsIncomplete() throws Exception {
        String token = login("d");
        Long conversationId = insertConversation("d");
        String body = performChat(token, GlmFakeUpstream.TRUNCATE_KEYWORD + " 超长问题", conversationId);
        Assertions.assertTrue(body.contains("\"finishReason\":\"interrupted\""), body);
        List<com.viomanager.ai.entity.AiMessage> messages = messagesOf(conversationId);
        Assertions.assertEquals(2, messages.size());
        Assertions.assertEquals(1, messages.get(1).getIncomplete(), "截断的 assistant 应标 incomplete=1");
        Assertions.assertEquals("被截断的", messages.get(1).getContent(), "已生成部分照常落库");
    }

    @Test
    void upstreamFailurePersistsNothing() throws Exception {
        String token = login("e");
        Long conversationId = insertConversation("e");
        String body = performChat(token, GlmFakeUpstream.FAIL_KEYWORD + " 触发故障", conversationId);
        Assertions.assertTrue(body.contains("event:error"), body);
        Assertions.assertEquals(0, messagesOf(conversationId).size(), "无产出不落库：不留问了没答的孤儿提问");
        Assertions.assertEquals(0, conversationMapper.selectById(conversationId).getMessageCount());
    }

    @Test
    void foreignConversationRejectedWith9101() throws Exception {
        String strangerToken = login("f");
        login("g");  // 会话主人也建号（token 不用，只为让 insertConversation 查得到人）
        Long conversationId = insertConversation("g");
        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"conversationId\":\"" + conversationId + "\",\"content\":\"偷聊\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(9101));
        Assertions.assertEquals(0, messagesOf(conversationId).size());
    }

    // ---------- 支撑 ----------

    private String performChat(String token, String content, Long conversationId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"conversationId\":\"" + conversationId + "\",\"content\":\"" + content + "\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String login(String suffix) throws Exception {
        String loginName = USER_PREFIX + suffix;
        SysUser user = new SysUser();
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole("STUDENT");
        user.setRealName("AI会话模式-" + suffix);
        user.setDefaultPassword(0);
        user.setStatus(1);
        sysUserMapper.insert(user);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"pass123456\"}"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    private Long insertConversation(String suffix) {
        SysUser user = sysUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysUser>()
                        .eq("login_name", USER_PREFIX + suffix));
        com.viomanager.ai.entity.AiConversation conversation = new com.viomanager.ai.entity.AiConversation()
                .setUserId(user.getId())
                .setTitle("新对话")
                .setProvider("glm")
                .setModel("glm-4-flash")
                .setMessageCount(0)
                .setArchived(0);
        conversationMapper.insert(conversation);
        return conversation.getId();
    }

    private List<com.viomanager.ai.entity.AiMessage> messagesOf(Long conversationId) {
        return messageMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.viomanager.ai.entity.AiMessage>()
                                .eq("conversation_id", conversationId))
                .stream()
                .sorted(Comparator.comparing(com.viomanager.ai.entity.AiMessage::getId))
                .toList();
    }
}
