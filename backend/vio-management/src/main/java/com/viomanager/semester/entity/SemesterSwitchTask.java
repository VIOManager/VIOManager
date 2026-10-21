package com.viomanager.semester.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 学期切换任务实体，对应表 semester_switch_task。
 * 刻意不继承 BaseEntity：本表审计列只有 created_at/updated_at（无 created_by/updated_by，
 * 谁发起谁取消已经用 initiated_by/cancelled_by 业务列表达），继承会把不存在的列写进 INSERT。
 * 乐观锁刻意用手工条件更新（service 层 UPDATE ... WHERE version=?），
 * 不挂 MP 的 @Version 插件——S08 的占位语义（PENDING_EXECUTE + version 双条件）
 * 比插件自增更严，且不依赖全局插件配置。
 * impact_snapshot 是 JSON 字符串（待归档比赛数/待继承赛队数/成员关系数），只在发起时拍快照。
 */
@Data
@Accessors(chain = true)
@TableName("semester_switch_task")
public class SemesterSwitchTask {

    /** 雪花 ID，JSON 出参按字符串序列化（JacksonConfig） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 逻辑删除标记（手写 SQL 需显式带 deleted = 0） */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 源学期 ID（发起时的当前学期） */
    private Long fromSemesterId;

    /** 源学期名冗余 */
    private String fromSemesterName;

    /** 目标学期 ID */
    private Long toSemesterId;

    /** 目标学期名冗余 */
    private String toSemesterName;

    /** PENDING_EXECUTE / CANCELLED / RUNNING / SUCCESS / FAILED */
    private String status;

    /** 待执行窗口分钟数，默认 30 */
    private Integer windowMinutes;

    /** 窗口截止时刻（创建时间 + windowMinutes），过期未执行拒绝执行 */
    private LocalDateTime executeAt;

    /** 实际开始执行时间 */
    private LocalDateTime startedAt;

    /** 执行完成时间（成败都回填） */
    private LocalDateTime finishedAt;

    /** 影响快照 JSON：{contestCount, teamCount, memberRelationCount} */
    private String impactSnapshot;

    /** 执行后回填：归档比赛数 */
    private Integer archivedContests;

    /** 执行后回填：继承赛队数 */
    private Integer inheritedTeams;

    /** 执行后回填：继承成员关系数 */
    private Integer inheritedMembers;

    /** 失败原因（FAILED 时回填） */
    private String errorMessage;

    /** 乐观锁版本号：占位 RUNNING 时 +1，并发双请求只成一个 */
    private Integer version;

    /** 发起人 sys_user.id */
    private Long initiatedBy;

    /** 发起人姓名冗余 */
    private String initiatedByName;

    /** 取消人 */
    private Long cancelledBy;

    /** 取消时间 */
    private LocalDateTime cancelledAt;
}
