package com.viomanager.achievement.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 成果记录实体，对应表 achievement。
 * 字段与 V1__init_52_tables.sql 第 471–504 行一一对应。
 *
 * ⚠️ team_id 可为空（表注释「个人成果可为空」）—— 但接口清单的 A01–A08 全部按队伍口径，
 *    个人成果场景本期未定义，建议实现时**要求 teamId 非空**并在群里登记该收敛。
 *
 * ⚠️ 两张表/两个字段都存佐证，语义需统一（接口清单 A06 是「单文件覆盖式」）：
 *    achievement.evidence_file_id / evidence_file_name —— 当前生效的一份（前端只读这两个）
 *    achievement_file                                  —— 多附件 + 版本留痕
 *
 * ⚠️ achievement_type 取值：AWARD 获奖 / RATING 评级 / QUALIFICATION 资质 /
 *    ACTIVITY 活动成果 / OTHER 其他（接口清单 A01/A03）。
 * ⚠️ status 取值：PENDING 待审批 / APPROVED 已通过 / REJECTED 已驳回。
 * ⚠️ submit_channel 取值：USER 队长提交（走审批） / ADMIN 管理员录入（直接生效）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("achievement")
public class Achievement extends BaseEntity {

    /** 所属赛队 ID，个人成果可为空 */
    private Long teamId;

    /** 关联比赛 ID */
    private Long contestId;

    /** 所属学期 ID */
    private Long semesterId;

    /** 所属组织（数据范围过滤依据） */
    private Long orgId;

    /** 成果归属人人员档案 ID（队长提交时为队长） */
    private Long ownerMemberId;

    /** 归属人姓名冗余 */
    private String ownerName;

    /** 成果类型：AWARD / RATING / QUALIFICATION / ACTIVITY / OTHER */
    @TableField("achievement_type")
    private String achievementType;

    /** 成果名称 */
    private String name;

    /** 级别：国家级 / 省级 / 市级 / 校级 / 院级 等（自由字符串，无枚举表） */
    private String level;

    /** 获奖/取得日期（接口清单字段名为 awardDate） */
    @TableField("award_date")
    private LocalDate awardDate;

    /** 成果描述 */
    private String description;

    /** 佐证材料文件 ID（§5 联动：成果生效后计入材料必交项） */
    private Long evidenceFileId;

    /** 佐证文件名冗余 */
    private String evidenceFileName;

    /** 状态：PENDING 待审批 / APPROVED 已通过 / REJECTED 已驳回 */
    private String status;

    /** 提交渠道：USER 队长提交（走审批） / ADMIN 管理员录入（直接生效） */
    private String submitChannel;

    /** 审批通过时间 */
    private LocalDateTime approvedAt;

    /** 审批人账号 ID */
    private Long approvedBy;

    /** 驳回原因 */
    private String rejectReason;

    /** 备注 */
    private String remark;
}
