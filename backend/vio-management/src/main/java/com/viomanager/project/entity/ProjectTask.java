package com.viomanager.project.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 项目任务实体，对应表 project_task。
 * 手工任务（milestone_node_id 为空）+ 节点同步任务（回填溯源）双来源；
 * 权限二分：所有人改全部字段，任务负责人本人仅可置 DONE。
 *
 * ⚠️ 同步防重：双入口（审批回调/挂载回溯）动手前先锁里程碑行（SELECT FOR UPDATE，见
 *    MilestoneSyncSupport），锁内存在性检查——数据库冻结约束下的无索引防重方案。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("project_task")
public class ProjectTask extends BaseEntity {

    /** 项目 ID */
    private Long projectId;

    /** 来源时间节点 ID，手工任务为空 */
    private Long milestoneNodeId;

    /** 任务标题 */
    private String title;

    /** 任务说明 */
    private String description;

    /** 负责人人员 ID */
    private Long assigneeMemberId;

    /** 负责人姓名冗余 */
    private String assigneeName;

    /** 截止时间 */
    private LocalDateTime dueDate;

    /** 状态：TODO 未开始 / DOING 进行中 / DONE 已完成 / BLOCKED 受阻 */
    private String status;

    /** 优先级：HIGH / NORMAL / LOW */
    private String priority;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 完成人 */
    private Long completedBy;

    /** 排序号 */
    private Integer sortNo;
}
