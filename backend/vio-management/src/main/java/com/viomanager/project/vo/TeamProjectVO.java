package com.viomanager.project.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 本队挂载的项目列表项（PR12，赛队详情页展示，与 PR11 互为镜像）。
 * 项目状态与挂载状态分列回显，避免"status"二义。
 */
@Data
public class TeamProjectVO {

    /** 项目 ID */
    private Long projectId;

    /** 项目名 */
    private String name;

    /** 所有人姓名 */
    private String ownerName;

    /** 项目状态：ACTIVE / FINISHED / ARCHIVED */
    private String projectStatus;

    /** 可见范围：PRIVATE / ORG / PUBLIC */
    private String visibility;

    /** 比赛名（赛队所属比赛） */
    private String contestName;

    /** 赛道名（未选为空） */
    private String trackName;

    /** 挂载时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime attachedAt;

    /** 挂载状态：ACTIVE 挂载中 / RELEASED 已解绑 */
    private String mountStatus;
}
