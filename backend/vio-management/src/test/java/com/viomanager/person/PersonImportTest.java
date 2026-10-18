package com.viomanager.person;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.viomanager.person.support.PersonExcelWriter;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * M07 导入口径验证：组织按名称精确匹配、学号已存在的行跳过（不计成功也不计失败）、
 * 非法行返回 Excel 实际行号。用 PersonExcelWriter 在内存里造 .xlsx（与 M06 模板同源）。
 */
class PersonImportTest extends PersonTestBase {

    private static final long ORG_CS = 9401L;
    private static final long PERSON_EXISTING = 94601L;

    private String superToken;

    @BeforeEach
    void setUp() throws Exception {
        //两个同名"重名学院"组织：按名称匹配不唯一 → 该行报错（契约原文）
        insertOrg(ORG_CS, "计算机学院", "/0/9401/");
        insertOrg(9402L, "计算机学院", "/0/9402/");
        insertOrg(9403L, "机械学院", "/0/9403/");
        insertPerson(PERSON_EXISTING, "已存在人", "s94601", ORG_CS, null);
        superToken = createUserAndLogin("import_super", "SUPER_ADMIN", null, null);
    }

    @Test
    void importMatchesOrgByNameSkipsExistingAndReportsRowErrors() throws Exception {
        //行2：合法新人（组织按名称匹配到唯一的"机械学院"）
        //行3：学号与库里 s94601 相同 → 跳过（success 与 fail 都不计）
        //行4：组织名"计算机学院"匹配到两个 → 报错
        byte[] xlsx = buildXlsx(List.of(
                List.of("导入新人", "s94602", "13900002222", "计算机学院", "软件工程", "软件2302",
                        "男", "汉族", "", "", "", "", "机械学院", "STUDENT"),
                List.of("重复学号人", "s94601", "", "", "", "", "", "", "", "", "", "", "机械学院", ""),
                List.of("组织歧义人", "s94603", "", "", "", "", "", "", "", "", "", "", "计算机学院", "")));
        //1.上传导入
        mockMvc.perform(multipart("/api/members/import")
                        .file(new MockMultipartFile("file", "人员导入.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx))
                        .header("Authorization", "Bearer " + superToken)
                        .header("X-Idempotency-Key", "k" + System.nanoTime()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                //2.成功 1（新人）、失败 1（组织歧义行）、跳过 1（已存在学号，两边都不计）
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failCount").value(1))
                //3.错误行带 Excel 实际行号：表头第 1 行，数据从第 2 行起 → 歧义行是第 4 行
                .andExpect(jsonPath("$.data.errors[0].row").value(4))
                .andExpect(jsonPath("$.data.errors[0].reason").value("所属组织不存在或名称不唯一"));
        //4.新人入库且组织解析到了 9403（按名称，不是猜 ID）
        org.junit.jupiter.api.Assertions.assertEquals(1, count(
                "SELECT COUNT(*) FROM member WHERE student_no = 's94602' AND org_id = 9403"));
        //5.重复学号没有新增行（库里仍只有一条 s94601）
        org.junit.jupiter.api.Assertions.assertEquals(1, count(
                "SELECT COUNT(*) FROM member WHERE student_no = 's94601' AND deleted = 0"));
        //6.性别"男"归一化成了 MALE
        org.junit.jupiter.api.Assertions.assertEquals(1, count(
                "SELECT COUNT(*) FROM member WHERE student_no = 's94602' AND gender = 'MALE'"));
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
