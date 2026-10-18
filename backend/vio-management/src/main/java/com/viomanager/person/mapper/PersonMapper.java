package com.viomanager.person.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.person.entity.Person;
import org.apache.ibatis.annotations.Mapper;

/**
 * 人员档案 mapper（单表 CRUD 走 BaseMapper，无手写 SQL、无 XML）。
 * 数据范围 SELF（规范 §5.2）：拦截器对所有角色放行不注入——
 * M02 详情要求"任意登录用户可查、按视角裁剪出参"，ORG 会把学生整体拦死（本人也查不到自己），
 * 因此可见性改由服务层负责：M01 列表部门管理员的组织子树过滤、M02 四档视角裁剪都在
 * PersonQuerySupport / PersonViewerSupport 里做（照审批模块 SELF + 服务端过滤的先例）。
 * 写接口（M03/M04/M05/M08/M09/M13）的越权封堵在 PersonViewerSupport.assertAdminCanManage。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface PersonMapper extends BaseMapper<Person> {
}
