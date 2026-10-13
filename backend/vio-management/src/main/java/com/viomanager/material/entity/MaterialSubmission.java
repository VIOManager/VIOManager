package com.viomanager.material.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.viomanager.common.mybatis.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 材料提交实体，对应表 material_submission。
 * 字段与 V1__init_52_tables.sql 第 440–469 行一一对应。
 *
 * ⚠️ 覆盖上传模型（MS02）：同一 (requirement_id, team_id) 允许多行历史，
 *    靠 is_current / version_no 区分有效版本，旧版本保留痕迹不物理删除。
 *
 * 🔴 并发风险：本表**没有** (team_id, requirement_id, is_current) 唯一约束，
 *    并发覆盖可能产生两条 is_current=1。必须用条件更新或乐观锁自行封住：
 *    UPDATE material_submission SET is_current = 0
 *     WHERE requirement_id = ? AND team_id = ? AND is_current = 1 AND deleted = 0
 *  （规范：全库无外键、C 线 5 表均无 UNIQUE KEY，参照完整性靠应用层。）
 *
 * ⚠️ org_id 是数据范围过滤依据列（B 线 team 表同名字段为 C 线要回写的两列之一）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("material_submission")
public class MaterialSubmission extends BaseEntity {

    /** 材料要求 ID */
    private Long requirementId;

    /** 材料任务 ID（冗余） */
    private Long taskId;

    /** 比赛 ID（冗余） */
    private Long contestId;

    /** 赛队 ID */
    private Long teamId;

    /** 学期 ID（冗余，便于按学期归档） */
    private Long semesterId;

    /** 赛队所属组织（数据范围过滤依据） */
    private Long orgId;

    /** 文件对象 ID（来自组长文件服务 /api/files 的 fileId） */
    private Long fileId;

    /** 文件名冗余，列表展示免联表 */
    private String fileName;

    /** 文件大小冗余（字节） */
    private Long fileSize;

    /** 是否当前生效版本：1 生效 0 已被覆盖。齐全率只统计 1（出参字段名 isCurrent，与接口清单同口径） */
    @TableField("is_current")
    private Integer isCurrent;

    /** 版本号，每次覆盖 +1，首次为 1 */
    @TableField("version_no")
    private Integer versionNo;

    /** 提交人人员档案 ID（必须是该队队长或队员） */
    private Long submittedBy;

    /** 提交人姓名冗余 */
    private String submittedByName;

    /** 提交时间 */
    private LocalDateTime submittedAt;

    /** 来源压缩包名（若通过 zip 批量上传，MS03） */
    private String sourceZipName;

    /** 备注 */
    private String remark;
}
