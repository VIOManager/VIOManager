package com.viomanager.team.vo;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 赛队列表项出参（TE01），字段口径 = 前端 front/src/api/team.ts TeamListItem。
 * ID 字段 Long 经 JacksonConfig 全局序列化为字符串。
 */
@Data
public class TeamListItemVO {

    private Long id;
    private String name;
    private String projectName;
    private Long contestId;
    private String contestName;
    private String trackName;
    private String leaderName;
    private Integer memberCount;
    private Integer advisorCount;
    private String status;
    private String materialStatus;
    private BigDecimal materialCompleteRate;
}
