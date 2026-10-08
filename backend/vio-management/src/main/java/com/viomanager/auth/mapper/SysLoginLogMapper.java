package com.viomanager.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.auth.entity.SysLoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志 mapper。
 */
@Mapper
public interface SysLoginLogMapper extends BaseMapper<SysLoginLog> {
}
