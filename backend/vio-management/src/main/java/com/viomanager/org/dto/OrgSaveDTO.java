package com.viomanager.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 组织新建/编辑入参（O02/O03 共用一份字段口径，区别只在路径是否带 id）。
 * status 不收：契约 body 没有它，新建固定启用（清单 §2）。
 */
@Data
public class OrgSaveDTO {

    /** 上级组织 ID，顶层传 0 */
    @NotNull(message = "上级组织不能为空")
    private Long parentId;

    /** 组织名称 */
    @NotBlank(message = "组织名称不能为空")
    private String name;

    /** 组织编码，可空；同一父节点下唯一（Excel 导入匹配用） */
    private String code;

    /** 组织类型：COLLEGE / DEPARTMENT / CLASS / CLUB / SUB_ORG / OTHER */
    @NotBlank(message = "组织类型不能为空")
    private String orgType;

    /** 是否分子组织，可空默认 0 */
    private Integer isSubOrg;

    /** 部门管理员人员 ID，可空 */
    private Long managerId;

    /** 负责人姓名，可空 */
    private String leaderName;

    /** 同级排序号，可空默认 0 */
    private Integer sortNo;

    /** 备注，可空 */
    private String remark;
}
