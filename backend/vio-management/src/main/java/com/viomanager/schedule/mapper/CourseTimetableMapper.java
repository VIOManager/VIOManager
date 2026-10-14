package com.viomanager.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.schedule.entity.CourseTimetable;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课表记录 mapper（单表 CRUD 走 BaseMapper）。
 * 数据范围说明：课表记录仅管理端核对查询（TT08），可见性由 ClassMappingService 显式控制；
 * 空课计算（SC06/SC07）按学期整段读取后内存判定单双周，不按组织裁剪（课表本身全平台共享）。
 */
@Mapper
@DataScope(scope = DataScope.Scope.ALL)
public interface CourseTimetableMapper extends BaseMapper<CourseTimetable> {
}
