package com.viomanager.project.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * PR04 编辑项目（仅所有人）。基本信息与状态流转一体：
 * status 传入且与当前不同时走条件更新（ACTIVE→FINISHED→ARCHIVED 顺序）；
 * updatedAt 传入时启用乐观锁（1007，知识库闭环模式）。
 */
@Data
public class ProjectUpdateDTO {

    @Size(max = 200, message = "项目名称不能超过 200 字")
    private String name;

    private String description;

    private Long contestId;

    private Long trackId;

    private LocalDate startDate;

    private LocalDate endDate;

    /** PRIVATE / ORG / PUBLIC */
    private String visibility;

    @Size(max = 500, message = "备注不能超过 500 字")
    private String remark;

    /** 目标状态：仅允许 ACTIVE→FINISHED→ARCHIVED 顺序流转；不传或相同则不动状态 */
    private String status;

    /** 乐观锁版本（不传则不启用） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
