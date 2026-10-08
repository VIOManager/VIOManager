package com.viomanager.auth.service.impl;

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
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录账号服务实现。
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements IUserService {

    @Resource
    private SysLoginLogMapper sysLoginLogMapper;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private BCryptPasswordEncoder passwordEncoder;

    @Override
    public Result<LoginVO> login(LoginDTO loginDTO, String ip, String userAgent) {
        //1.按登录名查账号
        SysUser user = query().eq("login_name", loginDTO.getLoginName()).one();
        //2.账号不存在或密码不对——统一报 2001，不区分提示，避免撞库探测
        if (user == null || !passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            //2.1失败也记登录日志（user 为空时 user_id 记 null）
            saveLoginLog(user == null ? null : user.getId(), loginDTO.getLoginName(), 0, "登录名或密码错误", ip, userAgent);
            return Result.fail(AuthErrorCode.LOGIN_FAILED);
        }
        //3.账号停用
        if (user.getStatus() == null || user.getStatus() != 1) {
            saveLoginLog(user.getId(), loginDTO.getLoginName(), 0, "账号已停用", ip, userAgent);
            return Result.fail(AuthErrorCode.ACCOUNT_DISABLED);
        }
        //4.生成 JWT，pwdUpdatedAt 毫秒值进载荷，改密踢人靠它比对
        long pwdUpdatedAt = toMillis(user.getPwdUpdatedAt());
        String token = jwtUtil.createToken(user.getId(), user.getRole(), user.getOrgId(), user.getRealName(), pwdUpdatedAt);
        //5.记成功日志 + 更新最后登录信息
        saveLoginLog(user.getId(), loginDTO.getLoginName(), 1, null, ip, userAgent);
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ip);
        updateById(user);
        //6.组装返回
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
