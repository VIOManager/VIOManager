package com.viomanager.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.viomanager.auth.entity.SysLoginLog;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysLoginLogMapper;
import com.viomanager.auth.mapper.SysUserMapper;
import com.viomanager.auth.service.AccountService;
import com.viomanager.common.exception.BizException;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 3 验证：认证与账号全链路。
 * 覆盖方案验证栏整条链：登录拿 token → 带 token 访问通过 → 篡改 token 1002 →
 * 改密后旧 token 1002 → 停用账号 1002；外加登录失败分支、登录日志留痕、AccountService 契约。
 * @Transactional 每例回滚，账号与日志不留在开发库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysLoginLogMapper sysLoginLogMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Resource
    private AccountService accountService;

    private SysUser normalUser;

    @BeforeEach
    void setUp() {
        // 正常启用账号
        normalUser = new SysUser();
        normalUser.setLoginName("probe_normal");
        normalUser.setPassword(passwordEncoder.encode("pass123456"));
        normalUser.setRole("STUDENT");
        normalUser.setRealName("探针用户");
        normalUser.setDefaultPassword(0);
        normalUser.setStatus(1);
        sysUserMapper.insert(normalUser);

        // 停用账号
        SysUser disabledUser = new SysUser();
        disabledUser.setLoginName("probe_disabled");
        disabledUser.setPassword(passwordEncoder.encode("pass123456"));
        disabledUser.setRole("STUDENT");
        disabledUser.setRealName("停用用户");
        disabledUser.setDefaultPassword(0);
        disabledUser.setStatus(0);
        sysUserMapper.insert(disabledUser);
    }

    /** 登录成功并记一条成功日志 */
    @Test
    void loginSuccessReturnsTokenAndFlag() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"probe_normal\",\"password\":\"pass123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.defaultPassword").value(false))
                .andExpect(jsonPath("$.data.role").value("STUDENT"));
        Long logs = sysLoginLogMapper.selectCount(
                new QueryWrapper<SysLoginLog>().eq("login_name", "probe_normal").eq("success", 1));
        assertEquals(1L, logs);
    }

    /** 密码错误 → 2001，且失败也留日志 */
    @Test
    void loginWrongPasswordReturns2001AndLogs() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"probe_normal\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2001));
        Long logs = sysLoginLogMapper.selectCount(
                new QueryWrapper<SysLoginLog>().eq("login_name", "probe_normal").eq("success", 0));
        assertEquals(1L, logs);
    }

    /** 停用账号 → 2002 */
    @Test
    void loginDisabledAccountReturns2002() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"probe_disabled\",\"password\":\"pass123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2002));
    }

    /** 不带 token 访问受保护接口 → HTTP 401 + code 1002 */
    @Test
    void accessWithoutTokenReturns401With1002() throws Exception {
        mockMvc.perform(get("/api/test/biz-error"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    /** 有效 token 放行（探针端点返回业务码 6001，说明过了认证关） */
    @Test
    void accessWithValidTokenPasses() throws Exception {
        String token = loginOk("probe_normal", "pass123456");
        mockMvc.perform(get("/api/test/biz-error").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(6001));
    }

    /** 篡改 token → 401 + 1002 */
    @Test
    void tamperedTokenRejected() throws Exception {
        String token = loginOk("probe_normal", "pass123456");
        mockMvc.perform(get("/api/test/biz-error").header("Authorization", "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    /** 改密后旧 token 失效，新密码能登录 */
    @Test
    void changePasswordInvalidatesOldToken() throws Exception {
        //1.登录拿 token
        String token = loginOk("probe_normal", "pass123456");
        //2.改密
        mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"pass123456\",\"newPassword\":\"newpass456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        //3.旧 token 再用 → 401 + 1002（pwd_updated_at 比对踢掉）
        mockMvc.perform(get("/api/test/biz-error").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
        //4.新密码能登录
        loginOk("probe_normal", "newpass456");
    }

    /** 改密时原密码错 → 2004 */
    @Test
    void changePasswordWithWrongOldReturns2004() throws Exception {
        String token = loginOk("probe_normal", "pass123456");
        mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"wrong-old\",\"newPassword\":\"newpass456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2004));
    }

    /** 登录后账号被停用 → token 立即失效 */
    @Test
    void disabledAfterLoginKicksToken() throws Exception {
        //1.登录拿 token
        String token = loginOk("probe_normal", "pass123456");
        //2.管理侧把账号停用
        normalUser.setStatus(0);
        sysUserMapper.updateById(normalUser);
        //3.旧 token → 401 + 1002
        mockMvc.perform(get("/api/test/biz-error").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    /** health 免认证 */
    @Test
    void healthSkipsAuth() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /** AccountService：绑定账号用初始密码 abc123 可登录且弹改密标记 */
    @Test
    void bindAccountUsesInitialPassword() throws Exception {
        accountService.bindAccount(123L, "probe_bind", "STUDENT", null, "绑定用户");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"probe_bind\",\"password\":\"abc123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.defaultPassword").value(true));
    }

    /** AccountService：登录名重复 → 2003 */
    @Test
    void bindDuplicateLoginNameThrows2003() {
        accountService.bindAccount(1L, "probe_dup", "STUDENT", null, "用户一");
        BizException e = assertThrows(BizException.class,
                () -> accountService.bindAccount(2L, "probe_dup", "STUDENT", null, "用户二"));
        assertEquals(2003, e.getCode());
    }

    /** AccountService：重置密码回到初始值且 pwd_updated_at 更新（旧 token 会被踢） */
    @Test
    void resetPasswordRestoresInitial() {
        Long accountId = accountService.bindAccount(1L, "probe_reset", "STUDENT", null, "重置用户");
        accountService.resetPassword(accountId);
        SysUser reloaded = sysUserMapper.selectById(accountId);
        assertEquals(1, reloaded.getDefaultPassword());
        assertNotNull(reloaded.getPwdUpdatedAt());
    }

    /** G1 防爆破：连续失败 5 次后锁 15 分钟，锁定期正确密码也回 2001 */
    @Test
    void fiveFailuresLockLogin() throws Exception {
        //1.连续输错 5 次
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"loginName\":\"probe_normal\",\"password\":\"wrong-pass\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(2001));
        }
        //2.第 6 次用正确密码 → 仍然 2001（锁定中，不暴露锁定状态）
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"probe_normal\",\"password\":\"pass123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2001));
    }

    /** G1 防爆破：15 分钟窗口外的旧失败不构成锁定 */
    @Test
    void oldFailuresDoNotLock() throws Exception {
        //1.手动插入 5 条 16 分钟前的失败日志（createdAt 显式赋值，填充器只补空值不会覆盖）
        for (int i = 0; i < 5; i++) {
            SysLoginLog oldFail = new SysLoginLog();
            oldFail.setUserId(normalUser.getId());
            oldFail.setLoginName("probe_normal");
            oldFail.setLoginType("PASSWORD");
            oldFail.setSuccess(0);
            oldFail.setFailReason("登录名或密码错误");
            oldFail.setCreatedAt(java.time.LocalDateTime.now().minusMinutes(16));
            sysLoginLogMapper.insert(oldFail);
        }
        //2.正确密码能正常登录（没锁）
        loginOk("probe_normal", "pass123456");
    }

    /** G1 防爆破：成功登录重置连败计数 */
    @Test
    void successResetsFailureStreak() throws Exception {
        //1.失败 4 次（不足 5 次）
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"loginName\":\"probe_normal\",\"password\":\"wrong-pass\"}"))
                    .andExpect(jsonPath("$.code").value(2001));
        }
        //2.成功一次，连败清零
        loginOk("probe_normal", "pass123456");
        //3.再失败 4 次 → 累计仍是 4 连败，正确密码可登录
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"loginName\":\"probe_normal\",\"password\":\"wrong-pass\"}"))
                    .andExpect(jsonPath("$.code").value(2001));
        }
        loginOk("probe_normal", "pass123456");
    }

    /** 登录成功并把 token 取出来给后续请求用 */
    private String loginOk(String loginName, String password) throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + loginName + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String body = mvcResult.getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.token");
    }
}
