package com.viomanager.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Step 6 验证（异步模式）：vio.audit.async 保持默认 true，
 * 审计由后台单线程写库，接口立即返回——轮询确认行最终落库。
 * ⚠️ 本类不加 @Transactional：异步线程写库在测试事务之外，回滚不掉，
 * 所以用 @AfterEach 手工删测试数据。这是唯一允许脏数据进开发库再手工清理的测试写法。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuditAsyncTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        // 防上次异常退出留下的残留导致登录名撞唯一键，先清一道
        jdbcTemplate.update("DELETE FROM sys_user WHERE login_name = ?", "audit_async_user");
        jdbcTemplate.update("DELETE FROM audit_log WHERE login_name = ?", "audit_async_user");
        SysUser user = new SysUser();
        user.setLoginName("audit_async_user");
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole("STUDENT");
        user.setRealName("异步审计用户");
        user.setDefaultPassword(0);
        user.setStatus(1);
        sysUserMapper.insert(user);
        userId = user.getId();
        MvcResult mvcResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"audit_async_user\",\"password\":\"pass123456\"}"))
                .andReturn();
        token = JsonPath.read(mvcResult.getResponse().getContentAsString(), "$.data.token");
    }

    @AfterEach
    void cleanUp() {
        // 异步线程写库不受测试事务回滚控制，必须手工删
        jdbcTemplate.update("DELETE FROM audit_log WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", userId);
    }

    @Test
    void asyncWriteVisibleWithinSeconds() throws Exception {
        // 调探针：接口不等写库直接返回成功
        mockMvc.perform(post("/api/dev/audit-probe")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"异步验证\",\"fail\":false}"))
                .andExpect(status().isOk());
        // 轮询等后台线程写完（上限 3 秒，正常几十毫秒）
        Map<String, Object> row = null;
        for (int i = 0; i < 30; i++) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM audit_log WHERE user_id = ? AND biz_type = 'DEV_AUDIT_PROBE'", userId);
            if (!rows.isEmpty()) {
                row = rows.get(0);
                break;
            }
            Thread.sleep(100);
        }
        assertNotNull(row, "异步审计应在数秒内落库");
        assertEquals("audit_async_user", row.get("login_name"));
        assertEquals("操作内容：异步验证", row.get("summary"));
    }
}
