package com.viomanager.achievement.dto;

import lombok.Data;

/**
 * 成果列表筛选入参（A01，GET 查询参数绑定）：四个条件全部可空，空即不过滤。
 * type 对应实体的 achievement_type 列（接口字段名与实体列名的映射见 service）。
 */
@Data
public class AchievementQueryDTO {

    /** 按赛队筛选 */
    private Long teamId;

    /** 按成果类型筛选：AWARD / RATING / QUALIFICATION / ACTIVITY / OTHER */
    private String type;

    /** 按状态筛选：PENDING / APPROVED / REJECTED */
    private String status;

    /** 按成果名称模糊搜索 */
    private String keyword;
}
