package com.viomanager.schedule.vo;

import java.util.Map;
import lombok.Data;

/**
 * SC06 整周空课矩阵单格（前端 FreeSlotCell 消费形状）：空课人数 + 按年级分布。
 */
@Data
public class FreeSlotCellVO {

    /** 星期：1-7 */
    private Integer weekday;

    /** 节次索引：1-5 */
    private Integer slotIndex;

    /** 该格空课人数 */
    private Integer freeCount;

    /** 年级 → 人数分布（如 {"2022": 12, "2023": 8}） */
    private Map<String, Integer> gradeDist;
}
