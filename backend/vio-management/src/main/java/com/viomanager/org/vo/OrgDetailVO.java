package com.viomanager.org.vo;

import lombok.Data;

/**
 * 组织详情（O05 出参）：树节点字段 + code/path/remark/managerId 管理视角信息。
 * 无敏感字段，但可见性同树接口（超管全量、部门管理员限子树）。
 */
@Data
public class OrgDetailVO {

    private String id;

    private String parentId;

    private String name;

    private String code;

    private String orgType;

    private Boolean isSubOrg;

    private Long managerId;

    /** 部门管理员姓名（联 member） */
    private String managerName;

    private String leaderName;

    private Integer sortNo;

    private Integer status;

    private Integer level;

    /** 祖先路径 /0/1/2/，管理视角排查用 */
    private String path;

    private String remark;
}
