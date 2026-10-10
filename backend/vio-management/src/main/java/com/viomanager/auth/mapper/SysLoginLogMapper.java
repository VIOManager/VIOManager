package com.viomanager.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.auth.entity.SysLoginLog;
import com.viomanager.common.datascop.DataScope;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志 mapper。SELF 声明：日志查询（G1 锁定判定等）发生在登录态建立之前，放行。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface SysLoginLogMapper extends BaseMapper<SysLoginLog> {
}
