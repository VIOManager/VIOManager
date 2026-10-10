package com.viomanager.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.common.datascop.DataScope;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录账号 mapper。
 * SELF 声明：认证模块的查询自管数据权限——登录按登录名查（无登录态）、
 * 改密按本人 ID 查、token 校验发生在登录态建立之前，都不该被子树过滤和学生默认拒绝拦截。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface SysUserMapper extends BaseMapper<SysUser> {
}
