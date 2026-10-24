package com.viomanager.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.ai.entity.AiConversation;
import com.viomanager.ai.entity.AiMessage;
import com.viomanager.ai.mapper.AiConversationMapper;
import com.viomanager.ai.mapper.AiMessageMapper;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import jakarta.annotation.Resource;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI03-07 会话管理：新建（默认标题/provider=glm）/列表倒序与本人隔离/
 * 历史正序且滤 tool 角色/改标题/删除/非本人一律 9101/幂等键 1005。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiConversationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Resource
    private AiConversationMapper conversationMapper;

    @Resource
    private AiMessageMapper messageMapper;

    @Test
    void ai03CreateWithDefaultAndCustomTitle() throws Exception {
        String token = login("ai_conv_owner_a");
        MvcResult result = mockMvc.perform(post("/api/ai/conversations")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Idempotency-Key", "ai_conv_create_a1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Long id = Long.valueOf(JsonPath.read(result.getResponse().getContentAsString(), "$.data.id").toString());
        AiConversation conversation = conversationMapper.selectById(id);
        Assertions.assertEquals("新对话", conversation.getTitle());
        Assertions.assertEquals("glm", conversation.getProvider());
        Assertions.assertEquals(0, conversation.getMessageCount());
        Assertions.assertNotNull(conversation.getCreatedAt(), "createdAt 由 DB 默认值兜底");

        mockMvc.perform(post("/api/ai/conversations")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Idempotency-Key", "ai_conv_create_a2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" 我的收藏问题 \"}"))
                .andExpect(jsonPath("$.code").value(0));
        //同秒双建 created_at 排序不稳，按 title 精确断言而非取"最新一条"
        SysUser owner = sysUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysUser>()
                        .eq("login_name", "ai_conv_owner_a"));
        AiConversation named = conversationMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiConversation>()
                        .eq("user_id", owner.getId())
                        .eq("title", "我的收藏问题"));
        Assertions.assertNotNull(named, "自定义标题应落库");
    }

    @Test
    void ai04ListsOnlyOwnInUpdatedOrder() throws Exception {
        String ownerToken = login("ai_conv_owner_b");
        String strangerToken = login("ai_conv_stranger_b");
        Long first = createConversation(ownerToken, "ai_conv_b1", "第一个会话");
        Long second = createConversation(ownerToken, "ai_conv_b2", "第二个会话");
        createConversation(strangerToken, "ai_conv_b3", "陌生人的会话");
        //改第一个会话标题推高其 updated_at，断言倒序感知更新而非创建顺序
        mockMvc.perform(patch("/api/ai/conversations/" + first)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"被翻新的会话\"}"))
                .andExpect(jsonPath("$.code").value(0));
        MvcResult result = mockMvc.perform(get("/api/ai/conversations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2))
                .andReturn();
        List<String> titles = JsonPath.read(result.getResponse().getContentAsString(), "$.data.list[*].title");
        Assertions.assertEquals(List.of("被翻新的会话", "第二个会话"), titles, "应按更新时间倒序且不含他人会话");
        Assertions.assertNotEquals(second, first);
    }

    @Test
    void ai05MessagesAscAndToolRoleFiltered() throws Exception {
        String token = login("ai_conv_owner_c");
        Long conversationId = createConversation(token, "ai_conv_c1", "历史会话");
        insertMessage(conversationId, "user", "我这个月的排班是哪几天？", 0);
        insertMessage(conversationId, "assistant", "本月你共有 4 个班次。", 0);
        insertMessage(conversationId, "tool", "queryMySchedule", 0);
        insertMessage(conversationId, "assistant", "中断的尾巴", 1);
        MvcResult result = mockMvc.perform(get("/api/ai/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(3))
                .andReturn();
        List<String> roles = JsonPath.read(result.getResponse().getContentAsString(), "$.data.list[*].role");
        Assertions.assertEquals(List.of("user", "assistant", "assistant"), roles, "tool 消息不进前端历史且正序");
        List<Integer> incomplete = JsonPath.read(result.getResponse().getContentAsString(), "$.data.list[*].incomplete");
        Assertions.assertEquals(List.of(0, 0, 1), incomplete);
    }

    @Test
    void foreignConversationRejectedWith9101() throws Exception {
        String ownerToken = login("ai_conv_owner_d");
        String strangerToken = login("ai_conv_stranger_d");
        Long conversationId = createConversation(ownerToken, "ai_conv_d1", "主人的会话");
        mockMvc.perform(get("/api/ai/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(jsonPath("$.code").value(9101));
        mockMvc.perform(patch("/api/ai/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"偷改\"}"))
                .andExpect(jsonPath("$.code").value(9101));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/ai/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(jsonPath("$.code").value(9101));
        mockMvc.perform(get("/api/ai/conversations/999999999999/messages")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.code").value(9101));
    }

    @Test
    void ai07DeleteRemovesFromList() throws Exception {
        String token = login("ai_conv_owner_e");
        Long conversationId = createConversation(token, "ai_conv_e1", "待删会话");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/ai/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/ai/conversations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void createIdempotentKeyRejectedOnRepeat() throws Exception {
        String token = login("ai_conv_owner_f");
        MockHttpServletRequestBuilder request = post("/api/ai/conversations")
                .header("Authorization", "Bearer " + token)
                .header("X-Idempotency-Key", "ai_conv_idem_f1")
                .contentType(MediaType.APPLICATION_JSON);
        mockMvc.perform(request).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(request).andExpect(jsonPath("$.code").value(1005));
    }

    // ---------- 支撑 ----------

    private String login(String loginName) throws Exception {
        SysUser user = new SysUser();
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole("STUDENT");
        user.setRealName("AI会话-" + loginName);
        user.setDefaultPassword(0);
        user.setStatus(1);
        sysUserMapper.insert(user);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"pass123456\"}"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    private Long createConversation(String token, String idemKey, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ai/conversations")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andReturn();
        return Long.valueOf(JsonPath.read(result.getResponse().getContentAsString(), "$.data.id").toString());
    }

    private void insertMessage(Long conversationId, String role, String content, int incomplete) {
        messageMapper.insert(new AiMessage()
                .setConversationId(conversationId)
                .setRole(role)
                .setContent(content)
                .setIncomplete(incomplete));
    }
}
