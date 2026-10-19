package com.viomanager.project.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 使用本项目的赛队列表项（PR11，项目详情页展示）。
 */
@Data
public class ProjectTeamVO {

    /** 赛队 ID */
    private Long teamId;

    /** 队名 */
    private String teamName;

    /** 比赛名（冗余自赛队所属比赛） */
    private String contestName;

    /** 赛道名（未选赛道为空） */
    private String trackName;

    /** 挂载操作人姓名（队长） */
    private String attachedByName;

    /** 挂载时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime attachedAt;

    /** 挂载状态：ACTIVE 挂载中 / RELEASED 已解绑 */
    private String status;
}
