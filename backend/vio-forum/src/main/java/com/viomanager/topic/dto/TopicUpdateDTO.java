package com.viomanager.topic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * F11 管理员编辑主题入参（换版块不在常规编辑范围，board 不收）。
 */
@Data
public class TopicUpdateDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不超过 200 字")
    private String title;

    @NotBlank(message = "正文不能为空")
    private String bodyText;

    private List<String> tags;
}
