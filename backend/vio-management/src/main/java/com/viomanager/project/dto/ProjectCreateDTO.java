package com.viomanager.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

/**
 * PR02 创建项目。所有人=当前登录人（JWT 解析 member_id），不接受指定他人。
 * 默认值在 service 落：semesterId=当前学期、orgId=本人组织、visibility=ORG。
 */
@Data
public class ProjectCreateDTO {

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 200, message = "项目名称不能超过 200 字")
    private String name;

    private String description;

    /** 所属比赛（可选挂载） */
    private Long contestId;

    /** 所属赛道（可选） */
    private Long trackId;

    /** 所属学期（默认当前激活学期） */
    private Long semesterId;

    /** 所属子组织（默认本人组织） */
    private Long orgId;

    /** 可见范围 PRIVATE / ORG / PUBLIC（默认 ORG） */
    private String visibility;

    private LocalDate startDate;

    private LocalDate endDate;

    @Size(max = 500, message = "备注不能超过 500 字")
    private String remark;
}
