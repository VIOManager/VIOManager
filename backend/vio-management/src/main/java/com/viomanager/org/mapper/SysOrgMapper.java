package com.viomanager.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.org.entity.SysOrg;
import org.apache.ibatis.annotations.Mapper;

/**
 * 组织架构 mapper（单表 CRUD 走 BaseMapper，无手写 SQL、无 XML）。
 * 数据范围 SELF（规范 §5.2）：拦截器对所有角色放行不注入——
 * O01 树的"部门管理员仅本部门子树"裁剪在服务层做（照 person / 审批 SELF + 服务端过滤先例），
 * 拦截器若按 ORG 注入会把管理员的可见根节点也裁掉。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface SysOrgMapper extends BaseMapper<SysOrg> {
}
