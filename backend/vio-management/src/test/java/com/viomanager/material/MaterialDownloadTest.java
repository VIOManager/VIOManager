package com.viomanager.material;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.approval.ApprovalTestBase;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 材料下载验证（MS04/MS05）：队友可下本人上传的提交文件（业务权限，非 F02 的仅本人矩阵）、
 * 非本队成员 5009、全队打包 zip 结构与内容逐字节还原、记录不存在 1004。
 * 文件走 /api/files 真实上传（MinIO + ClamAV 真链路）。
 */
@TestPropertySource(properties = "vio.audit.async=false")
class MaterialDownloadTest extends ApprovalTestBase {

    private static final long TEAM_ID = 88012L;
    private static final long CONTEST_ID = 4900202L;
    private static final long TASK_ID = 5200202L;
    private static final long REQ_ID = 5300202L;

    private static final byte[] PDF_CONTENT =
            "%PDF-1.4\n%VIOManager 打包测试文件\n%%EOF".getBytes();

    private String leaderToken;
    private String mateToken;
    private String alienToken;
    private long submissionId;
    private long fileId;

    @BeforeEach
    void setUp() throws Exception {
        insertOrg(9311L, "下载学院", "/0/9311/");
        insertMember(8821L, "上传队长", 9311L);
        insertMember(8822L, "下载队员", 9311L);
        insertMember(8825L, "路人甲", 9311L);
        jdbcTemplate.update(
                "INSERT INTO team (id, name, contest_id, semester_id, org_id, leader_member_id, status) "
                        + "VALUES (?, ?, ?, 999, ?, ?, 'ACTIVE')",
                TEAM_ID, "下载测试队", CONTEST_ID, 9311L, 8821L);
        insertTeamMember(TEAM_ID, 8821L, "上传队长", 1);
        insertTeamMember(TEAM_ID, 8822L, "下载队员", 0);
        jdbcTemplate.update(
                "INSERT INTO material_task (id, contest_id, semester_id, name) VALUES (?, ?, 999, '参赛材料')",
                TASK_ID, CONTEST_ID);
        jdbcTemplate.update(
                "INSERT INTO material_requirement (id, task_id, contest_id, material_type, name, allowed_formats) "
                        + "VALUES (?, ?, ?, 'REGISTRATION_FORM', '报名表', 'pdf')",
                REQ_ID, TASK_ID, CONTEST_ID);
        leaderToken = createUserAndLogin("mdt_leader", "STUDENT", 9311L, 8821L);
        mateToken = createUserAndLogin("mdt_mate", "STUDENT", 9311L, 8822L);
        alienToken = createUserAndLogin("mdt_alien", "STUDENT", 9311L, 8825L);
        //真实上传拿 fileId（MinIO + ClamAV 真链路），幂等键带时间戳避免撞车
        fileId = upload("mdt-" + System.nanoTime(), "报名表.pdf", leaderToken);
        submissionId = System.nanoTime();
        jdbcTemplate.update(
                "INSERT INTO material_submission (id, requirement_id, task_id, contest_id, team_id, "
                        + "semester_id, org_id, file_id, file_name, submitted_by) "
                        + "VALUES (?, ?, ?, ?, ?, 999, ?, ?, ?, ?)",
                submissionId, REQ_ID, TASK_ID, CONTEST_ID, TEAM_ID, 9311L, fileId, "报名表.pdf", 8821L);
    }

    @Test
    void teammateDownloadsSubmission() throws Exception {
        //8822 是队友但不是上传人：F02 的仅本人矩阵会拦，MS04 的业务权限（本队成员）必须放行
        MvcResult result = mockMvc.perform(get("/api/materials/submissions/" + submissionId + "/download")
                        .header("Authorization", "Bearer " + mateToken))
                .andExpect(status().isOk())
                .andReturn();
        assertArrayEquals(PDF_CONTENT, result.getResponse().getContentAsByteArray());
        Integer count = jdbcTemplate.queryForObject(
                "SELECT download_count FROM file_object WHERE id = ?", Integer.class, fileId);
        assert count != null && count == 1 : "下载计数应 +1";
    }

    @Test
    void nonMemberRejected() throws Exception {
        download().andExpect(jsonPath("$.code").value(5009));
    }

    @Test
    void submissionNotFound() throws Exception {
        mockMvc.perform(get("/api/materials/submissions/999999/download")
                        .header("Authorization", "Bearer " + mateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    void packageTeamZip() throws Exception {
        //全队打包：zip 魔数 PK、条目按"要求名/文件名"分目录、内容逐字节还原
        MvcResult result = mockMvc.perform(get("/api/materials/teams/" + TEAM_ID + "/package")
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andReturn();
        byte[] body = result.getResponse().getContentAsByteArray();
        assert body.length > 4 && body[0] == 'P' && body[1] == 'K' : "响应应是 zip 流（PK 魔数）";
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body))) {
            ZipEntry entry = zip.getNextEntry();
            assert entry != null : "zip 内至少一个条目";
            assert entry.getName().startsWith("报名表/") : "条目应按要求名分目录，实际 " + entry.getName();
            byte[] buffer = new byte[4096];
            int len = zip.read(buffer);
            assertArrayEquals(PDF_CONTENT, Arrays.copyOf(buffer, len));
        }
    }

    @Test
    void packageTeamByNonMemberRejected() throws Exception {
        mockMvc.perform(get("/api/materials/teams/" + TEAM_ID + "/package")
                        .header("Authorization", "Bearer " + alienToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5009));
    }

    private ResultActions download() throws Exception {
        return mockMvc.perform(get("/api/materials/submissions/" + submissionId + "/download")
                        .header("Authorization", "Bearer " + alienToken))
                .andExpect(status().isOk());
    }

    private long upload(String idemKey, String filename, String token) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "application/pdf", PDF_CONTENT);
        MvcResult result = mockMvc.perform(multipart("/api/files")
                        .file(file)
                        .param("bizType", "MATERIAL")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Idempotency-Key", idemKey))
                .andExpect(status().isOk())
                .andReturn();
        return Long.parseLong(JsonPath.read(result.getResponse().getContentAsString(), "$.data.fileId"));
    }

    private void insertMember(long id, String name, long orgId) {
        jdbcTemplate.update(
                "INSERT INTO member (id, name, org_id, member_type) VALUES (?, ?, ?, 'STUDENT')",
                id, name, orgId);
    }

    private void insertTeamMember(long teamId, long memberId, String name, int isLeader) {
        jdbcTemplate.update(
                "INSERT INTO team_member (id, team_id, member_id, member_name, member_org_id, is_leader, status, joined_at) "
                        + "VALUES (?, ?, ?, ?, 9311, ?, 'CONFIRMED', NOW())",
                System.nanoTime(), teamId, memberId, name, isLeader);
    }
}
