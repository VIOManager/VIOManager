package com.viomanager.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 8 补充验证：扫描服务不可用策略（组长 2026-10-10 拍板——不因服务故障影响用户）。
 * 把 ClamAV 端口故意指到一个没人监听的端口模拟服务挂掉：
 * 上传照常成功、scanStatus=UNAVAILABLE、库里有 error 留痕；文件还能正常下载（只有 INFECTED 才拦）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {"vio.audit.async=false", "vio.file.clamav.port=3999"})
class ClamAvDownTest {

    private static final byte[] PDF_CONTENT = "%PDF-1.4\n%扫描服务不可用策略验证\n%%EOF".getBytes();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        SysUser user = new SysUser();
        user.setLoginName("clamdown_student");
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole("STUDENT");
        user.setRealName("扫描挂机探针");
        user.setDefaultPassword(0);
        user.setStatus(1);
        sysUserMapper.insert(user);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"clamdown_student\",\"password\":\"pass123456\"}"))
                .andReturn();
        token = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data.token");
    }

    @Test
    void uploadSucceedsAndMarkedUnavailableWhenScannerDown() throws Exception {
        // 扫描服务连不上：上传照样成功（不返回 1011），scanStatus 告知"未扫描"
        MockMultipartFile file = new MockMultipartFile(
                "file", "资料.pdf", "application/pdf", PDF_CONTENT);
        MvcResult result = mockMvc.perform(multipart("/api/files")
                        .file(file)
                        .param("bizType", "MATERIAL")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Idempotency-Key", "clamdown-key-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.scanStatus").value("UNAVAILABLE"))
                .andReturn();
        long fileId = Long.parseLong(
                JsonPath.read(result.getResponse().getContentAsString(), "$.data.fileId"));
        // 落库同样是 UNAVAILABLE（管理员事后可按此标记重扫）
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT scan_status FROM file_object WHERE id = ?", fileId);
        assertEquals("UNAVAILABLE", row.get("scan_status"));
    }

    @Test
    void unavailableFileIsStillDownloadable() throws Exception {
        // 先传一份（扫描挂 → UNAVAILABLE），再下载：只有染毒文件才拦，未扫描的照常放行
        MockMultipartFile file = new MockMultipartFile(
                "file", "可下载.pdf", "application/pdf", PDF_CONTENT);
        MvcResult uploadResult = mockMvc.perform(multipart("/api/files")
                        .file(file)
                        .param("bizType", "MATERIAL")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Idempotency-Key", "clamdown-key-2"))
                .andExpect(status().isOk())
                .andReturn();
        long fileId = Long.parseLong(
                JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.data.fileId"));
        MvcResult downloadResult = mockMvc.perform(get("/api/files/" + fileId + "/download")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertArrayEquals(PDF_CONTENT, downloadResult.getResponse().getContentAsByteArray());
    }
}
