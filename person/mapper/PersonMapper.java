package com.viomanager.person.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.person.entity.Person;
import org.apache.ibatis.annotations.Mapper;

/**
 * 人员档案 mapper（单表 CRUD 走 BaseMapper，无手写 SQL、无 XML）。
 * 数据范围 ORG（规范 §5.2）：超管全量放行 / 部门管理员本组织子树 / 学生拒绝。
 * 人员接口在 controller 再用 @RequireRole("SUPER_ADMIN") 收口——注解管"能不能进"，
 * 这里的数据范围管"进了能看到哪些行"，两层防线互为兜底。
 */
@Mapper
@DataScope(scope = DataScope.Scope.ORG)
public interface PersonMapper extends BaseMapper<Person> {
}
