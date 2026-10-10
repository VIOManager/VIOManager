package com.viomanager.approval;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.approval.service.ApprovalClient;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审批测试公共基类：容器装配 + 造人 / 建组织 / 登录的通用辅助。
 * 子类按需补 @TestPropertySource（涉及审计断言或审批动作的加 vio.audit.async=false）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class ApprovalTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected SysUserMapper sysUserMapper;

    @Autowired
    protected BCryptPasswordEncoder passwordEncoder;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Resource
    protected ApprovalClient approvalClient;

    /** 造一个指定角色 / 组织 / 人员档案绑定的用户并登录拿 token */
    protected String createUserAndLogin(String loginName, String role, Long orgId, Long memberId)
            throws Exception {
        SysUser user = new SysUser();
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole(role);
        user.setOrgId(orgId);
        user.setMemberId(memberId);
        user.setRealName("审批探针-" + loginName);
        user.setDefaultPassword(0);
        user.setStatus(1);
        sysUserMapper.insert(user);
        MvcResult loginResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"pass123456\"}"))
                .andReturn();
        return JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data.token");
    }

    /** 插一条组织（sys_org 非空列只给 name / org_type，其余走默认值） */
    protected void insertOrg(Long id, String name, String path) {
        jdbcTemplate.update(
                "INSERT INTO sys_org (id, parent_id, name, org_type, path) VALUES (?, 0, ?, 'DEPARTMENT', ?)",
                id, name, path);
    }
}
