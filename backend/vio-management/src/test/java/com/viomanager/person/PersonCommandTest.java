package com.viomanager.person;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * 写侧安全验证：M03 建档越权封堵与 3001、M05 删除三规则（队长 3003 / 本人 3004 /
 * 清队伍关系 + 禁用账号）、M12 技能本人或管理员、M13 账号绑定与重置。
 */
class PersonCommandTest extends PersonTestBase {

    private static final long ORG_CS = 9401L;
    private static final long ORG_ME = 9402L;
    private static final long PERSON_LEADER = 94201L;
    private static final long PERSON_NORMAL = 94202L;
    private static final long PERSON_SELF_DEL = 94203L;
    private static final long PERSON_BIND = 94204L;

    private String superToken;
    private String deptCsToken;

    @BeforeEach
    void setUp() throws Exception {
        insertOrg(ORG_CS, "计算机学院", "/0/9401/");
        insertOrg(ORG_ME, "机械学院", "/0/9402/");
        insertSkillItem(94901L, "BACKEND", "后端开发");
        //三支测试人员：活跃队长 / 普通队员 / 部门管理员本人绑定的人
        insertPerson(PERSON_LEADER, "测试队长", "s94201", ORG_CS, null);
        insertPerson(PERSON_NORMAL, "普通队员", "s94202", ORG_CS, null);
        insertPerson(PERSON_SELF_DEL, "本人档", "s94203", ORG_CS, null);
        insertPerson(PERSON_BIND, "绑定人", "s94204", ORG_CS, null);
        //活跃队伍：94201 是队长、94202 是普通队员
        insertTeam(94401L, "删除测试队", ORG_CS, PERSON_LEADER);
        insertTeamMember(94921L, 94401L, PERSON_LEADER, "测试队长", 1, "CONFIRMED");
        insertTeamMember(94922L, 94401L, PERSON_NORMAL, "普通队员", 0, "CONFIRMED");
        //给普通队员与本人档绑上真实账号（M05 禁用与 3004 的前置）
        createUserAndLogin("cmd_normal_user", "STUDENT", ORG_CS, PERSON_NORMAL);
        superToken = createUserAndLogin("cmd_super", "SUPER_ADMIN", null, null);
        //部门管理员本人绑定 94203：他删自己 → 3004
        deptCsToken = createUserAndLogin("cmd_dept_self", "DEPT_ADMIN", ORG_CS, PERSON_SELF_DEL);
    }

