package com.viomanager.material.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 材料收集任务实体，对应表 material_task。
 * 字段与 V1__init_52_tables.sql 第 398–416 行一一对应（表结构即契约，禁改 V1）。
 *
 * ⚠️ 无 org_id 之外的数据范围列：本表 org_id 可为 NULL，不做数据范围强依据。
 * ⚠️ 无 UNIQUE KEY：同一比赛只能建一个任务这条规则**没有数据库兜底**，
 *    必须在 service 层先查后建（并考虑并发，必要时加锁或补 V2 唯一索引）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("material_task")
public class MaterialTask extends BaseEntity {

    /** 所属比赛 ID（B 线 contests 表） */
    private Long contestId;

    /** 所属学期 ID */
    private Long semesterId;

    /** 归属组织，可为空 */
    private Long orgId;

    /** 任务名称，如「初赛材料收集」（接口出参字段名为 name，前端旧清单叫 title，见适配说明） */
    private String name;

    /** 任务说明 */
    private String description;

    /** 截止时间，可为空（为空视为不限期） */
    private LocalDateTime deadline;

    /** 状态：ACTIVE 进行中 / CLOSED 已关闭 */
    private String status;

    /** 排序号 */
    private Integer sortNo;
}
