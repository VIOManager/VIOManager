package com.viomanager.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.project.entity.TeamProjectRef;
import org.apache.ibatis.annotations.Mapper;

/**
 * 赛队挂载项目 Mapper。@DataScope(SELF)：查询均由 service 按项目/赛队可见性显式控制（同 ProjectMapper 理由）。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface TeamProjectRefMapper extends BaseMapper<TeamProjectRef> {
}
