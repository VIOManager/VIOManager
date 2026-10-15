package com.viomanager.project;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/**
 * M5 项目文件测试（PR25–PR28）：上传两段式（骨架 F01 → 关联行，权威值覆盖）、
 * 上传权限（所有人∪成员）、删除权限（所有人删任何/成员仅自己的）、
 * 下载可见性闸门 + 字节回环（真实 MinIO 往返）。
 * ClamAV 未起时按组长拍板的不可用策略：上传照常成功、scanStatus=UNAVAILABLE、
 * 下载不拦（只有 INFECTED 才拒）——本测试不依赖 ClamAV 进程。
 */
class ProjectFileTest extends PmTestSupport {

    private static final Long ORG_SOFT = 9301L;
    private static final Long ORG_MECH = 9302L;
    private static final Long SEMESTER = 95001L;

    private static final byte[] PDF_CONTENT =
            "%PDF-1.4\n%项目文件测试\n%%EOF".getBytes();

    private String ownerToken;
    private String memberToken;
    private String otherMemberToken;
    private String outsiderToken;
    private long projectId;

    @BeforeEach
    void setUp() throws Exception {
        insertOrg(ORG_SOFT, "软件学院", "/0/9301/");
        insertOrg(ORG_MECH, "机械学院", "/0/9302/");
        insertCurrentSemester(SEMESTER, "2026-2027学年第一学期-PM文件测试");
        insertMember(80001L, "项目A", ORG_SOFT, "STUDENT");
        insertMember(80002L, "成员B", ORG_SOFT, "STUDENT");
        insertMember(80003L, "成员C", ORG_SOFT, "STUDENT");
        insertMember(80004L, "外人D", ORG_MECH, "STUDENT");
        ownerToken = createUserAndLogin("pm6_owner", "STUDENT", ORG_SOFT, 80001L);
        memberToken = createUserAndLogin("pm6_member_b", "STUDENT", ORG_SOFT, 80002L);
        otherMemberToken = createUserAndLogin("pm6_member_c", "STUDENT", ORG_SOFT, 80003L);
        outsiderToken = createUserAndLogin("pm6_outsider", "STUDENT", ORG_MECH, 80004L);
        String body = createProject(ownerToken, "{\"name\":\"文件验证项目\",\"visibility\":\"ORG\"}");
        assertEquals(0, (int) JsonPath.read(body, "$.code"), body);
        projectId = Long.parseLong(JsonPath.read(body, "$.data.id"));
        //B 是项目成员（上传权限覆盖成员）
        jdbcTemplate.update("INSERT INTO project_member (id, project_id, member_id, member_name, "
                + "project_role) VALUES (70001, ?, 80002, '成员B', 'MEMBER')", projectId);
    }

