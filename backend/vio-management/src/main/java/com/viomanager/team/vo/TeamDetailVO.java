package com.viomanager.team.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 赛队详情出参（TE02），字段口径 = 前端 TeamDetail。
 * viewerRole 由后端按当前登录人身份判定，前端按钮显隐的唯一依据：
 *  LEADER 队长 / MEMBER 队员 / ADVISOR 指导教师 / ADMIN 管理端。
 */
@Data
public class TeamDetailVO {

    private Long id;
    private String name;
    private String projectName;
    private Long contestId;
    private String contestName;
    private Long trackId;
    private String trackName;
    private Long leaderId;
    private String leaderName;
    private String status;
    private String materialStatus;
    private BigDecimal materialCompleteRate;
    private List<TeamMemberVO> members;
    private List<TeamAdvisorVO> advisors;
    private LocalDateTime createdAt;
    /** 当前登录人对本队的视角 */
    private String viewerRole;
}
