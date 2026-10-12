package com.viomanager.dev;

import com.viomanager.common.datascop.DataScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 仅开发期数据范围探针用的 mapper：两个方法分别声明 ORG 与 SELF，
 * 用于验证拦截器对不同角色的注入与拒绝行为。
 */
@Mapper
public interface ScopeProbeMapper {

    /**
     * ORG 声明：部门管理员会被注入"本部门子树"过滤；超管不注入查全量；学生被默认拒绝。
     */
    @DataScope(scope = DataScope.Scope.ORG, orgColumn = "org_id")
    @Select("SELECT COUNT(*) FROM member WHERE deleted = 0")
    Long countMembersInScope();

    /**
     * SELF 声明：SQL 自带本人条件（user_id 限当前用户），各角色放行。
     */
    @DataScope(scope = DataScope.Scope.SELF)
    @Select("SELECT COUNT(*) FROM sys_login_log WHERE user_id = #{userId}")
    Long countMyLoginLogs(@Param("userId") Long userId);
}
