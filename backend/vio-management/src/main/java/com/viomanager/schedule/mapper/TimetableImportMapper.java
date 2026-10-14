package com.viomanager.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.schedule.entity.TimetableImport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课表导入批次 mapper（单表 CRUD 走 BaseMapper）。
 * 数据范围说明（照 TeamMapper 先例标 ALL 放行拦截器）：批次仅管理端读写，
 * 列表可见性（超管全部 / 部门管理员本组织子树）由 TimetableImportService 按角色显式过滤；
 * 学生不直接访问本表（controller 层 @RequireRole 已挡）。
 */
@Mapper
@DataScope(scope = DataScope.Scope.ALL)
public interface TimetableImportMapper extends BaseMapper<TimetableImport> {
}
