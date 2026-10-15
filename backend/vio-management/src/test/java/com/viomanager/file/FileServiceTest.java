package com.viomanager.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 8 验证：文件服务全链路（真实 MinIO + 真实 ClamAV）。
 * 覆盖方案验证栏：传普通文件成功；传 EICAR 标准测试样本被拒、file_object 记 INFECTED、audit_log 有记录。
 * max-size-mb 调成 1 便于构造超限用例；审计切同步模式才能在事务里断言。
 * ⚠️ MinIO 里的对象不随 @Transactional 回滚（外部系统），测试对象残留在 vio-files 桶属预期，
 * 数据库记录全部回滚。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {"vio.audit.async=false", "vio.file.max-size-mb=1"})
class FileServiceTest {

    /** EICAR 标准病毒测试样本（业界公认的"无害病毒"，专用于验证杀毒链路） */
    private static final String EICAR =
            "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    private static final byte[] PDF_CONTENT = "%PDF-1.4\n%VIOManager 测试文件内容\n%%EOF".getBytes();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SysUser studentA;

    private String studentAToken;

    private String studentBToken;

    private String deptAToken;

    private String deptBToken;

    private String superToken;

    @BeforeEach
    void setUp() throws Exception {
        //1.两个部门的组织树（部门管理员的下载权限按子树判断）
        insertOrg(9301L, "软件学院", "/0/9301/");
        insertOrg(9302L, "机械学院", "/0/9302/");
        //2.四种身份各造一人：学生A在9301、学生B在9302、两部门管理员、超管
        studentAToken = createUserAndLogin("file_student_a", "STUDENT", 9301L);
        studentA = findUser("file_student_a");
        studentBToken = createUserAndLogin("file_student_b", "STUDENT", 9302L);
        deptAToken = createUserAndLogin("file_dept_a", "DEPT_ADMIN", 9301L);
        deptBToken = createUserAndLogin("file_dept_b", "DEPT_ADMIN", 9302L);
        superToken = createUserAndLogin("file_super", "SUPER_ADMIN", null);
    }

