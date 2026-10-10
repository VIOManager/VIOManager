package com.viomanager.roleguard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 4.5 验证：@RequireRole 角色校验。
 * 两个 dev 探针接口（仅超管 / 两种管理员），三种角色各打一遍：
 * 够格的放行，不够格的 1003 + HTTP 403；未登录 401 + 1002。
 * @Transactional 每例回滚，测试账号不留开发库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class RequireRoleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    private String superToken;

    private String deptToken;

    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        // 三种角色各造一人、各拿 token
        superToken = createUserAndLogin("role_probe_super", "SUPER_ADMIN");
        deptToken = createUserAndLogin("role_probe_dept", "DEPT_ADMIN");
        studentToken = createUserAndLogin("role_probe_student", "STUDENT");
    }

    @Test
    void superOnlyEndpointAllowsSuperAdmin() throws Exception {
        mockMvc.perform(get("/api/dev/role-probe/super-only")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.role").value("SUPER_ADMIN"));
    }

    @Test
    void superOnlyEndpointRejectsOtherRoles() throws Exception {
        // 部门管理员与学生一样：仅超管的接口一概 403 + 1003
        mockMvc.perform(get("/api/dev/role-probe/super-only")
                        .header("Authorization", "Bearer " + deptToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
        mockMvc.perform(get("/api/dev/role-probe/super-only")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    void adminBothEndpointAllowsTwoAdminRoles() throws Exception {
        // 超管、部门管理员都放行
        mockMvc.perform(get("/api/dev/role-probe/admin-both")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/dev/role-probe/admin-both")
                        .header("Authorization", "Bearer " + deptToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.role").value("DEPT_ADMIN"));
    }

    @Test
    void adminBothEndpointRejectsStudent() throws Exception {
        mockMvc.perform(get("/api/dev/role-probe/admin-both")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    void roleEndpointsRequireLogin() throws Exception {
        // 不带 token：JWT 过滤器先拦，401 + 1002（轮不到角色切面）
        mockMvc.perform(get("/api/dev/role-probe/super-only"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    /** 造一个指定角色的用户并登录拿 token */
    private String createUserAndLogin(String loginName, String role) throws Exception {
        SysUser user = new SysUser();
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole(role);
        user.setRealName("角色探针-" + role);
        user.setDefaultPassword(0);
        user.setStatus(1);
        sysUserMapper.insert(user);
        MvcResult mvcResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"pass123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return JsonPath.read(mvcResult.getResponse().getContentAsString(), "$.data.token");
    }
}
