package com.viomanager.schedule.vo;

import java.util.List;
import lombok.Data;

/**
 * SC01 周排班表单格（前端 ScheduleCell 消费形状）：7×5 格子视图，每格含排班人员。
 */
@Data
public class ScheduleCellVO {

    /** 星期：1-7 */
    private Integer weekday;

    /** 节次索引：1-5 */
    private Integer slotIndex;

    /** 该格排班人员 */
    private List<ScheduleEntryVO> entries;
}
