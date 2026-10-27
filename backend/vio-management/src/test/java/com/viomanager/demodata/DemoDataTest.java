package com.viomanager.demodata;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 10 验证：演示数据完整性（R__demo_data.sql）。
 * 前提：全新库由 Flyway 自动建表 + 灌演示数据（本测试跑在已迁移的库上）。
 * 覆盖：8 账号可登录且口径正确（admin 首登提示改密）、组织树含 3 分子组织、
 * 学期/模板/比赛/赛队/队员/获奖记录齐全、材料类型字典可从真实接口读出。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DemoDataTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void demoAccountsLogin() throws Exception {
        //1.admin：超管，defaultPassword=true（前端据此弹强制改密提示）
        MvcResult admin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"admin\",\"password\":\"Admin@123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.data.defaultPassword").value(true))
                .andReturn();
        String adminToken = JsonPath.read(admin.getResponse().getContentAsString(), "$.data.token");
        //2.部门管理员与学生各登录一个，角色与组织正确
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"dept_chen\",\"password\":\"Dept@123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.role").value("DEPT_ADMIN"))
                .andExpect(jsonPath("$.data.defaultPassword").value(false));
        MvcResult student = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"stu_wang\",\"password\":\"Stu@123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.realName").value("王小明"))
                .andReturn();
        //3.学生的 token 走一个真实接口（字典），整条链路可用
        String token = JsonPath.read(student.getResponse().getContentAsString(), "$.data.token");
        mockMvc.perform(get("/api/dicts/MATERIAL_TYPE").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        //4.2026-10-24 新增三账号：孙小雨补号（原无账号成待办孤儿）+ 联调沙盒班底李浩然/吴建国
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"stu_sun\",\"password\":\"Stu@123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.realName").value("孙小雨"));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"stu_li\",\"password\":\"Stu@123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.realName").value("李浩然"));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"dept_wu\",\"password\":\"Dept@123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("DEPT_ADMIN"))
                .andExpect(jsonPath("$.data.realName").value("吴建国"));
    }

    @Test
    void orgTreeContainsTwoSubOrgs() {
        // 组织树 4 个节点：中心 → 3 个协会直挂（2026-10-23 改回 PRD §3.3 原意形状，
        // 学院/专业/班级回归人员档案文本字段；2026-10-24 加机械创新协会联调沙盒部门）
        Long orgCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_org WHERE id IN (1, 2, 3, 4) AND deleted = 0", Long.class);
        orgCount = orgCount == null ? 0 : orgCount;
        org.junit.jupiter.api.Assertions.assertEquals(4, orgCount);
        Long subOrgCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_org WHERE is_sub_org = 1 AND deleted = 0", Long.class);
        org.junit.jupiter.api.Assertions.assertEquals(3, subOrgCount == null ? 0 : subOrgCount);
        // 协会直挂中心（数据范围子树演示的根基），机械创新协会同构
        String path = jdbcTemplate.queryForObject(
                "SELECT path FROM sys_org WHERE id = 2", String.class);
        org.junit.jupiter.api.Assertions.assertEquals("/0/1/2/", path);
        String mechPath = jdbcTemplate.queryForObject(
                "SELECT path FROM sys_org WHERE id = 4", String.class);
        org.junit.jupiter.api.Assertions.assertEquals("/0/1/4/", mechPath);
    }

    @Test
    void semesterContestTeamsAndMembersPresent() {
        // 学期：唯一当前学期，进行中
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                "SELECT status FROM semester WHERE id = 301", String.class));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT is_current FROM semester WHERE id = 301", Integer.class));
        // 比赛：报名中
        org.junit.jupiter.api.Assertions.assertEquals("REGISTERING", jdbcTemplate.queryForObject(
                "SELECT status FROM contest WHERE id = 402", String.class));
        // 材料状态是 C 线活数据：材料提交/比赛材料要求变动都会触发整场重算
        // （MaterialCompletionRecalculator：有提交但必传缺项 = PENDING），种子基线
        // 501=SUBMITTED、502=NOT_STARTED 会随活库操作合法漂移（2026-10-26 实录：
        // 501 被重算为 PENDING）——同 DictItemAdminTest 活库口径，断合法域不断精确值
        org.junit.jupiter.api.Assertions.assertTrue(java.util.List.of("NOT_STARTED", "PENDING", "SUBMITTED")
                .contains(jdbcTemplate.queryForObject(
                        "SELECT material_status FROM team WHERE id = 501", String.class)));
        org.junit.jupiter.api.Assertions.assertTrue(java.util.List.of("NOT_STARTED", "PENDING", "SUBMITTED")
                .contains(jdbcTemplate.queryForObject(
                        "SELECT material_status FROM team WHERE id = 502", String.class)));
        // 队员下限 3 人（种子：501 王小明+赵晓亮、502 孙小雨队长未确认）——TE04 加人/P05 同意/TE06 确认
        // 是种子队常用演示路径，真实走查会往 501/502 加队员（2026-10-27 实录：105 孙小雨被加进 501 致 4 行），
        // 同 material_status 活库口径断下限不断精确值
        Long memberRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_member WHERE team_id IN (501, 502) AND deleted = 0", Long.class);
        org.junit.jupiter.api.Assertions.assertTrue((memberRows == null ? 0 : memberRows) >= 3);
        // 成员 105 已补账号（2026-10-24：原"无账号边界"演示让 802/803/807 成待办孤儿，弃用）
        Long withAccount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member WHERE id = 105 AND has_account = 1", Long.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, withAccount == null ? 0 : withAccount);
    }

    @Test
    void achievementAndDictPresent() {
        // 获奖记录：已通过（PRD §5 演示要求）
        org.junit.jupiter.api.Assertions.assertEquals("APPROVED", jdbcTemplate.queryForObject(
                "SELECT status FROM achievement WHERE id = 701", String.class));
        // 材料类型字典：3 个预设项
        Long dictItems = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_item WHERE dict_code = 'MATERIAL_TYPE' AND deleted = 0",
                Long.class);
        org.junit.jupiter.api.Assertions.assertEquals(3, dictItems == null ? 0 : dictItems);
    }

    @Test
    void scheduleDemoDataPresent() {
        // 排班演示（2026-10-24 补）：课表走真导入（组长导的全校教务 CSV），种子只管映射确认/规则/班次。
        // 断言一律收窄到种子固定 ID 段或固定键——演示库是活库，用户随时会经 UI 导数据/取消班次，
        // 全表计数会被真数据打爆（曾因真导入 87499 行课表红过一次）
        // 班级映射：演示三班确认（教务键 23-1 带杠 ↔ 档案 2301 不带杠靠映射搭桥；
        // 真导入自动生成的映射行经 uk_class_mapping 幂等合并走 UPDATE 置位）
        Long confirmed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM class_mapping WHERE semester_id = 301 AND confirmed = 1 AND deleted = 0"
                        + " AND class_key IN ('自动化23-1班', '电信23-2班', '机械23-1班')", Long.class);
        org.junit.jupiter.api.Assertions.assertEquals(3, confirmed == null ? 0 : confirmed);
        // 假课表历史包袱已清（当日早先版本曾造 20 行假课表，班级键与真导入两套写法撞宽容匹配歧义）
        Long fakeCourses = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_timetable WHERE import_id = 1121", Long.class);
        org.junit.jupiter.api.Assertions.assertEquals(0, fakeCourses == null ? 0 : fakeCourses);
        // 排班规则：3 个协会各 10 格（工作日 1-2 节），种子固定 ID 段
        Long rules = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule_rule WHERE id BETWEEN 1161 AND 1190 AND deleted = 0", Long.class);
        org.junit.jupiter.api.Assertions.assertEquals(30, rules == null ? 0 : rules);
        // 在排班次：第 6 周（页面默认周）4 条 PLANNED（状态值对齐 ScheduleServiceImpl 真实口径，非 mock 的 ACTIVE）
        Long duties = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule WHERE id BETWEEN 1201 AND 1204 AND week_no = 6"
                        + " AND status = 'PLANNED' AND deleted = 0", Long.class);
        org.junit.jupiter.api.Assertions.assertEquals(4, duties == null ? 0 : duties);
        // 冲突演示位 1 条：李浩然周一第 2 节（真课表机械23-1班该格有机械专业英语）
        Long conflicts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule WHERE id BETWEEN 1201 AND 1204 AND course_conflict = 1"
                        + " AND deleted = 0", Long.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, conflicts == null ? 0 : conflicts);
    }
}
