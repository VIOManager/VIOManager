package com.viomanager.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import com.viomanager.auth.service.AccountService;
import com.viomanager.common.enums.AuthErrorCode;
import com.viomanager.common.exception.BizException;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 账号管理实现，A 线人员档案模块在进程内调用（K4：HTTP 面由 A 线自己的接口出）。
 */
@Service
public class AccountServiceImpl implements AccountService {

    /** 初始密码，PRD §3.1 约定 */
    public static final String INITIAL_PASSWORD = "abc123";

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private BCryptPasswordEncoder passwordEncoder;

    @Override
    public Long bindAccount(Long memberId, String loginName, String role, Long orgId, String realName) {
        //1.登录名查重（查询自动带 deleted=0，不与已删账号比较）
        SysUser exists = sysUserMapper.selectOne(new QueryWrapper<SysUser>().eq("login_name", loginName));
        if (exists != null) {
            throw new BizException(AuthErrorCode.LOGIN_NAME_EXISTS);
        }
        //2.建账号：初始密码 abc123、defaultPassword=1、启用状态
        SysUser user = new SysUser();
        user.setMemberId(memberId);
        user.setLoginName(loginName);
        user.setPassword(passwordEncoder.encode(INITIAL_PASSWORD));
        user.setRole(role);
        user.setOrgId(orgId);
        user.setRealName(realName);
        user.setDefaultPassword(1);
        user.setStatus(1);
        sysUserMapper.insert(user);
        return user.getId();
    }

    @Override
    public void changeLoginName(Long userId, String newLoginName) {
        //1.新登录名查重
        SysUser exists = sysUserMapper.selectOne(new QueryWrapper<SysUser>().eq("login_name", newLoginName));
        if (exists != null) {
            throw new BizException(AuthErrorCode.LOGIN_NAME_EXISTS);
        }
        //2.更新登录名
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(AuthErrorCode.LOGIN_NAME_EXISTS, "账号不存在");
        }
        user.setLoginName(newLoginName);
        sysUserMapper.updateById(user);
    }

    @Override
    public void resetPassword(Long userId) {
        //1.回到初始密码 abc123，defaultPassword 回 1
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(AuthErrorCode.LOGIN_NAME_EXISTS, "账号不存在");
        }
        //2.pwd_updated_at 更新——旧 token 在下一次请求被过滤器比对踢掉，重置等于强制下线
        user.setPassword(passwordEncoder.encode(INITIAL_PASSWORD));
        user.setDefaultPassword(1);
        user.setPwdUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
    }
}
