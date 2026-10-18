package com.viomanager.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.viomanager.auth.dto.LoginDTO;
import com.viomanager.auth.dto.LoginVO;
import com.viomanager.auth.dto.PasswordDTO;
import com.viomanager.auth.entity.SysLoginLog;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysLoginLogMapper;
import com.viomanager.auth.mapper.SysUserMapper;
import com.viomanager.auth.service.IUserService;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.JwtUtil;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.AuthErrorCode;
import com.viomanager.common.result.Result;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录账号服务实现。
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements IUserService {

    /** G1 防爆破（组长拍板）：连续失败 5 次锁 15 分钟 */
    private static final int LOCK_FAIL_LIMIT = 20;
    private static final int LOCK_MINUTES = 15;

    @Resource
    private SysLoginLogMapper sysLoginLogMapper;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private BCryptPasswordEncoder passwordEncoder;

    /**
     * 假的 BCrypt 散列：账号不存在时也拿它跑一次 matches，对齐两种场景的耗时——
     * 不然"账号不存在秒回、密码错慢回"的时间差能把登录名撞出来。
     */
    private String dummyHash;

    @PostConstruct
    public void initDummyHash() {
        dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Override
    public Result<LoginVO> login(LoginDTO loginDTO, String ip, String userAgent) {
        String loginName = loginDTO.getLoginName();
        //1.防爆破锁定检查：锁定期连正确密码也回 2001，不暴露"你被锁了"这个信息
        if (isLocked(loginName)) {
            saveLoginLog(null, loginName, 0, "锁定期间拒绝", ip, userAgent);
            return Result.fail(AuthErrorCode.LOGIN_FAILED);
        }
        //2.按登录名查账号
        SysUser user = query().eq("login_name", loginName).one();
        //3.账号不存在也跑一次 BCrypt 对齐耗时，统一报 2001——不区分提示，避免撞库探测
        String hashToCheck = user == null ? dummyHash : user.getPassword();
        boolean passwordOk = passwordEncoder.matches(loginDTO.getPassword(), hashToCheck);
        if (user == null || !passwordOk) {
            //3.1失败也记登录日志（user 为空时 user_id 记 null）
            saveLoginLog(user == null ? null : user.getId(), loginName, 0, "登录名或密码错误", ip, userAgent);
            return Result.fail(AuthErrorCode.LOGIN_FAILED);
        }
        //4.账号停用
        if (user.getStatus() == null || user.getStatus() != 1) {
            saveLoginLog(user.getId(), loginName, 0, "账号已停用", ip, userAgent);
            return Result.fail(AuthErrorCode.ACCOUNT_DISABLED);
        }
        //5.生成 JWT，pwdUpdatedAt 毫秒值进载荷，改密踢人靠它比对
        long pwdUpdatedAt = toMillis(user.getPwdUpdatedAt());
        String token = jwtUtil.createToken(user.getId(), user.getLoginName(), user.getRole(),
                user.getOrgId(), user.getRealName(), pwdUpdatedAt);
        //6.记成功日志 + 更新最后登录信息
        saveLoginLog(user.getId(), loginName, 1, null, ip, userAgent);
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ip);
        updateById(user);
        //7.组装返回
        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setUserId(user.getId());
        loginVO.setRealName(user.getRealName());
        loginVO.setRole(user.getRole());
        loginVO.setOrgId(user.getOrgId());
        loginVO.setDefaultPassword(user.getDefaultPassword() != null && user.getDefaultPassword() == 1);
        return Result.ok(loginVO);
    }

    @Override
    public Result<Void> changePassword(PasswordDTO passwordDTO) {
        //1.取当前登录人——只信 ThreadLocal 里的身份，不信任何请求参数
        CurrentUser current = UserHolder.getUser();
        SysUser user = getById(current.getUserId());
        //2.校验原密码
        if (user == null || !passwordEncoder.matches(passwordDTO.getOldPassword(), user.getPassword())) {
            return Result.fail(AuthErrorCode.OLD_PASSWORD_WRONG);
        }
        //3.更新密码与 pwd_updated_at——旧 token 下一次请求会被过滤器比对踢掉
        user.setPassword(passwordEncoder.encode(passwordDTO.getNewPassword()));
        user.setPwdUpdatedAt(LocalDateTime.now());
        user.setDefaultPassword(0);
        updateById(user);
        return Result.ok();
    }

    /**
     * G1 防爆破锁定判定（2026-10-08 安全审查拍板；2026-10-16 lzh 拍板阈值 5→20：开发期联调频繁误锁）：
     * 取该登录名最近 20 条登录日志，全是失败、且最早那条失败发生在 15 分钟内 → 锁定中。
     * ⚠️ 并发：两个请求同时到达可能都通过判定，多放行一两次可接受——
     * 聚合 sys_login_log 的方案就是不加锁不建表，lzh 已拍板。
     */
    private boolean isLocked(String loginName) {
        //1.取最近 20 条（new 到 old）
        List<SysLoginLog> recent = sysLoginLogMapper.selectList(
                new QueryWrapper<SysLoginLog>()
                        .eq("login_name", loginName)
                        .orderByDesc("created_at")
                        .last("limit " + LOCK_FAIL_LIMIT));
        //2.不足 20 条，或中间有一次成功，都不算锁定
        if (recent.size() < LOCK_FAIL_LIMIT) {
            return false;
        }
        for (SysLoginLog logRow : recent) {
            if (logRow.getSuccess() != null && logRow.getSuccess() == 1) {
                return false;
            }
        }
        //3.最早的那条失败（第 20 条）在 15 分钟内 → 锁定中
        SysLoginLog oldest = recent.get(recent.size() - 1);
        if (oldest.getCreatedAt() == null) {
            return false;
        }
        return oldest.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(LOCK_MINUTES));
    }

    /**
     * 记一条登录日志，成功失败都记（PRD §3.17 关键操作留痕）。
     */
    private void saveLoginLog(Long userId, String loginName, int success, String failReason, String ip, String userAgent) {
        SysLoginLog loginLog = new SysLoginLog();
        loginLog.setUserId(userId);
        loginLog.setLoginName(loginName);
        loginLog.setLoginType("PASSWORD");
        loginLog.setSuccess(success);
        loginLog.setFailReason(failReason);
        loginLog.setIp(ip);
        loginLog.setUserAgent(userAgent);
        sysLoginLogMapper.insert(loginLog);
    }

    /**
     * LocalDateTime 转毫秒时间戳，null 按 0 处理（从未改过密码）。
     */
    private long toMillis(LocalDateTime time) {
        if (time == null) {
            return 0L;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
