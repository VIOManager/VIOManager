package com.viomanager.auth.service;

import com.viomanager.auth.entity.SysUser;
import com.viomanager.auth.mapper.SysUserMapper;
import com.viomanager.common.auth.AuthTokenChecker;
import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * 管理系统侧的 token 附加校验：验签之后查库确认账号仍然有效。
 * 论坛阶段2 不装配这个 Bean，过滤器只验签——这是微服务库边界（架构调整方案 §4）。
 * 注：这是校验组件不是配置类，按包职责归 service 包（组长规矩：配置类才进 config 包）。
 */
@Component
public class ManagementTokenChecker implements AuthTokenChecker {

    @Resource
    private SysUserMapper sysUserMapper;

    @Override
    public void check(CurrentUser user) {
        //1.查账号
        SysUser sysUser = sysUserMapper.selectById(user.getUserId());
        //2.账号不存在、已删、停用，一律按未登录处理（1002 + 401）
        if (sysUser == null || sysUser.getStatus() == null || sysUser.getStatus() != 1) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED);
        }
        //3.改密踢人：库里的 pwd_updated_at 比 token 签发时新，说明这是改密前的旧 token
        LocalDateTime dbTime = sysUser.getPwdUpdatedAt();
        long dbMillis = dbTime == null ? 0L : dbTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (dbMillis > user.getPwdUpdatedAt()) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED);
        }
    }
}