    /** PR26 上传，返回响应体 */
    private String upload(String token, String fileName, String fileCategory) throws Exception {
        return mockMvc.perform(withToken(multipart("/api/projects/" + projectId + "/files")
                        .file(new MockMultipartFile("file", fileName,
                                MediaType.APPLICATION_PDF_VALUE, PDF_CONTENT)), token)
                        .param("fileCategory", fileCategory))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String list(String token) throws Exception {
        return mockMvc.perform(withToken(get("/api/projects/" + projectId + "/files"), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    // ---------------------------------------------------------------- PR25/26 上传与列表

    @Test
    void uploadAndListEndToEnd() throws Exception {
        //所有人与成员都可传；外部门学生（项目不可见）→ 7002（先于可见性？不——上传门槛即 7002）
        String ownerUpload = upload(ownerToken, "设计文档.pdf", "DOC");
        assertEquals(0, (int) JsonPath.read(ownerUpload, "$.code"), ownerUpload);
        long ownerRecordId = Long.parseLong(JsonPath.read(ownerUpload, "$.data.id"));
        String memberUpload = upload(memberToken, "代码包.pdf", "CODE");
        assertEquals(0, (int) JsonPath.read(memberUpload, "$.code"), memberUpload);
        //外人（外部门）上传 → 7002（非所有人非成员）
        assertEquals(7002, (int) JsonPath.read(upload(outsiderToken, "偷传.pdf", "DOC"), "$.code"));
        //关联行：文件名/大小/分类/上传人以 file_object 权威值与人员口径落库
        var row = jdbcTemplate.queryForMap(
                "SELECT file_name, file_size, file_category, uploaded_by, uploaded_by_name "
                        + "FROM project_file WHERE id = ?", ownerRecordId);
        assertEquals("设计文档.pdf", row.get("file_name"));
        assertEquals(PDF_CONTENT.length, ((Number) row.get("file_size")).longValue());
        assertEquals("DOC", row.get("file_category"));
        assertEquals(80001L, ((Number) row.get("uploaded_by")).longValue());
        //PR25 列表：项目可见者可看（owner/成员）；外部门学生项目不可见 → 7003
        String ownerList = list(ownerToken);
        assertEquals(0, (int) JsonPath.read(ownerList, "$.code"));
        assertEquals(2, ((java.util.List<?>) JsonPath.read(ownerList, "$.data[*]")).size());
        assertEquals(0, (int) JsonPath.read(list(memberToken), "$.code"));
        assertEquals(7003, (int) JsonPath.read(list(outsiderToken), "$.code"));
        //非法分类 → 1001
        assertEquals(1001, (int) JsonPath.read(upload(ownerToken, "视频.pdf", "VIDEO"), "$.code"));
    }

    // ---------------------------------------------------------------- PR27 删除

    @Test
    void deletePermissionMatrix() throws Exception {
        String bUpload = upload(memberToken, "成员B的文件.pdf", "DOC");
        long bRecordId = Long.parseLong(JsonPath.read(bUpload, "$.data.id"));
        //成员 C（不是上传人）删 B 的文件 → 7002
        assertEquals(7002, (int) JsonPath.read(mockMvc.perform(withToken(
                        delete("/api/projects/" + projectId + "/files/" + bRecordId), otherMemberToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        //上传人本人可删自己的
        assertEquals(0, (int) JsonPath.read(mockMvc.perform(withToken(
                        delete("/api/projects/" + projectId + "/files/" + bRecordId), memberToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT deleted FROM project_file WHERE id = ?", Integer.class, bRecordId));
        //所有人可删成员上传的
        String cUpload = upload(memberToken, "再传一份.pdf", "DOC");
        long cRecordId = Long.parseLong(JsonPath.read(cUpload, "$.data.id"));
        assertEquals(0, (int) JsonPath.read(mockMvc.perform(withToken(
                        delete("/api/projects/" + projectId + "/files/" + cRecordId), ownerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
        //跨项目删：另一项目存在但记录不属于它 → 1004（记录与项目不匹配）
        String otherBody = createProject(ownerToken, "{\"name\":\"文件旁项目\",\"visibility\":\"ORG\"}");
        long otherProjectId = Long.parseLong(JsonPath.read(otherBody, "$.data.id"));
        assertEquals(1004, (int) JsonPath.read(mockMvc.perform(withToken(
                        delete("/api/projects/" + otherProjectId + "/files/" + cRecordId), ownerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
    }

    // ---------------------------------------------------------------- PR28 下载

    @Test
    void downloadRespectsVisibilityAndStreams() throws Exception {
        String upload = upload(ownerToken, "下载验证.pdf", "DOC");
        assertEquals(0, (int) JsonPath.read(upload, "$.code"), upload);
        long recordId = Long.parseLong(JsonPath.read(upload, "$.data.id"));
        //所有人下载：字节回环（真实 MinIO 往返）
        MvcResult result = mockMvc.perform(withToken(
                        get("/api/projects/files/" + recordId + "/download"), ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        assertArrayEquals(PDF_CONTENT, result.getResponse().getContentAsByteArray());
        //同部门学生（ORG 可见）也能下载
        mockMvc.perform(withToken(get("/api/projects/files/" + recordId + "/download"), otherMemberToken))
                .andExpect(status().isOk());
        //外部门学生 → 7003（可见性闸门先于文件）
        String denied = mockMvc.perform(withToken(
                        get("/api/projects/files/" + recordId + "/download"), outsiderToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertEquals(7003, (int) JsonPath.read(denied, "$.code"), denied);
        //每次下载计数 +1（骨架 streamFile 保留计数）
        Integer count = jdbcTemplate.queryForObject(
                "SELECT download_count FROM file_object WHERE id = "
                        + "(SELECT file_id FROM project_file WHERE id = ?)", Integer.class, recordId);
        assertEquals(2, count, "两次成功下载各计数一次");
        //不存在的记录 → 1004
        assertEquals(1004, (int) JsonPath.read(mockMvc.perform(withToken(
                        get("/api/projects/files/999999/download"), ownerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.code"));
    }
}
