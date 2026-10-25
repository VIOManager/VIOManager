package com.viomanager.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.ai.support.GlmFakeUpstream;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI01 审计挂载（清单 §6）：每次 chat 异步写 bizType=AI，成功失败都记，
 * 摘要取首问前 50 字。同步审计模式断言（同事务回滚不留脏行）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "vio.audit.async=false")
class AiChatAuditTest {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.viomanager.ai.mapper.AiConversationMapper conversationMapper;

    @Test
    void chatSuccessAndRejectionBothAudited() throws Exception {
        String token = login("au_a");
        Long userId = userIdOf("ai_au_au_a");

        //受理成功：异步生成在测试事务外落不了审计？——审计切面在请求线程同步执行，success=1 已落
        MvcResult ok = mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"content\":\"这是一句超过五十个字的测试提问用来验证审计摘要只截取前五十个字符落库不超列宽的边界行为样例\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(ok)).andExpect(status().isOk());
        Integer successCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE biz_type='AI' AND user_id=? AND success=1", Integer.class, userId);
        Assertions.assertEquals(1, successCount, "成功受理应记一条 success=1 的 AI 审计");
        String summary = jdbcTemplate.queryForObject(
                "SELECT summary FROM audit_log WHERE biz_type='AI' AND user_id=? AND success=1 LIMIT 1",
                String.class, userId);
        Assertions.assertNotNull(summary);
        Assertions.assertTrue(summary.length() <= 50 + "AI对话：".length(), "摘要应截取首问前 50 字：" + summary);

        //预检拒绝：他人会话 → 9101，审计记 success=0（异常路径让切面记失败）
        String ownerToken = login("au_b");
        Long conversationId = insertConversation(userIdOf("ai_au_au_b"));
        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"conversationId\":\"" + conversationId + "\",\"content\":\"偷聊\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(9101));
        Integer rejectCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE biz_type='AI' AND user_id=? AND success=0", Integer.class, userId);
        Assertions.assertEquals(1, rejectCount, "预检拒绝应记一条 success=0 的 AI 审计");
    }

    // ---------- 支撑 ----------

    private String login(String suffix) throws Exception {
        String loginName = "ai_au_" + suffix;
        SysUser user = new SysUser();
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole("STUDENT");
        user.setRealName("AI审计-" + suffix);
        user.setDefaultPassword(0);
        user.setStatus(1);
        sysUserMapper.insert(user);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"pass123456\"}"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    private Long userIdOf(String loginName) {
        return sysUserMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysUser>()
                .eq("login_name", loginName)).getId();
    }

    private Long insertConversation(Long userId) {
        com.viomanager.ai.entity.AiConversation conversation = new com.viomanager.ai.entity.AiConversation()
                .setUserId(userId)
                .setTitle("新对话")
                .setProvider("glm")
                .setModel("glm-4-flash")
                .setMessageCount(0)
                .setArchived(0);
        conversationMapper.insert(conversation);
        return conversation.getId();
    }
}
