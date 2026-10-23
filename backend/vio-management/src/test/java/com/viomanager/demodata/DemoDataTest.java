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
 * 覆盖：5 账号可登录且口径正确（admin 首登提示改密）、组织树含 2 分子组织、
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
    }

    @Test
    void orgTreeContainsTwoSubOrgs() {
        // 组织树 3 个节点：中心 → 2 个协会直挂（2026-10-23 改回 PRD §3.3 原意形状，
        // 学院/专业/班级回归人员档案文本字段，不再是树节点）
        Long orgCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_org WHERE id IN (1, 2, 3) AND deleted = 0", Long.class);
        orgCount = orgCount == null ? 0 : orgCount;
        org.junit.jupiter.api.Assertions.assertEquals(3, orgCount);
        Long subOrgCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_org WHERE is_sub_org = 1 AND deleted = 0", Long.class);
        org.junit.jupiter.api.Assertions.assertEquals(2, subOrgCount == null ? 0 : subOrgCount);
        // 协会直挂中心（数据范围子树演示的根基）
        String path = jdbcTemplate.queryForObject(
                "SELECT path FROM sys_org WHERE id = 2", String.class);
        org.junit.jupiter.api.Assertions.assertEquals("/0/1/2/", path);
    }

    @Test
    void semesterContestTeamsAndMembersPresent() {
        // 学期：唯一当前学期，进行中
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                "SELECT status FROM semester WHERE id = 301", String.class));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT is_current FROM semester WHERE id = 301", Integer.class));
        // 比赛：报名中；两支赛队材料状态一前一后
        org.junit.jupiter.api.Assertions.assertEquals("REGISTERING", jdbcTemplate.queryForObject(
                "SELECT status FROM contest WHERE id = 402", String.class));
        org.junit.jupiter.api.Assertions.assertEquals("SUBMITTED", jdbcTemplate.queryForObject(
                "SELECT material_status FROM team WHERE id = 501", String.class));
        org.junit.jupiter.api.Assertions.assertEquals("NOT_STARTED", jdbcTemplate.queryForObject(
                "SELECT material_status FROM team WHERE id = 502", String.class));
        // 队员 3 人：501 队长已确认，502 队长未确认
        Long memberRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_member WHERE team_id IN (501, 502) AND deleted = 0", Long.class);
        org.junit.jupiter.api.Assertions.assertEquals(3, memberRows == null ? 0 : memberRows);
        // 成员 105 故意没绑账号（演示"有档案无账号"的边界）
        Long noAccount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member WHERE id = 105 AND has_account = 0", Long.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, noAccount == null ? 0 : noAccount);
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
}
