package com.viomanager.person;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.viomanager.auth.entity.SysUser;
import com.viomanager.person.support.PersonExcelWriter;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * M07 导入口径验证（2026-10-24 自动建号拍板后）：
 * 组织按名称精确匹配、行级自动建号（M13 规则：登录名=学工号列、abc123、首登强制改密）、
 * 已存在有账号跳过 / 无账号补建、建号失败整行回滚（行级 NESTED 事务）、空学号整行失败。
 * 用 PersonExcelWriter 在内存里造 .xlsx（与 M06 模板同源）。
 */
class PersonImportTest extends PersonTestBase {

    private static final long ORG_CS = 9401L;
    private static final long PERSON_EXISTING = 94601L;
    private static final long PERSON_OUTSIDE = 94603L;

    private String superToken;

    @BeforeEach
    void setUp() throws Exception {
        //两个同名"计算机学院"组织：按名称匹配不唯一 → 该行报错（契约原文）
        insertOrg(ORG_CS, "计算机学院", "/0/9401/");
        insertOrg(9402L, "计算机学院", "/0/9402/");
        insertOrg(9403L, "机械学院", "/0/9403/");
        insertPerson(PERSON_EXISTING, "已存在人", "s94601", ORG_CS, null);
        //9402 里的存量无账号人员：部门管理员越权补号用例的目标
        insertPerson(PERSON_OUTSIDE, "外院人", "s94603", 9402L, null);
        superToken = createUserAndLogin("import_super", "SUPER_ADMIN", null, null);
    }

