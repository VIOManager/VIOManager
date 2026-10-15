package com.viomanager.project;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 项目管理模块测试基类（M1–M6 共用夹具）。
 * 与 FileServiceTest 同款基建：真实 dev 库 + MockMvc 全链路；
 * 差异是 PM 的业务身份是 member 档案（owner/memberId 口径），故用户一律绑档案造号
 * （纯管理账号走 createAdminAndLogin 不绑档案）。
 * @Transactional 不在基类——单线程用例各自声明（回滚）；并发套件（PmConcurrencyTest）
 * 不能有事务（子线程开独立事务提交后主线程回滚不了），靠独立 ID 段 + @AfterEach 自清理。
 * 审计切同步模式便于事务内断言（后续里程碑用）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"vio.audit.async=false", "vio.pm.cache.enabled=false"})
public abstract class PmTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected SysUserMapper sysUserMapper;

    @Autowired
    protected BCryptPasswordEncoder passwordEncoder;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Resource
    protected WebApplicationContext applicationContext;

    /** 建组织（同 FileServiceTest 口径：DEPARTMENT 类型，path 决定子树判定） */
    protected void insertOrg(Long id, String name, String path) {
        jdbcTemplate.update(
                "INSERT INTO sys_org (id, parent_id, name, org_type, path) VALUES (?, 0, ?, 'DEPARTMENT', ?)",
                id, name, path);
    }

    /** 建人员档案（member 表：PM 一切业务身份的锚点） */
    protected void insertMember(Long id, String name, Long orgId, String memberType) {
        jdbcTemplate.update(
                "INSERT INTO member (id, name, org_id, member_type, status, has_account) "
                        + "VALUES (?, ?, ?, ?, 1, 1)",
                id, name, orgId, memberType);
    }

    /** 建绑定档案的登录账号并登录拿 token（学生/教师/部门管理员用） */
    protected String createUserAndLogin(String loginName, String role, Long orgId, Long memberId)
            throws Exception {
        SysUser user = new SysUser();
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole(role);
        user.setOrgId(orgId);
        user.setMemberId(memberId);
        user.setRealName("项目探针-" + loginName);
        user.setDefaultPassword(0);
        user.setStatus(1);
        sysUserMapper.insert(user);
        return login(loginName);
    }

    /** 建不绑档案的纯管理账号（超管场景：member_id 为 NULL，建项目应被拦） */
    protected String createAdminAndLogin(String loginName, String role) throws Exception {
        SysUser user = new SysUser();
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole(role);
        user.setRealName("项目探针-" + loginName);
        user.setDefaultPassword(0);
        user.setStatus(1);
        sysUserMapper.insert(user);
        return login(loginName);
    }

    /**
     * 建一个确定性的当前学期：先清掉库里已有 is_current（事务内不可见、回滚即还原），
     * 保证"创建默认取当前学期"的断言不被 dev 库真实数据干扰。
     */
    protected Long insertCurrentSemester(Long id, String name) {
        jdbcTemplate.update("UPDATE semester SET is_current = 0 WHERE is_current = 1 AND deleted = 0");
        jdbcTemplate.update(
                "INSERT INTO semester (id, name, status, is_current) VALUES (?, ?, 'ACTIVE', 1)",
                id, name);
        return id;
    }

    /** 登录拿 token */
    protected String login(String loginName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"pass123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    /** 带鉴权头的请求构造器（null token = 匿名） */
    protected MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder builder, String token) {
        return token == null ? builder : builder.header("Authorization", "Bearer " + token);
    }

    /** POST /api/projects 建项目，返回响应体字符串 */
    protected String createProject(String token, String body) throws Exception {
        return mockMvc.perform(withToken(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON), token)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