    @Test
    void createOutsideSubtreeRejected() throws Exception {
        //计算机学院管理员往机械学院建人 → 1003
        mockMvc.perform(post("/api/members")
                        .header("Authorization", "Bearer " + deptCsToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"越权人\",\"orgId\":9402,\"memberType\":\"STUDENT\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    void createDuplicateStudentNoRejectedWith3001() throws Exception {
        mockMvc.perform(post("/api/members")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"重复学号人\",\"studentNo\":\"s94201\",\"orgId\":9401}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3001));
    }

    @Test
    void deleteLeaderRejectedWith3003() throws Exception {
        mockMvc.perform(delete("/api/members/" + PERSON_LEADER)
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3003));
    }

    @Test
    void deleteCurrentLoginAccountRejectedWith3004() throws Exception {
        mockMvc.perform(delete("/api/members/" + PERSON_SELF_DEL)
                        .header("Authorization", "Bearer " + deptCsToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3004));
    }

    @Test
    void deleteNormalMemberClearsRelationsAndDisablesAccount() throws Exception {
        //1.超管删普通队员 → 成功
        mockMvc.perform(delete("/api/members/" + PERSON_NORMAL)
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        //2.档案逻辑删
        org.junit.jupiter.api.Assertions.assertEquals(1, count(
                "SELECT deleted FROM member WHERE id = ?", PERSON_NORMAL));
        //3.队伍关系物理清（uk_team_member 决定不能软删）
        org.junit.jupiter.api.Assertions.assertEquals(0, count(
                "SELECT COUNT(*) FROM team_member WHERE member_id = ?", PERSON_NORMAL));
        //4.账号被禁用（孤儿账号口子）
        org.junit.jupiter.api.Assertions.assertEquals(0, count(
                "SELECT status FROM sys_user WHERE member_id = ?", PERSON_NORMAL));
    }

    @Test
    void skillsEditableBySelfWithDictValidation() throws Exception {
        String selfToken = createUserAndLogin("cmd_self_skill", "STUDENT", ORG_CS, PERSON_BIND);
        //1.本人改技能 → 成功且落库（提交项=字典显示名；字典条目值 BACKEND≠名 后端开发，
        //  2026-10-24 label 语系拍板后只有显示名是合法提交项）
        mockMvc.perform(put("/api/members/" + PERSON_BIND + "/skills")
                        .header("Authorization", "Bearer " + selfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skills\":[\"后端开发\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        org.junit.jupiter.api.Assertions.assertEquals(1, count(
                "SELECT COUNT(*) FROM member_skill WHERE member_id = ? AND skill_value = '后端开发' "
                        + "AND skill_label = '后端开发'",
                PERSON_BIND));
        //2.字典外的显示名 → 1001
        mockMvc.perform(put("/api/members/" + PERSON_BIND + "/skills")
                        .header("Authorization", "Bearer " + selfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skills\":[\"HACKING\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
        //3.旧口径的条目值（BACKEND）不再是合法提交项 → 1001（label 语系的行为翻转锁定）
        mockMvc.perform(put("/api/members/" + PERSON_BIND + "/skills")
                        .header("Authorization", "Bearer " + selfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skills\":[\"BACKEND\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void skillsRejectedForStranger() throws Exception {
        String strangerToken = createUserAndLogin("cmd_stranger", "STUDENT", ORG_ME, null);
        mockMvc.perform(put("/api/members/" + PERSON_BIND + "/skills")
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skills\":[\"BACKEND\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    void accountBindUsesJobNoAsDefaultLoginName() throws Exception {
        //给绑定人造学工号，M13 默认登录名应取它
        jdbcTemplate.update("UPDATE member SET job_no = 'T94204' WHERE id = ?", PERSON_BIND);
        //1.空 loginName 绑定 → 登录名取学工号、初始密码态、角色 STUDENT
        mockMvc.perform(post("/api/members/" + PERSON_BIND + "/account")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        org.junit.jupiter.api.Assertions.assertEquals(1, count(
                "SELECT COUNT(*) FROM sys_user WHERE member_id = ? AND login_name = 'T94204' "
                        + "AND default_password = 1 AND role = 'STUDENT'",
                PERSON_BIND));
        //2.档案标记已绑定
        org.junit.jupiter.api.Assertions.assertEquals(1, count(
                "SELECT has_account FROM member WHERE id = ?", PERSON_BIND));
        //3.重复绑定 → 1001
        mockMvc.perform(post("/api/members/" + PERSON_BIND + "/account")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void accountChangeResetsPasswordAndOldTokenDies() throws Exception {
        //1.先绑定（显式登录名）
        mockMvc.perform(post("/api/members/" + PERSON_BIND + "/account")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"bind94204\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        //2.用初始密码 abc123 登录拿旧 token
        String oldToken = loginWith("bind94204", "abc123");
        //3.PUT 改名 + 重置密码
        mockMvc.perform(put("/api/members/" + PERSON_BIND + "/account")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"bind94204new\",\"resetPassword\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        //4.新登录名生效、回到初始密码态
        org.junit.jupiter.api.Assertions.assertEquals(1, count(
                "SELECT COUNT(*) FROM sys_user WHERE member_id = ? AND login_name = 'bind94204new' "
                        + "AND default_password = 1", PERSON_BIND));
        //5.旧 token 因 pwd_updated_at 变化被踢（下一次请求 401）
        mockMvc.perform(put("/api/members/" + PERSON_BIND + "/skills")
                        .header("Authorization", "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skills\":[\"BACKEND\"]}"))
                .andExpect(status().isUnauthorized());
    }

    /** 指定账号密码登录拿 token（M13 绑定账号用初始密码 abc123） */
    private String loginWith(String loginName, String password) throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.token");
    }
}
