package com.viomanager.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viomanager.ai.tool.AiTool;
import com.viomanager.ai.tool.AiToolRegistry;
import com.viomanager.ai.tool.AiToolRoles;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.exception.BizException;
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
    void whitelistShouldHoldSixteenTools() {
        List<String> expected = List.of("queryMySchedule", "queryMyAchievements", "queryMyWeeklyPerformance",
                "queryMembers", "queryTeam", "queryTeamsMissingMaterials",
                "queryTeamAchievements", "queryFreeSlots", "queryMemberTeams",
                "queryContests", "queryProjects", "queryOrgStructure",
                "queryMyApprovals", "querySemesterInfo", "queryScheduleTable",
                "searchKb");
        for (String name : expected) {
            Assertions.assertTrue(registry.byName(name).isPresent(), "白名单应含工具：" + name);
        }
        Assertions.assertEquals(expected.size(), toolsByName.size(),
                "白名单应恰为 16 个工具，实际：" + toolsByName.keySet());
    }

    // ---------- 角色门：学生被拒，拿到可转述的错误 JSON ----------

    @Test
    void studentShouldBeDeniedOnAdminTools() throws Exception {
        for (String name : List.of("queryMembers", "queryTeamsMissingMaterials", "queryFreeSlots",
                "queryMemberTeams", "queryProjects", "queryOrgStructure", "queryScheduleTable")) {
            String result = toolsByName.get(name).execute(emptyArgs(), student);
            Assertions.assertEquals(AiToolRoles.DENIED_JSON, result, name + " 应拒学生");
        }
    }

    // ---------- 学生可用的本人域/公开域工具 ----------

    @Test
    void searchKbShouldFailFastWhenRagDisabled() throws Exception {
        //默认测试上下文 vio.rag.enabled=false（零装配）：searchKb fail-fast 抛 9105，
        //由 AiChatFlow 兜底转 {"error":...} 给模型转述——工具注册不依赖 RAG 开关，但检索入口拒绝静默空答
        JsonNode args = MAPPER.createObjectNode().put("question", "怎么加队伍");
        BizException e = Assertions.assertThrows(BizException.class,
                () -> toolsByName.get("searchKb").execute(args, student));
        Assertions.assertEquals(9105, e.getCode(), "RAG 未启用应抛 9105（AI_RAG_NOT_ENABLED）");
    }

    @Test
    void studentShouldUsePublicAndSelfTools() throws Exception {
        //service 层按 UserHolder 判身份，学生域工具须以学生身份真实执行
        UserHolder.saveUser(student);
        try {
            String contests = toolsByName.get("queryContests").execute(emptyArgs(), student);
            Assertions.assertEquals(0, MAPPER.readTree(contests).path("code").asInt(),
                    "学生应可查比赛列表：" + contests);

            String semester = toolsByName.get("querySemesterInfo").execute(emptyArgs(), student);
            JsonNode semesterNode = MAPPER.readTree(semester);
            Assertions.assertTrue(semesterNode.has("currentSemester") || semesterNode.has("error"),
                    "学期信息应有当前学期或无学期说明：" + semester);
        } finally {
            UserHolder.saveUser(superAdmin);
        }
        //审批 MINE 视角要求人员档案绑定：合成 userId=1 无档案，借演示学生 stu_wang(204) 验真
        CurrentUser stuWang = user("STUDENT");
        stuWang.setUserId(204L);
        UserHolder.saveUser(stuWang);
        try {
            String approvals = toolsByName.get("queryMyApprovals").execute(emptyArgs(), stuWang);
            Assertions.assertEquals(0, MAPPER.readTree(approvals).path("code").asInt(),
                    "学生应可查自己发起的审批（MINE 视角）：" + approvals);
        } finally {
            UserHolder.saveUser(superAdmin);
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
    void sloppyKeywordShouldFallBackToFullList() throws Exception {
        //qwen2.5 实测会把整句塞进 keyword：LIKE 匹配不到时应回退全量列表（数据范围不变），
        //并且必须带 note 说明"没匹配到"——否则模型把回退列表硬贴上所问队名（S3 实测错标）
        JsonNode args = MAPPER.createObjectNode().put("keyword", "星辰算法队的管理完成了吗");
        String result = toolsByName.get("queryTeam").execute(args, superAdmin);
        JsonNode node = MAPPER.readTree(result);
        Assertions.assertEquals(0, node.path("teams").path("code").asInt(),
                "回退后仍应是 code=0 的列表查询：" + result);
        Assertions.assertFalse(node.path("note").asText("").isBlank(),
                "回退时应带 note 说明关键词未匹配：" + result);
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
    void superAdminShouldQueryMemberTeamsByName() throws Exception {
        // 组长实测痛点："他参加啥比赛了"被模型拿 queryMyAchievements 张冠李戴——
        // 新工具按姓名闭环：人员档案拿 memberId → 在册赛队 → 比赛名
        JsonNode args = MAPPER.createObjectNode().put("keyword", "王小明");
        String result = toolsByName.get("queryMemberTeams").execute(args, superAdmin);
        JsonNode node = MAPPER.readTree(result);
        Assertions.assertTrue(node.has("member") && node.has("teams"),
                "应返回成员与队伍结构：" + result);
        // 抗活库：演示数据王小明有队伍则断言队名与比赛名存在，库空不硬断
        if (node.path("teamCount").asInt() > 0) {
            JsonNode first = node.path("teams").get(0);
            Assertions.assertTrue(first.hasNonNull("teamName"), "队伍应带队名：" + result);
        }
    }

    @Test
    void superAdminShouldGetFreeSlotErrorWithoutWeekNo() throws Exception {
        // 不给 weekNo：service 报周次越界，异常由 Flow 兜底转 JSON——直调层验证异常可抛出即可
        Assertions.assertThrows(Exception.class,
                () -> toolsByName.get("queryFreeSlots").execute(emptyArgs(), superAdmin));
    }

    // ---------- 缺口六工具（2026-10-25 补）：超管正常路径 ----------

    @Test
    void superAdminShouldQueryGapTools() throws Exception {
        Assertions.assertEquals(0, MAPPER.readTree(
                        toolsByName.get("queryContests").execute(emptyArgs(), superAdmin))
                .path("code").asInt(), "比赛列表应 code=0");

        Assertions.assertEquals(0, MAPPER.readTree(
                        toolsByName.get("queryProjects").execute(emptyArgs(), superAdmin))
                .path("code").asInt(), "项目列表应 code=0");

        Assertions.assertTrue(MAPPER.readTree(
                        toolsByName.get("queryOrgStructure").execute(emptyArgs(), superAdmin))
                .isArray(), "组织架构应返回树数组");

        Assertions.assertEquals(0, MAPPER.readTree(
                        toolsByName.get("queryMyApprovals").execute(emptyArgs(), superAdmin))
                .path("code").asInt(), "审批待办（TODO 视角）应 code=0");

        JsonNode semester = MAPPER.readTree(
                toolsByName.get("querySemesterInfo").execute(emptyArgs(), superAdmin));
        Assertions.assertTrue(semester.has("currentSemester") || semester.has("error"),
                "学期信息应有当前学期或无学期说明：" + semester);
        if (semester.has("currentWeek")) {
            Assertions.assertTrue(semester.path("currentWeek").asInt() >= 1, "周次应从 1 起");
        }

        // 排班总表不给 weekNo：service 校验周次越界，异常可抛出（Flow 兜底转 JSON）
        Assertions.assertThrows(Exception.class,
                () -> toolsByName.get("queryScheduleTable").execute(emptyArgs(), superAdmin));
    }

    // ---------- 支撑 ----------

    private JsonNode emptyArgs() {
        return MAPPER.createObjectNode();
    }
}
