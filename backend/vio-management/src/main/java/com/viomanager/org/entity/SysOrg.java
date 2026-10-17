package com.viomanager.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 组织架构实体，对应表 sys_org（树形，含分子组织）。
 * 只做数据库映射，业务在 OrgServiceImpl；前后端传输一律走 vo 包，禁止实体直出。
 * path 冗余祖先路径（形如 /0/1/2/），是全站数据范围子树查询的依据——
 * 改上级时必须级联重算子树 path 与 level，维护逻辑见 OrgServiceImpl。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("sys_org")
public class SysOrg extends BaseEntity {

    /** 上级组织 ID，顶层的父为 0 */
    private Long parentId;

    /** 组织名称 */
    private String name;

    /** 组织编码，同一父节点下唯一，用于 Excel 导入匹配 */
    private String code;

    /** 组织类型：COLLEGE 学院 / DEPARTMENT 部门 / CLASS 班级 / CLUB 社团部门 / SUB_ORG 分子组织 / OTHER 其他（清单 §6 默认写死枚举不字典化） */
    private String orgType;

    /** 是否分子组织：1 是 0 否。排班与绩效按此维度隔离（泓海特色） */
    private Integer isSubOrg;

    /** 该组织的部门管理员人员 ID（联 member.name 展示 managerName） */
    private Long managerId;

    /** 负责人姓名（仅展示用，可无账号） */
    private String leaderName;

    /** 同级排序号 */
    private Integer sortNo;

    /** 祖先路径 /0/1/12/，顶层为 /0/{id}/ */
    private String path;

    /** 层级，顶层为 1 */
    private Integer level;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
