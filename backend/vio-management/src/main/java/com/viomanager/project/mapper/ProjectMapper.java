package com.viomanager.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.project.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 项目 Mapper。
 *
 * ⚠️ @DataScope 声明 SELF 的理由同 AchievementMapper：项目可见性是八条规则的特殊语义
 * （所有人/成员/教师/子树/PUBLIC/ORG/授权），拦截器三种模式都表达不了，由 service 经
 * ProjectVisibilitySupport 显式控制——本 mapper 的所有查询 SQL 均自带可见性条件。
 * 若组长要求管理端列表自动按部门子树过滤，可改 ORG，但学生列表仍需 SELF 路径。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface ProjectMapper extends BaseMapper<Project> {

    /**
     * 状态条件流转（PR04）：WHERE 带旧状态，0 行命中=已被并发改变。
     * updated_at 由列上 ON UPDATE CURRENT_TIMESTAMP 自动刷新（真实状态变更，刻意保留）。
     */
    @Update("UPDATE project SET status = #{to}, updated_by = #{operator} "
            + "WHERE id = #{id} AND status = #{from} AND deleted = 0")
    int casStatus(@Param("id") Long id, @Param("from") String from,
                  @Param("to") String to, @Param("operator") Long operator);
}
