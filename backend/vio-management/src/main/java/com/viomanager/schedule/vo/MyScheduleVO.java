package com.viomanager.schedule.vo;

import lombok.Data;

/**
 * SC05 我的排班行（学生视角，前端 MySchedule 消费形状；管理端隐藏此菜单）。
 */
@Data
public class MyScheduleVO {

    /** 排班 ID */
    private Long id;

    /** 周次 */
    private Integer weekNo;

    /** 子组织名 */
    private String subOrgName;

    /** 星期：1-7 */
    private Integer weekday;

    /** 节次索引：1-5 */
    private Integer slotIndex;

    /** 值班内容 */
    private String dutyName;

    /** 备注 */
    private String remark;
}
