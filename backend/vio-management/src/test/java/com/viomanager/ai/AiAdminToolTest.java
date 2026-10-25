package com.viomanager.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.ai.tool.AiTool;
import com.viomanager.ai.tool.AiToolRegistry;
import com.viomanager.ai.tool.AiToolRoles;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理域五个工具（清单 §2 补全）直调测试：不走 SSE 全链路（已有 AiChatToolCallTest 覆盖），
 * 直接 execute 验证三件事——角色门（学生拿无权限 JSON）、真实服务翻译、注册表 8 工具齐。
 * UserHolder 由测试手工挂/摘：TeamQueryServiceImpl 等从 ThreadLocal 读当前用户做数据范围。
 */
@SpringBootTest
@Transactional
class AiAdminToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private AiToolRegistry registry;

    /** 注入的是 bean 名 → Bean 映射，须按工具 name() 重建（bean 名是 queryMembersTool 这类） */
    @Autowired
    private List<AiTool> toolBeans;

    private Map<String, AiTool> toolsByName;

    private final CurrentUser student = user("STUDENT");
    private final CurrentUser superAdmin = user("SUPER_ADMIN");

    private static CurrentUser user(String role) {
        CurrentUser u = new CurrentUser();
        u.setUserId(1L);
        u.setLoginName("tool_test");
        u.setRealName("工具测试");
        u.setRole(role);
        return u;
    }

    @BeforeEach
    void setUp() {
        toolsByName = toolBeans.stream()
                .collect(Collectors.toMap(AiTool::name, Function.identity()));
        UserHolder.saveUser(superAdmin);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    // ---------- 注册表 ----------

    @Test
    void whitelistShouldHoldEightTools() {
        List<String> expected = List.of("queryMySchedule", "queryMyAchievements", "queryMyWeeklyPerformance",
                "queryMembers", "queryTeam", "queryTeamsMissingMaterials",
                "queryTeamAchievements", "queryFreeSlots");
        for (String name : expected) {
            Assertions.assertTrue(registry.byName(name).isPresent(), "白名单应含工具：" + name);
        }
        Assertions.assertEquals(expected.size(), toolsByName.size(),
                "白名单应恰为 8 个工具，实际：" + toolsByName.keySet());
    }

    // ---------- 角色门：学生被拒，拿到可转述的错误 JSON ----------

    @Test
    void studentShouldBeDeniedOnAdminTools() throws Exception {
        for (String name : List.of("queryMembers", "queryTeamsMissingMaterials", "queryFreeSlots")) {
            String result = toolsByName.get(name).execute(emptyArgs(), student);
            Assertions.assertEquals(AiToolRoles.DENIED_JSON, result, name + " 应拒学生");
        }
    }

    // ---------- 正常执行：超管身份真实跑通各服务 ----------

    @Test
    void superAdminShouldQueryMembers() throws Exception {
        String result = toolsByName.get("queryMembers").execute(emptyArgs(), superAdmin);
        JsonNode node = MAPPER.readTree(result);
        Assertions.assertEquals(0, node.path("code").asInt(), "人员分页应 code=0：" + result);
        Assertions.assertTrue(node.has("data"), "应返回 data 结构");
    }

    @Test
    void superAdminShouldListTeams() throws Exception {
        String result = toolsByName.get("queryTeam").execute(emptyArgs(), superAdmin);
        JsonNode node = MAPPER.readTree(result);
        Assertions.assertEquals(0, node.path("teams").path("code").asInt(), "赛队列表应 code=0：" + result);
    }

    @Test
    void superAdminShouldQueryMissingMaterials() throws Exception {
        String result = toolsByName.get("queryTeamsMissingMaterials").execute(emptyArgs(), superAdmin);
        JsonNode node = MAPPER.readTree(result);
        Assertions.assertTrue(node.has("missingTeams") && node.path("total").isInt(),
                "应返回未交齐队伍结构：" + result);
    }

    @Test
    void superAdminShouldQueryAchievements() throws Exception {
        String result = toolsByName.get("queryTeamAchievements").execute(emptyArgs(), superAdmin);
        JsonNode node = MAPPER.readTree(result);
        Assertions.assertEquals(0, node.path("code").asInt(), "成果列表应 code=0：" + result);
    }

    @Test
    void superAdminShouldGetFreeSlotErrorWithoutWeekNo() throws Exception {
        // 不给 weekNo：service 报周次越界，异常由 Flow 兜底转 JSON——直调层验证异常可抛出即可
        Assertions.assertThrows(Exception.class,
                () -> toolsByName.get("queryFreeSlots").execute(emptyArgs(), superAdmin));
    }

    // ---------- 支撑 ----------

    private JsonNode emptyArgs() {
        return MAPPER.createObjectNode();
    }
}