    @Test
    void uploadCleanFileThenDownloadByUploader() throws Exception {
        //1.上传：返回 fileId + scanStatus=CLEAN + 原始文件名
        String body = upload("file-key-1", "证书.pdf", PDF_CONTENT, studentAToken);
        assertEquals(0, (int) JsonPath.read(body, "$.code"));
        assertEquals("CLEAN", JsonPath.read(body, "$.data.scanStatus"));
        assertEquals("证书.pdf", JsonPath.read(body, "$.data.originalName"));
        long fileId = Long.parseLong(JsonPath.read(body, "$.data.fileId"));
        //2.落库字段完整性：CLEAN、上传人、所属组织、指纹
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM file_object WHERE id = ?", fileId);
        assertEquals("CLEAN", row.get("scan_status"));
        assertEquals(studentA.getId(), row.get("created_by"));
        assertEquals(9301L, row.get("org_id"));
        assertEquals("pdf", row.get("file_ext"));
        assertEquals(64, String.valueOf(row.get("sha256")).length());
        //3.同 key 再传：幂等拦截 1005
        String dupBody = upload("file-key-1", "证书.pdf", PDF_CONTENT, studentAToken);
        assertEquals(1005, (int) JsonPath.read(dupBody, "$.code"));
        //4.上传人下载：内容逐字节一致（MinIO 真实往返）、文件名回显、计数 +1、审计有痕
        MvcResult downloadResult = mockMvc.perform(get("/api/files/" + fileId + "/download")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andReturn();
        assertArrayEquals(PDF_CONTENT, downloadResult.getResponse().getContentAsByteArray());
        // 头里的中文文件名是百分号编码形态（filename*=UTF-8'' 标准写法）
        String encodedName = java.net.URLEncoder.encode("证书.pdf", java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        assertTrue(downloadResult.getResponse().getHeader("Content-Disposition").contains(encodedName));
        Integer count = jdbcTemplate.queryForObject(
                "SELECT download_count FROM file_object WHERE id = ?", Integer.class, fileId);
        assertEquals(1, count);
        assertNotNull(latestAuditRow("FILE_DOWNLOAD"));
    }

    @Test
    void uploadEicarRejectedAndAudited() throws Exception {
        //1.传 EICAR 病毒样本：1010 拒绝
        String body = upload("file-key-2", "virus.pdf", EICAR.getBytes(), studentAToken);
        assertEquals(1010, (int) JsonPath.read(body, "$.code"));
        //2.file_object 记 INFECTED + 病毒特征名（留证不删）
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM file_object WHERE original_name = 'virus.pdf'");
        assertEquals("INFECTED", row.get("scan_status"));
        assertTrue(String.valueOf(row.get("scan_signature")).toLowerCase().contains("eicar"));
        //3.audit_log 有失败记录（方案验证栏）
        Map<String, Object> auditRow = latestAuditRow("FILE_UPLOAD");
        assertNotNull(auditRow);
        assertEquals(0, ((Number) auditRow.get("success")).intValue());
        //4.染毒文件连上传人本人都不能下载（CLEAN 是唯一闸门）
        mockMvc.perform(get("/api/files/" + row.get("id") + "/download")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    void uploadBadExtensionRejected() throws Exception {
        String body = upload("file-key-3", "evil.exe", PDF_CONTENT, studentAToken);
        assertEquals(1008, (int) JsonPath.read(body, "$.code"));
    }

    @Test
    void uploadOversizeRejected() throws Exception {
        // 上限被测试配置调成 1MB，传 1.2MB 的 pdf → 1009
        byte[] big = new byte[1200 * 1024];
        String body = upload("file-key-4", "big.pdf", big, studentAToken);
        assertEquals(1009, (int) JsonPath.read(body, "$.code"));
    }

    @Test
    void downloadPermissionMatrix() throws Exception {
        // 学生A（9301）上传一份文件
        String body = upload("file-key-5", "材料.pdf", PDF_CONTENT, studentAToken);
        long fileId = Long.parseLong(JsonPath.read(body, "$.data.fileId"));
        // 学生B（9302）：不是本人上传 → 403 + 1003
        mockMvc.perform(get("/api/files/" + fileId + "/download")
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
        // 部门管理员B（9302）：文件不在自己子树 → 403
        mockMvc.perform(get("/api/files/" + fileId + "/download")
                        .header("Authorization", "Bearer " + deptBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
        // 部门管理员A（9301）：文件在自己子树 → 放行
        mockMvc.perform(get("/api/files/" + fileId + "/download")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk());
        // 超管：全放行
        mockMvc.perform(get("/api/files/" + fileId + "/download")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk());
        // 计数只被放行的两次累加
        Integer count = jdbcTemplate.queryForObject(
                "SELECT download_count FROM file_object WHERE id = ?", Integer.class, fileId);
        assertEquals(2, count);
    }

    @Test
    void downloadNotFound() throws Exception {
        mockMvc.perform(get("/api/files/999999/download")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    void kbFileDownloadableByAnyLoggedInUser() throws Exception {
        //知识库附件（KB_FILE）随条目全员可见：部门管理员上传，外部门无归属关系的学生也能下载
        String body = upload("file-key-kb", "知识库.pdf", PDF_CONTENT, deptAToken, "KB_FILE");
        assertEquals(0, (int) JsonPath.read(body, "$.code"));
        long fileId = Long.parseLong(JsonPath.read(body, "$.data.fileId"));
        MvcResult downloadResult = mockMvc.perform(get("/api/files/" + fileId + "/download")
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isOk())
                .andReturn();
        assertArrayEquals(PDF_CONTENT, downloadResult.getResponse().getContentAsByteArray());
    }

    /** 打上传接口：multipart + token + 幂等键，返回响应体字符串 */
    private String upload(String idemKey, String filename, byte[] content, String token) throws Exception {
        return upload(idemKey, filename, content, token, "MATERIAL");
    }

    /** 打上传接口：bizType 可指定（KB_FILE 公共资源等场景） */
    private String upload(String idemKey, String filename, byte[] content, String token, String bizType) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "application/pdf", content);
        MockHttpServletRequestBuilder request = multipart("/api/files")
                .file(file)
                .param("bizType", bizType)
                .header("Authorization", "Bearer " + token)
                .header("X-Idempotency-Key", idemKey);
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    /** 取指定业务类型最新一行审计（同步模式写在测试事务里，读得到） */
    private Map<String, Object> latestAuditRow(String bizType) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM audit_log WHERE biz_type = ? ORDER BY id DESC", bizType);
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    private SysUser findUser(String loginName) {
        return sysUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysUser>()
                        .eq("login_name", loginName));
    }

    /** 造一个指定角色与组织的用户并登录拿 token */
    private String createUserAndLogin(String loginName, String role, Long orgId) throws Exception {
        SysUser user = new SysUser();
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole(role);
        user.setOrgId(orgId);
        user.setRealName("文件探针-" + loginName);
        user.setDefaultPassword(0);
        user.setStatus(1);
        sysUserMapper.insert(user);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"pass123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data.token");
    }

    /** 插一条组织（sys_org 非空列只给 name / org_type，其余走默认值） */
    private void insertOrg(Long id, String name, String path) {
        jdbcTemplate.update(
                "INSERT INTO sys_org (id, parent_id, name, org_type, path) VALUES (?, 0, ?, 'DEPARTMENT', ?)",
                id, name, path);
    }
}
