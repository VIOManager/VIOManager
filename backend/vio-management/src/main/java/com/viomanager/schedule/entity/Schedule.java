package com.viomanager.schedule.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 排班实体，对应表 schedule（V1__init_52_tables.sql 第 874–902 行，表结构即契约）。
 * 三维隔离：学期 + 周次 + 子组织；org_id = sub_org_id 作为数据范围过滤锚点。
 *
 * ⚠️ 表唯一键 uk_schedule_conflict 含 slot_index 不含 weekday，与拍板规则
 * 「同周同人同子组织仅一条有效排班」不完全对齐——由 service 层校验 8005 兜底
 * （拍板记录①，改表需另走契约变更流程，见排班清单 §8）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("schedule")
public class Schedule extends BaseEntity {

    /** 学期 ID */
    private Long semesterId;

    /** 周次 */
    private Integer weekNo;

    /** 子组织 ID（排班隔离维度） */
    private Long subOrgId;

    /** 排班人员 ID */
    private Long memberId;

    /** 人员姓名冗余 */
    private String memberName;

    /** 人员班级冗余（展示用） */
    private String memberClassName;

    /** 星期：1-7 */
    private Integer weekday;

    /** 节次索引：1-5 */
    private Integer slotIndex;

    /** 节次名称，如「第1-2节」 */
    private String slotName;

    /** 值班岗位/内容，如「机房值班」 */
    private String dutyName;

    /** 是否与课表冲突：1 有课冲突（管理员强制保存）0 空课 */
    private Integer courseConflict;

    /** 冲突提示，如「第3-4节有课：高等数学」 */
    private String conflictNote;

    /** 状态：PLANNED 已排 / CANCELLED 已取消 */
    private String status;

    /** 归属组织（数据范围过滤依据，= sub_org_id） */
    private Long orgId;

    /** 备注 */
    private String remark;
}
