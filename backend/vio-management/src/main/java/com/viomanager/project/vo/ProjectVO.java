package com.viomanager.project.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 项目列表卡片（PR01 列表 / PR02 创建返回复用）。
 * 跨线名（orgName/semesterName/contestName）由 QuerySupport 批量 IN 装配，列表零 JOIN。
 */
@Data
public class ProjectVO {

    private Long id;

    private String name;

    /** 所有人姓名（表内冗余列直取） */
    private String ownerName;

    /** 组织名（批量装配） */
    private String orgName;

    /** 学期名（批量装配） */
    private String semesterName;

    /** 比赛名（批量装配，未挂载为 null） */
    private String contestName;

    /** ACTIVE / FINISHED / ARCHIVED */
    private String status;

    /** PRIVATE / ORG / PUBLIC */
    private String visibility;

    private LocalDate startDate;

    private LocalDate endDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 乐观锁版本（PR04 编辑回传启用，知识库闭环模式） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
