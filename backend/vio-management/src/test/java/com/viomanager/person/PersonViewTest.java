package com.viomanager.person;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M02 四档视角 + M10 身份证 + M11 附件下载的安全验证：
 * 超管/本部门管理员/本人全量，本队队长大部分（无身份证明文），其他仅基础字段；
 * 敏感口（M10/M11）对受限视角一律 3010。
 */
class PersonViewTest extends PersonTestBase {

    private static final long ORG_CS = 9401L;
    private static final long ORG_SUB = 9403L;
    private static final long ORG_ME = 9402L;
    private static final long PERSON_TARGET = 94101L;
    private static final long PERSON_CAPTAIN = 94102L;
    private static final long PERSON_OTHER_ORG = 94103L;
    private static final String ID_CARD = "230101200001011234";

    private String superToken;
    private String deptCsToken;
    private String deptMeToken;
    private String selfToken;
    private String captainToken;
    private String strangerToken;

    @BeforeEach
    void setUp() throws Exception {
        //组织：计算机学院 9401（子树含 9403 软件开发部）、机械学院 9402
        insertOrg(ORG_CS, "计算机学院", "/0/9401/");
        insertOrg(ORG_SUB, "软件开发部", "/0/9401/9403/");
        insertOrg(ORG_ME, "机械学院", "/0/9402/");
        //目标人挂在子组织 9403（验证部门管理员按子树放行）；他人挂 9402
        insertPerson(PERSON_TARGET, "目标人", "s94011", ORG_SUB, ID_CARD);
        insertPerson(PERSON_CAPTAIN, "队长", "s94012", ORG_SUB, null);
        insertPerson(PERSON_OTHER_ORG, "外部门人", "s94021", ORG_ME, null);
        //活跃队伍：队长 94102 带目标人 94101
        insertTeam(94301L, "视角测试队", ORG_SUB, PERSON_CAPTAIN);
        insertTeamMember(94911L, 94301L, PERSON_CAPTAIN, "队长", 1, "CONFIRMED");
        insertTeamMember(94912L, 94301L, PERSON_TARGET, "目标人", 0, "CONFIRMED");
        //六个视角的登录人
        superToken = createUserAndLogin("view_super", "SUPER_ADMIN", null, null);
        deptCsToken = createUserAndLogin("view_dept_cs", "DEPT_ADMIN", ORG_CS, null);
        deptMeToken = createUserAndLogin("view_dept_me", "DEPT_ADMIN", ORG_ME, null);
        selfToken = createUserAndLogin("view_self", "STUDENT", ORG_SUB, PERSON_TARGET);
        captainToken = createUserAndLogin("view_captain", "STUDENT", ORG_SUB, PERSON_CAPTAIN);
        strangerToken = createUserAndLogin("view_stranger", "STUDENT", ORG_ME, PERSON_OTHER_ORG);
    }

    @Test
    void superAdminSeesFullDetail() throws Exception {
        mockMvc.perform(get("/api/members/" + PERSON_TARGET)
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.limited").value(false))
                .andExpect(jsonPath("$.data.phone").value("13800001111"))
                .andExpect(jsonPath("$.data.idCard").value(ID_CARD))
                .andExpect(jsonPath("$.data.studentNo").value("s94011"));
    }

    @Test
    void deptAdminOfSubtreeSeesFullDetail() throws Exception {
        //管理员根 9401，目标人在子组织 9403——子树内全量
        mockMvc.perform(get("/api/members/" + PERSON_TARGET)
                        .header("Authorization", "Bearer " + deptCsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.limited").value(false))
                .andExpect(jsonPath("$.data.idCard").value(ID_CARD));
    }

    @Test
    void deptAdminOfOtherOrgGetsLimited() throws Exception {
        //机械学院管理员看计算机学院的人：受限档，不报错（契约：其他仅姓名/学院/班级/技能）
        mockMvc.perform(get("/api/members/" + PERSON_TARGET)
                        .header("Authorization", "Bearer " + deptMeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.limited").value(true))
                .andExpect(jsonPath("$.data.name").value("目标人"))
                .andExpect(jsonPath("$.data.phone").value(nullValue()))
                .andExpect(jsonPath("$.data.studentNo").value(nullValue()))
                .andExpect(jsonPath("$.data.idCard").value(nullValue()));
    }

    @Test
    void selfSeesFullDetailWithLoginName() throws Exception {
        mockMvc.perform(get("/api/members/" + PERSON_TARGET)
                        .header("Authorization", "Bearer " + selfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.limited").value(false))
                .andExpect(jsonPath("$.data.idCard").value(ID_CARD))
                .andExpect(jsonPath("$.data.loginName").value("view_self"));
    }

    @Test
    void captainSeesMostButNoIdCard() throws Exception {
        //队长档：手机号完整，但身份证明文不给（要走 M10），尾 4 位可见
        mockMvc.perform(get("/api/members/" + PERSON_TARGET)
                        .header("Authorization", "Bearer " + captainToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.limited").value(false))
                .andExpect(jsonPath("$.data.phone").value("13800001111"))
                .andExpect(jsonPath("$.data.idCard").value(nullValue()))
                .andExpect(jsonPath("$.data.idCardLast4").value("1234"));
    }

    @Test
    void strangerStudentGetsLimited() throws Exception {
        mockMvc.perform(get("/api/members/" + PERSON_TARGET)
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.limited").value(true))
                .andExpect(jsonPath("$.data.name").value("目标人"))
                .andExpect(jsonPath("$.data.phone").value(nullValue()));
    }

    @Test
    void idCardRejectedForStrangerWith3010() throws Exception {
        mockMvc.perform(get("/api/members/" + PERSON_TARGET + "/id-card")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3010));
    }

    @Test
    void idCardAllowedForCaptainAndSelf() throws Exception {
        //队长走 M10 拿完整身份证（契约允许），本人同理
        mockMvc.perform(get("/api/members/" + PERSON_TARGET + "/id-card")
                        .header("Authorization", "Bearer " + captainToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idCard").value(ID_CARD));
        mockMvc.perform(get("/api/members/" + PERSON_TARGET + "/id-card")
                        .header("Authorization", "Bearer " + selfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idCard").value(ID_CARD));
    }

    @Test
    void photoDownloadRejectedForStrangerWith3010() throws Exception {
        mockMvc.perform(get("/api/members/" + PERSON_TARGET + "/photo")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3010));
    }

    @Test
    void photoDownloadWithoutFileReturns1004() throws Exception {
        //权限够（本人）但没传过证件照 → 1004 而不是 500
        mockMvc.perform(get("/api/members/" + PERSON_TARGET + "/photo")
                        .header("Authorization", "Bearer " + selfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    void listIsAdminOnlyAndSubtreeScoped() throws Exception {
        //1.学生调列表 → 1003（controller 注解收口）
        mockMvc.perform(get("/api/members")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
        //2.计算机学院管理员列表能搜到子组织的人、搜不到机械学院的人
        mockMvc.perform(get("/api/members").param("keyword", "目标人")
                        .header("Authorization", "Bearer " + deptCsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].idCard").value(nullValue()))
                .andExpect(jsonPath("$.data.list[0].email").value(nullValue()))
                .andExpect(jsonPath("$.data.list[0].photoFileId").value(nullValue()));
        //3.按外部门人名搜 → 0（子树过滤生效）
        mockMvc.perform(get("/api/members").param("keyword", "外部门人")
                        .header("Authorization", "Bearer " + deptCsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
