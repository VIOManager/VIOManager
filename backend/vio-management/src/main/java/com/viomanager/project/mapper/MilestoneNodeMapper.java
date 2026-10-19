package com.viomanager.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.project.entity.MilestoneNode;
import org.apache.ibatis.annotations.Mapper;

/**
 * 比赛时间节点 Mapper。@DataScope(SELF)：管理员看全部、普通用户仅 APPROVED，由 service 显式控制。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface MilestoneNodeMapper extends BaseMapper<MilestoneNode> {
}
