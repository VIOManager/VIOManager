package com.viomanager.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录账号 mapper。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
