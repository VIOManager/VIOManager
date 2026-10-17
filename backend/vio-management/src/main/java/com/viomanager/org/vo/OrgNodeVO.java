package com.viomanager.org.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 组织树节点（O01 出参，与前端 OrgNode 一一对应）。
 * id 走字符串口径（规范 §2：长整型一律字符串防 JS 精度丢失）。
 * managerName 由服务层联 member 批量装配，不在 SQL 里逐节点 join。
 */
@Data
public class OrgNodeVO {

    private String id;

    private String parentId;

    private String name;

    private String orgType;

    private Boolean isSubOrg;

    /** 部门管理员姓名（manager_id 联 member） */
    private String managerName;

    private String leaderName;

    private Integer sortNo;

    private Integer status;

    private Integer level;

    private List<OrgNodeVO> children = new ArrayList<>();
}
