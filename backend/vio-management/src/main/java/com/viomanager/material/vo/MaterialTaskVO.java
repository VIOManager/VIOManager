package com.viomanager.material.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 材料任务出参（MT01 列表项 / MT02 创建返回），字段口径 = C 线接口清单 §2 MT01。
 * 四个聚合列出参说明：contestName 来自 contest（B 线表）；
 * requirementCount 为该任务下未删要求条数；teamTotal / teamComplete 为该任务所属比赛下
 * ACTIVE 队伍的总数与其中材料齐全率已达 100% 的队伍数（team 表，C 线回写列）。
 */
@Data
public class MaterialTaskVO {

    /** 任务 ID（JSON 序列化为字符串） */
    private Long id;

    /** 所属比赛 ID */
    private Long contestId;

    /** 比赛名称（contest.name；比赛被删后该列为 null，任务本身仍展示） */
    private String contestName;

    /** 任务名称 */
    private String name;

    /** 任务说明 */
    private String description;

    /** 截止时间，null = 不限期 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deadline;

    /** 状态：ACTIVE 进行中 / CLOSED 已关闭 */
    private String status;

    /** 该任务下未删的材料要求条数 */
    private Integer requirementCount;

    /** 该任务所属比赛的 ACTIVE 赛队总数 */
    private Integer teamTotal;

    /** 其中材料齐全率已达 100% 的队伍数 */
    private Integer teamComplete;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
