package com.viomanager.semester.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.semester.entity.Semester;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学期 mapper（单表 CRUD 走 BaseMapper，无手写 SQL、无 XML）。
 * 数据范围 SELF（规范 §5.2）：学期是全平台数据，无组织维度；
 * 且接口层 S01-S08 仅超管可进（@RequireRole 收口），无需拦截器注入。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface SemesterMapper extends BaseMapper<Semester> {
}
