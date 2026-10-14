package com.viomanager.schedule.vo;

import lombok.Data;

/**
 * SC10 排班规则出参（前端 ScheduleRule 消费形状）：7×5 每时段一条，
 * 未配置时段按"未启用"语义返回（enabled=false）。
 */
@Data
public class ScheduleRuleVO {

    /** 星期：1-7 */
    private Integer weekday;

    /** 节次索引：1-5 */
    private Integer slotIndex;

    /** 是否启用（未配置 = false） */
    private Boolean enabled;

    /** 最少人数 */
    private Integer minPerson;

    /** 最多人数 */
    private Integer maxPerson;
}
