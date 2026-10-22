package com.viomanager.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.schedule.entity.ClassMapping;
import org.apache.ibatis.annotations.Mapper;

/**
 * 班级映射 mapper（单表 CRUD 走 BaseMapper）。
 * 数据范围说明：映射仅管理端确认与查询（TT06/TT07），可见性由 ClassMappingService 显式控制。
 * UNIQUE KEY (semester_id, class_key, deleted) 兜底重复导入：同键已存在时跳过而非报错（upsert 语义）。
 */
@Mapper
@DataScope(scope = DataScope.Scope.ALL)
public interface ClassMappingMapper extends BaseMapper<ClassMapping> {
}
