package com.viomanager.material.vo;

import java.util.List;
import lombok.Data;

/**
 * 管理端队伍提交概览出参（MS01）：一支队伍一行的完整视图——
 * 队伍基本信息 + 齐全率与待补文案 + 逐要求的提交状态行。
 */
@Data
public class TeamMaterialRowVO {

    /** 赛队 ID（JSON 序列化为字符串） */
    private Long teamId;

    /** 队名 */
    private String teamName;

    /** 队长姓名（team_member 冗余名；队长未落到队成员表时为 null） */
    private String leaderName;

    /** 当前齐全率（0–100 整数，读 team.material_complete_rate） */
    private Integer completeRate;

    /** 状态文案："齐全" 或 "待补 N 项"（只数必传项缺口） */
    private String statusText;

    /** 逐要求的提交状态（sort_no / id 升序） */
    private List<MaterialSubmitInfoVO> items;
}
