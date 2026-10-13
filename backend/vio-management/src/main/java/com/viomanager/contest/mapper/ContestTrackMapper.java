package com.viomanager.contest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.contest.entity.ContestTrack;
import org.apache.ibatis.annotations.Mapper;

/**
 * 比赛赛道 mapper（单表 CRUD 走 BaseMapper）。
 * 赛道数据无独立可见性需求，标 ALL 放行；可见性随比赛在 Service 层统一判定。
 */
@Mapper
@DataScope(scope = DataScope.Scope.ALL)
public interface ContestTrackMapper extends BaseMapper<ContestTrack> {
}
