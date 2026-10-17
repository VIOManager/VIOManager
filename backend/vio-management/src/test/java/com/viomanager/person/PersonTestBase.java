package com.viomanager.person;

import com.jayway.jsonpath.JsonPath;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import com.viomanager.person.support.PersonSensitiveSupport;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

/**
 * 人员模块测试公共基类：容器装配 + 造人 / 建组织 / 建队伍 / 登录的通用辅助。
 * person 接口几乎都挂 @AuditLog（含 M10/M11 敏感查看），统一 vio.audit.async=false
 * 让审计同步落库，@Transactional 测试结束一起回滚，不污染演示数据。
 * 造数 ID 统一用 94xxx 段，避开演示数据（member 101-105 / 审批 801-807）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "vio.audit.async=false")
public abstract class PersonTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected SysUserMapper sysUserMapper;

    @Autowired
    protected BCryptPasswordEncoder passwordEncoder;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Resource
    protected PersonSensitiveSupport sensitiveSupport;

    /** 造一个指定角色 / 组织 / 人员档案绑定的用户并登录拿 token */
    protected String createUserAndLogin(String loginName, String role, Long orgId, Long memberId)
            throws Exception {
        SysUser user = new SysUser();
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode("pass123456"));
        user.setRole(role);
        user.setOrgId(orgId);
        user.setMemberId(memberId);
        user.setRealName("人员探针-" + loginName);
        user.setDefaultPassword(0);
        user.setStatus(1);
        sysUserMapper.insert(user);
        MvcResult loginResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"pass123456\"}"))
                .andReturn();
        return JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data.token");
    }

    /** 插一条组织（path 前缀决定子树归属） */
    protected void insertOrg(Long id, String name, String path) {
        jdbcTemplate.update(
                "INSERT INTO sys_org (id, parent_id, name, org_type, path) VALUES (?, 0, ?, 'DEPARTMENT', ?)",
                id, name, path);
    }

    /** 插一条人员档案：身份证走真实 AES 加密，手机号固定，便于断言裁剪 */
    protected void insertPerson(Long id, String name, String studentNo, Long orgId, String idCardPlain) {
        jdbcTemplate.update(
                "INSERT INTO member (id, name, student_no, college, class_name, org_id, phone, "
                        + "id_card, id_card_last4, member_type, status, has_account) "
                        + "VALUES (?, ?, ?, '计算机学院', '软件2301', ?, '13800001111', ?, ?, 'STUDENT', 1, 0)",
                id, name, studentNo, orgId, sensitiveSupport.encryptIdCard(idCardPlain),
                idCardPlain == null ? null : idCardPlain.substring(idCardPlain.length() - 4));
    }

    /** 插一条 SKILL 字典项（PRESET 已审核口径；M12 校验的取值来源） */
    protected void insertSkillItem(Long id, String value, String label) {
        jdbcTemplate.update(
                "INSERT INTO sys_dict_item (id, dict_code, item_value, item_label, source, audit_status, status) "
                        + "VALUES (?, 'SKILL', ?, ?, 'PRESET', 'APPROVED', 1)", id, value, label);
    }

    /** 插一支活跃队伍（contest/semester 用假 ID：表无外键，逻辑引用即可） */
    protected void insertTeam(Long id, String name, Long orgId, Long leaderMemberId) {
        jdbcTemplate.update(
                "INSERT INTO team (id, name, contest_id, semester_id, org_id, leader_member_id, status) "
                        + "VALUES (?, ?, 999001, 999001, ?, ?, 'ACTIVE')", id, name, orgId, leaderMemberId);
    }

    /** 插一条队伍成员关系（isLeader 1 队长 0 队员） */
    protected void insertTeamMember(Long id, Long teamId, Long memberId, String memberName, int isLeader,
                                     String status) {
        jdbcTemplate.update(
                "INSERT INTO team_member (id, team_id, member_id, member_name, is_leader, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", id, teamId, memberId, memberName, isLeader, status);
    }

    /** COUNT 断言辅助 */
    protected int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
