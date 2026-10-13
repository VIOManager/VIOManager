package com.viomanager.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 编辑赛队参数（TE13 body）：队名 / 项目名。队名同比赛下唯一（4002）。
 */
@Data
public class TeamUpdateDTO {

    @NotBlank(message = "队名不能为空")
    @Size(max = 200, message = "队名最长 200 字")
    private String name;

    @Size(max = 200, message = "项目名最长 200 字")
    private String projectName;
}
