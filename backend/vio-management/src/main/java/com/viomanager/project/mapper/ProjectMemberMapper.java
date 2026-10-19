package com.viomanager.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.project.entity.ProjectMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目成员 Mapper。@DataScope(SELF)：查询均由 service 按项目可见性显式控制（同 ProjectMapper 理由）。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface ProjectMemberMapper extends BaseMapper<ProjectMember> {
}
