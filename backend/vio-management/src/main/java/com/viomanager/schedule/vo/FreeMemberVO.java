package com.viomanager.schedule.vo;

import lombok.Data;

/**
 * SC07 单格空课名单行（前端 FreeMember 消费形状）。按年级分组展示归前端渲染，接口平铺返回。
 */
@Data
public class FreeMemberVO {

    /** 人员 ID */
    private Long memberId;

    /** 姓名 */
    private String name;

    /** 班级 */
    private String className;

    /** 年级（学号推导优先、班级名兜底） */
    private String grade;
}
