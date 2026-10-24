package com.viomanager.person;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * M05B 批量删除安全验证（2026-10-24 拍板：整批拒绝口径）：
 * 任一 id 不可删（队长 3003 / 当前登录账号 3004 / 越权 1003 / 不存在 1004 / 超限 3011）
 * → 全批不动，message 列出不可删名单与原因；全批可删才逐个级联删除（级联同 M05 单删）。
 */
class PersonBatchDeleteTest extends PersonTestBase {

    private static final long ORG_CS = 9401L;
    private static final long ORG_ME = 9402L;
    private static final long PERSON_LEADER = 94301L;
    private static final long PERSON_NORMAL = 94302L;
    private static final long PERSON_SELF_DEL = 94303L;
    private static final long PERSON_OUTSIDE = 94304L;

    private String superToken;
    private String deptCsToken;

    @BeforeEach
    void setUp() throws Exception {
        insertOrg(ORG_CS, "计算机学院", "/0/9401/");
        insertOrg(ORG_ME, "机械学院", "/0/9402/");
        //四名测试人员：活跃队长 / 普通队员（带真实账号）/ 部门管理员本人 / 机械学院的目标
        insertPerson(PERSON_LEADER, "批删队长", "s94301", ORG_CS, null);
        insertPerson(PERSON_NORMAL, "批删队员", "s94302", ORG_CS, null);
        insertPerson(PERSON_SELF_DEL, "批删本人", "s94303", ORG_CS, null);
        insertPerson(PERSON_OUTSIDE, "批删外院", "s94304", ORG_ME, null);
        insertTeam(94501L, "批删测试队", ORG_CS, PERSON_LEADER);
        insertTeamMember(94931L, 94501L, PERSON_LEADER, "批删队长", 1, "CONFIRMED");
        insertTeamMember(94932L, 94501L, PERSON_NORMAL, "批删队员", 0, "CONFIRMED");
        createUserAndLogin("batch_normal_user", "STUDENT", ORG_CS, PERSON_NORMAL);
        superToken = createUserAndLogin("batch_super", "SUPER_ADMIN", null, null);
        //计算机学院管理员本人绑定 94303：批里混入自己 → 3004
        deptCsToken = createUserAndLogin("batch_dept_self", "DEPT_ADMIN", ORG_CS, PERSON_SELF_DEL);
    }

    @Test
    void batchDeleteRejectsWholeBatchListingBlocked() throws Exception {
        //队长 + 本人混在批次里 → 整批不动（有队长 → 3003），message 列名单与原因
        mockMvc.perform(delete("/api/members")
                        .header("Authorization", "Bearer " + deptCsToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + PERSON_NORMAL + "," + PERSON_LEADER + "," + PERSON_SELF_DEL + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3003))
                .andExpect(jsonPath("$.message", containsString("整批未删除")))
                .andExpect(jsonPath("$.message", containsString("批删队长：队长")))
                .andExpect(jsonPath("$.message", containsString("批删本人：当前登录账号")));
        //整批未动：普通队员没被删，队伍关系完好
        Assertions.assertEquals(0, count("SELECT deleted FROM member WHERE id = " + PERSON_NORMAL));
        Assertions.assertEquals(1, count(
                "SELECT COUNT(*) FROM team_member WHERE member_id = " + PERSON_NORMAL));
    }

    @Test
    void batchDeleteAllClearDeletesAllWithCascade() throws Exception {
        //全批可删（重复 id 去重只删一次）→ 全批删除，级联同 M05 单删
        mockMvc.perform(delete("/api/members")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + PERSON_NORMAL + "," + PERSON_NORMAL + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        //档案逻辑删、队伍关系物理清、账号禁用
        Assertions.assertEquals(1, count("SELECT deleted FROM member WHERE id = " + PERSON_NORMAL));
        Assertions.assertEquals(0, count(
                "SELECT COUNT(*) FROM team_member WHERE member_id = " + PERSON_NORMAL));
        Assertions.assertEquals(0, count("SELECT status FROM sys_user WHERE member_id = " + PERSON_NORMAL));
    }

    @Test
    void batchDeleteMissingIdRejectsWholeBatchWith1004() throws Exception {
        //批次里混入不存在的 id → 1004 带出该 id，整批不动
        mockMvc.perform(delete("/api/members")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + PERSON_NORMAL + ",99999]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004))
                .andExpect(jsonPath("$.message", containsString("99999")));
        Assertions.assertEquals(0, count("SELECT deleted FROM member WHERE id = " + PERSON_NORMAL));
    }

    @Test
    void batchDeleteOutsideSubtreeRejectedWith1003() throws Exception {
        //计算机学院管理员删机械学院的人 → 1003，整批不动
        mockMvc.perform(delete("/api/members")
                        .header("Authorization", "Bearer " + deptCsToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + PERSON_OUTSIDE + "]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
        Assertions.assertEquals(0, count("SELECT deleted FROM member WHERE id = " + PERSON_OUTSIDE));
    }

    @Test
    void batchDeleteOverLimitRejectedWith3011() throws Exception {
        //201 个 id → 3011 超上限（3006-3009 已被组织/学期占用）
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < 201; i++) {
            ids.append(i == 0 ? "" : ",").append(96000 + i);
        }
        mockMvc.perform(delete("/api/members")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + ids + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3011))
                .andExpect(jsonPath("$.message", containsString("上限 200")));
    }

    @Test
    void batchDeleteRejectsEmptyIds() throws Exception {
        //空 ids → 1001 参数错
        mockMvc.perform(delete("/api/members")
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }
}
