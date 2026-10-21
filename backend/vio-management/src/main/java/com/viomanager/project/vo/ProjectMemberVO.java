package com.viomanager.project.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 项目成员列表项（PR06）：含角色与加入时间。
 */
@Data
public class ProjectMemberVO {

    /** 人员档案 ID */
    private Long memberId;

    /** 姓名冗余 */
    private String memberName;

    /** 项目角色：OWNER 所有人 / MEMBER 成员 */
    private String projectRole;

    /** 加入时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime joinAt;
}
