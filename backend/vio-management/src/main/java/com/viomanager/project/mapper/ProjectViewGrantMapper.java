package com.viomanager.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.project.entity.ProjectViewGrant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目查看授权 Mapper。@DataScope(SELF)：授权列表仅所有人与管理员可见，由 service 显式控制。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface ProjectViewGrantMapper extends BaseMapper<ProjectViewGrant> {
}