    @Test
    void importCreatesAccountBackfillsAndReportsRowErrors() throws Exception {
        //行2：合法新人（组织按名称匹配到唯一的"机械学院"）→ 建档 + 自动建号
        //行3：学号与库里 s94601 相同且无账号 → 补建账号，计成功
        //行4：组织名"计算机学院"匹配到两个 → 报错
        byte[] xlsx = buildXlsx(List.of(
                List.of("导入新人", "s94602", "13900002222", "计算机学院", "软件工程", "软件2302",
                        "男", "汉族", "", "", "", "", "机械学院", "STUDENT"),
                List.of("重复学号人", "s94601", "", "", "", "", "", "", "", "", "", "", "机械学院", ""),
                List.of("组织歧义人", "s94604", "", "", "", "", "", "", "", "", "", "", "计算机学院", "")));
        //1.上传导入
        mockMvc.perform(multipart("/api/members/import")
                        .file(new MockMultipartFile("file", "人员导入.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx))
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                //2.成功 2（新人建档建号 + 存量补建）、失败 1（组织歧义行）
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failCount").value(1))
                //3.错误行带 Excel 实际行号：表头第 1 行，数据从第 2 行起 → 歧义行是第 4 行
                .andExpect(jsonPath("$.data.errors[0].row").value(4))
                .andExpect(jsonPath("$.data.errors[0].reason").value("所属组织不存在或名称不唯一"));
        //4.新人入库：组织解析到 9403（按名称不是猜 ID）、性别归一化 MALE、账号按 M13 建好
        Assertions.assertEquals(1, count(
                "SELECT COUNT(*) FROM member WHERE student_no = 's94602' AND org_id = 9403 AND gender = 'MALE'"));
        Assertions.assertEquals(1, count(
                "SELECT COUNT(*) FROM sys_user WHERE login_name = 's94602' AND default_password = 1 "
                        + "AND role = 'STUDENT' "
                        + "AND member_id = (SELECT id FROM member WHERE student_no = 's94602')"));
        //5.存量无账号的 s94601 被补建：has_account 回 1、登录名取学号、人还是原来那条
        Assertions.assertEquals(1, count("SELECT has_account FROM member WHERE id = " + PERSON_EXISTING));
        Assertions.assertEquals(1, count(
                "SELECT COUNT(*) FROM sys_user WHERE login_name = 's94601' AND member_id = " + PERSON_EXISTING));
        Assertions.assertEquals(1, count(
                "SELECT COUNT(*) FROM member WHERE student_no = 's94601' AND deleted = 0"));
    }

    @Test
    void importSkipsExistingPersonWithAccount() throws Exception {
        //已存在且已有账号 → 整行跳过：不计成功也不计失败，不重复建号（重导幂等）
        jdbcTemplate.update("UPDATE member SET has_account = 1 WHERE id = ?", PERSON_EXISTING);
        byte[] xlsx = buildXlsx(List.of(
                List.of("重复学号人", "s94601", "", "", "", "", "", "", "", "", "", "", "机械学院", "")));
        mockMvc.perform(multipart("/api/members/import")
                        .file(new MockMultipartFile("file", "人员导入.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx))
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.failCount").value(0));
        //没有为已绑账号的人再建登录号
        Assertions.assertEquals(0, count(
                "SELECT COUNT(*) FROM sys_user WHERE member_id = " + PERSON_EXISTING));
    }

    @Test
    void importRollsBackRowWhenLoginNameOccupied() throws Exception {
        //登录名 s94607 已被别的账号占用 → 该行整体失败：档案随行级事务回滚，不留"有档无号"半成品
        SysUser squatter = new SysUser();
        squatter.setLoginName("s94607");
        squatter.setPassword("not-a-real-hash");
        squatter.setRole("STUDENT");
        squatter.setRealName("占号人");
        squatter.setDefaultPassword(0);
        squatter.setStatus(1);
        sysUserMapper.insert(squatter);
        byte[] xlsx = buildXlsx(List.of(
                List.of("撞号人", "s94607", "", "", "", "", "", "", "", "", "", "", "机械学院", "")));
        mockMvc.perform(multipart("/api/members/import")
                        .file(new MockMultipartFile("file", "人员导入.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx))
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.failCount").value(1))
                .andExpect(jsonPath("$.data.errors[0].row").value(2))
                .andExpect(jsonPath("$.data.errors[0].reason").value(containsString("账号创建失败")));
        //行级事务把档案一起回滚了
        Assertions.assertEquals(0, count("SELECT COUNT(*) FROM member WHERE student_no = 's94607'"));
        //占号账号原样保留
        Assertions.assertEquals(1, count("SELECT COUNT(*) FROM sys_user WHERE login_name = 's94607'"));
    }

    @Test
    void importRejectsBlankStudentNo() throws Exception {
        //学号/学工号列留空：自动建号没有登录名来源，整行失败
        byte[] xlsx = buildXlsx(List.of(
                List.of("无学号人", "", "", "", "", "", "", "", "", "", "", "", "机械学院", "")));
        mockMvc.perform(multipart("/api/members/import")
                        .file(new MockMultipartFile("file", "人员导入.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx))
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.failCount").value(1))
                .andExpect(jsonPath("$.data.errors[0].row").value(2))
                .andExpect(jsonPath("$.data.errors[0].reason").value(
                        "学号/学工号不能为空（导入将按其自动创建登录账号）"));
        Assertions.assertEquals(0, count("SELECT COUNT(*) FROM member WHERE name = '无学号人'"));
    }

    @Test
    void backfillOutsideSubtreeRejectedForDeptAdmin() throws Exception {
        //9402 里的存量无账号人员，9401 的部门管理员重导补号 → 越权该行报错
        //（补建只认档案真实组织；Excel 行里的组织列特意留空，证明它不影响判定）
        String deptCsToken = createUserAndLogin("import_dept_cs", "DEPT_ADMIN", ORG_CS, null);
        byte[] xlsx = buildXlsx(List.of(
                List.of("外院人", "s94603", "", "", "", "", "", "", "", "", "", "", "", "")));
        mockMvc.perform(multipart("/api/members/import")
                        .file(new MockMultipartFile("file", "人员导入.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx))
                        .header("Authorization", "Bearer " + deptCsToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.failCount").value(1))
                .andExpect(jsonPath("$.data.errors[0].reason").value("无权为该组织人员补建账号"));
        Assertions.assertEquals(0, count("SELECT has_account FROM member WHERE id = " + PERSON_OUTSIDE));
    }

    @Test
    void importRejectsBadFileType() throws Exception {
        mockMvc.perform(multipart("/api/members/import")
                        .file(new MockMultipartFile("file", "名单.csv", "text/csv", "姓名\n张三".getBytes()))
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    /** 内存里造 xlsx：模板表头 + 数据行（写出实现与 M06 模板同源，格式必然兼容） */
    private byte[] buildXlsx(List<List<String>> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PersonExcelWriter.write(PersonExcelWriter.TEMPLATE_HEADERS, rows, out);
        return out.toByteArray();
    }
}
